package pl.ks.viewer;

import lombok.*;
import lombok.extern.slf4j.Slf4j;
import pl.ks.collapsed.CollapsedStack;
import pl.ks.jfr.parser.JfrEcidInfo;
import pl.ks.jfr.parser.JfrParsedAllocationEvent;
import pl.ks.jfr.parser.JfrParsedCommonStackTraceEvent;
import pl.ks.jfr.parser.JfrParsedCpuUsageEvent;
import pl.ks.jfr.parser.JfrParsedEventWithTime;
import pl.ks.jfr.parser.JfrParsedExecutionSampleEvent;
import pl.ks.jfr.parser.JfrParsedFile;
import pl.ks.jfr.parser.JfrParsedLockEvent;
import pl.ks.jfr.parser.JfrParser;
import pl.ks.viewer.flamegraph.FlameGraphExecutor;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.time.Instant;
import java.time.temporal.ChronoField;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Stream;

import static pl.ks.viewer.JfrControllerCommon.createConfig;
import static pl.ks.viewer.TimeTable.Type.SELF_TIME;
import static pl.ks.viewer.TimeTable.Type.TOTAL_TIME;

@Slf4j
@RequiredArgsConstructor
class StatefulJfrViewerService {
    private final Map<UUID, JfrParsedFile> parsedFiles = new ConcurrentHashMap<>();

    private final JfrParser jfrParser;
    private final FlameGraphExecutor flameGraphExecutor;

    List<StatefulJfrFile> getFiles() {
        return parsedFiles.entrySet().stream()
                .sorted(Comparator.comparing(o -> o.getValue().getParseStartDate()))
                .map(entry -> StatefulJfrFile.builder()
                        .id(entry.getKey())
                        .parseStartDate(entry.getValue().getParseStartDate())
                        .filenames(entry.getValue().getFilenames())
                        .build()
                )
                .toList();
    }

    void remove(UUID uuid) {
        parsedFiles.remove(uuid);
    }

    JfrParsedFile getFile(UUID uuid) {
        return parsedFiles.get(uuid);
    }

    StatefulJfrHighChartCpuStats getCpuStats(UUID uuid, JfrViewerFilterAndLevelConfig config) {
        JfrParsedFile jfrParsedFile = parsedFiles.get(uuid);
        List<Predicate<JfrParsedCpuUsageEvent>> filters = createFilters(config, jfrParsedFile, JfrParsedCpuUsageEvent.class);

        Stream<JfrParsedCpuUsageEvent> samples = jfrParsedFile.getCpuUsageSamples().stream();
        for (Predicate<JfrParsedCpuUsageEvent> filter : filters) {
            samples = samples.filter(filter);
        }
        List<JfrParsedCpuUsageEvent> cpuUsageEvents = samples.sorted(Comparator.comparing(JfrParsedCpuUsageEvent::getEventTime)).toList();
        Map<String, List<JfrParsedCpuUsageEvent>> cpuUsageEventsPerFile = new HashMap<>();
        for (JfrParsedCpuUsageEvent event : cpuUsageEvents) {
            cpuUsageEventsPerFile.computeIfAbsent(event.getFilename(), name -> new ArrayList<>()).add(event);
        }

        return StatefulJfrHighChartCpuStats.builder()
                .filenames(jfrParsedFile.getFilenames())
                .cpuUsageEventsPerFile(cpuUsageEventsPerFile)
                .build();
    }

    List<JfrEcidInfo> getCorrelationIdStats(UUID uuid, JfrViewerFilterAndLevelConfig config) {
        Map<Long, JfrEcidInfo> correlationIdInfo = new ConcurrentHashMap<>();
        getFilteredExecutionSamples(config, getFile(uuid)).stream().parallel().forEach(sample -> {
            correlationIdInfo.computeIfAbsent(sample.getCorrelationId(), JfrEcidInfo::new)
                    .newExecutionSample(sample.getEventTime(), sample.isConsumesCpu());
        });
        return correlationIdInfo.values().stream()
                .sorted(Comparator.comparing(JfrEcidInfo::timeDiff).reversed())
                .limit(1000)
                .toList();
    }

    byte[] getExecutionSamplesFlameGraph(UUID uuid, JfrViewerFilterAndLevelConfig config) {
        JfrParsedFile jfrParsedFile = parsedFiles.get(uuid);
        CollapsedStack collapsed = jfrParsedFile.asCollapsed(getFilteredExecutionSamples(config, jfrParsedFile),
                config.getAdditionalLevels(), JfrParsedExecutionSampleEvent::getFullStackTrace);
//        String CollapsedDir = "C:\\Users\\haris\\Downloads" + "/";

//        OutputStream os = null;
//        try {
//            os = new FileOutputStream(new File(CollapsedDir+"Collapsed1.collapsed"));
//            for(Map.Entry a : collapsed.stacks().entrySet()){
//                os.write((a.getKey()+" "+a.getValue()).getBytes());
//                os.write("\n".getBytes());
////                writer.close();

//            }
//        } catch (FileNotFoundException e) {
//            throw new RuntimeException(e);
//        } catch (IOException e) {
//            throw new RuntimeException(e);
//        }

//        System.out.println(CollapsedDir);
//        for(Map.Entry a : collapsed.stacks().entrySet()){
//            (a.getKey()+" "+a.getValue());
//        }
        return flameGraphExecutor.generateFlameGraphHtml5(collapsed, "Execution samples", config.isReverseOn());
    }

    TimeTable getExecutionSamplesTimeStats(UUID uuid, JfrViewerFilterAndLevelConfig config, TimeTable.Type type) {
        JfrParsedFile jfrParsedFile = parsedFiles.get(uuid);
        return TimeTableCreator.create(generateTimeStats(getFilteredExecutionSamples(config, jfrParsedFile)), type, config.getTableLimit(), uuid);
    }

    private List<JfrParsedExecutionSampleEvent> getFilteredExecutionSamples(JfrViewerFilterAndLevelConfig config, JfrParsedFile jfrParsedFile) {
        List<Predicate<JfrParsedExecutionSampleEvent>> filters = createFilters(config, jfrParsedFile, JfrParsedExecutionSampleEvent.class);

        Stream<JfrParsedExecutionSampleEvent> samples = jfrParsedFile.getExecutionSamples().stream();
        for (Predicate<JfrParsedExecutionSampleEvent> filter : filters) {
            samples = samples.filter(filter);
        }
        return samples.toList();
    }

    byte[] getAllocationCountSamplesFlameGraph(UUID uuid, JfrViewerFilterAndLevelConfig config) {
        JfrParsedFile jfrParsedFile = parsedFiles.get(uuid);
        CollapsedStack collapsed = jfrParsedFile.asCollapsed(getFilteredAllocationSamples(config, jfrParsedFile), config.getAdditionalLevels(), JfrParsedAllocationEvent::getFullStackTrace);
        return flameGraphExecutor.generateFlameGraphHtml5(collapsed, "Allocation samples (count)", config.isReverseOn());
    }

    byte[] getAllocationSizeSamplesFlameGraph(UUID uuid, JfrViewerFilterAndLevelConfig config) {
        JfrParsedFile jfrParsedFile = parsedFiles.get(uuid);
        CollapsedStack collapsed = jfrParsedFile.asCollapsed(getFilteredAllocationSamples(config, jfrParsedFile), config.getAdditionalLevels(), JfrParsedAllocationEvent::getFullStackTrace, JfrParsedAllocationEvent::getSize);
        return flameGraphExecutor.generateFlameGraphHtml5(collapsed, "Allocation samples (size)", config.isReverseOn());
    }

    TimeTable getAllocationCountSamplesTimeStats(UUID uuid, JfrViewerFilterAndLevelConfig config, TimeTable.Type type) {
        JfrParsedFile jfrParsedFile = parsedFiles.get(uuid);
        SelfAndTotalTimeStats stats = generateTimeStats(getFilteredAllocationSamples(config, jfrParsedFile), JfrParsedAllocationEvent::getObjectClass);
        return TimeTableCreator.create(stats, type, config.getTableLimit(), uuid);
    }

    TimeTable getAllocationSizeSamplesTimeStats(UUID uuid, JfrViewerFilterAndLevelConfig config, TimeTable.Type type) {
        JfrParsedFile jfrParsedFile = parsedFiles.get(uuid);
        SelfAndTotalTimeStats stats = generateTimeStats(getFilteredAllocationSamples(config, jfrParsedFile), JfrParsedAllocationEvent::getSize, JfrParsedAllocationEvent::getObjectClass);
        return TimeTableCreator.create(stats, type, config.getTableLimit(), uuid);
    }

    private List<JfrParsedAllocationEvent> getFilteredAllocationSamples(JfrViewerFilterAndLevelConfig config, JfrParsedFile jfrParsedFile) {
        List<Predicate<JfrParsedAllocationEvent>> filters = createFilters(config, jfrParsedFile, JfrParsedAllocationEvent.class);

        Stream<JfrParsedAllocationEvent> samples = jfrParsedFile.getAllocationSamples().stream();
        for (Predicate<JfrParsedAllocationEvent> filter : filters) {
            samples = samples.filter(filter);
        }
        return samples.toList();
    }

    byte[] getLockCountSamplesFlameGraph(UUID uuid, JfrViewerFilterAndLevelConfig config) {
        JfrParsedFile jfrParsedFile = parsedFiles.get(uuid);
        CollapsedStack collapsed = jfrParsedFile.asCollapsed(getFilteredLockSamples(config, jfrParsedFile), config.getAdditionalLevels(), JfrParsedLockEvent::getFullStackTrace);
        return flameGraphExecutor.generateFlameGraphHtml5(collapsed, "Lock samples", config.isReverseOn());
    }

    byte[] getLockTimeSamplesFlameGraph(UUID uuid, JfrViewerFilterAndLevelConfig config) {
        JfrParsedFile jfrParsedFile = parsedFiles.get(uuid);
        CollapsedStack collapsed = jfrParsedFile.asCollapsed(getFilteredLockSamples(config, jfrParsedFile), config.getAdditionalLevels(), JfrParsedLockEvent::getFullStackTrace, JfrParsedLockEvent::getDuration);
        return flameGraphExecutor.generateFlameGraphHtml5(collapsed, "Lock samples", config.isReverseOn());
    }

    TimeTable getLockCountSamplesTimeStats(UUID uuid, JfrViewerFilterAndLevelConfig config, TimeTable.Type type) {
        JfrParsedFile jfrParsedFile = parsedFiles.get(uuid);
        SelfAndTotalTimeStats stats = generateTimeStats(getFilteredLockSamples(config, jfrParsedFile), JfrParsedLockEvent::getMonitorClass);
        return TimeTableCreator.create(stats, type, config.getTableLimit(), uuid);
    }

    TimeTable getLockTimeSamplesTimeStats(UUID uuid, JfrViewerFilterAndLevelConfig config, TimeTable.Type type) {
        JfrParsedFile jfrParsedFile = parsedFiles.get(uuid);
        SelfAndTotalTimeStats stats = generateTimeStats(getFilteredLockSamples(config, jfrParsedFile), JfrParsedLockEvent::getDuration, JfrParsedLockEvent::getMonitorClass);
        return TimeTableCreator.create(stats, type, config.getTableLimit(), uuid);
    }

    private List<JfrParsedLockEvent> getFilteredLockSamples(JfrViewerFilterAndLevelConfig config, JfrParsedFile jfrParsedFile) {
        List<Predicate<JfrParsedLockEvent>> filters = createFilters(config, jfrParsedFile, JfrParsedLockEvent.class);

        Stream<JfrParsedLockEvent> samples = jfrParsedFile.getLockSamples().stream();
        for (Predicate<JfrParsedLockEvent> filter : filters) {
            samples = samples.filter(filter);
        }

        return samples.toList();
    }

    UUID parseNewFiles(List<String> files) {
        UUID uuid = UUID.randomUUID();
        List<Path> paths = files.stream()
                .map(Paths::get)
                .toList();

        JfrParsedFile parsedFile = jfrParser.parse(paths);
        addNewFile(uuid, parsedFile);
        return uuid;
    }

    void addNewFile(UUID uuid, JfrParsedFile parsedFile) {
        parsedFiles.put(uuid, parsedFile);
    }

    private <T extends JfrParsedCommonStackTraceEvent> SelfAndTotalTimeStats generateTimeStats(List<T> events) {
        return generateTimeStats(events, ignored -> 1L, null);
    }

    private <T extends JfrParsedCommonStackTraceEvent> SelfAndTotalTimeStats generateTimeStats(
            List<T> events,
            Function<T, String> additionalConsumingResourceFunction
    ) {
        return generateTimeStats(events, ignored -> 1L, additionalConsumingResourceFunction);
    }

    private <T extends JfrParsedCommonStackTraceEvent> SelfAndTotalTimeStats generateTimeStats(
            List<T> events,
            Function<T, Long> countFunction,
            Function<T, String> additionalConsumingResourceFunction
    ) {
        SelfAndTotalTimeStats selfAndTotalTimeStats = new SelfAndTotalTimeStats();
        events.stream().parallel()
                .forEach(event -> {
                    String[] stackTrace = event.getStackTrace();
                    Set<String> visited = new HashSet<>();
                    Long count = countFunction.apply(event);
                    selfAndTotalTimeStats.newStackTrace(count);
                    for (int i = stackTrace.length - 1; i >= 0; i--) {
                        if (visited.contains(stackTrace[i])) {
                            continue;
                        }
                        visited.add(stackTrace[i]);
                        boolean consumingResource = additionalConsumingResourceFunction == null && i == stackTrace.length - 1;
                        selfAndTotalTimeStats.methodSample(stackTrace[i], consumingResource, count);
                    }
                    if (additionalConsumingResourceFunction != null) {
                        selfAndTotalTimeStats.methodSample(additionalConsumingResourceFunction.apply(event), true, count);
                    }
                });
        return selfAndTotalTimeStats;
    }

    @SneakyThrows
    private <T> List<Predicate<T>> createFilters(JfrViewerFilterAndLevelConfig config, JfrParsedFile jfrParsedFile, Class<T> clazz) {
        List<Predicate<T>> filters = new ArrayList<>(2);

        if (JfrParsedExecutionSampleEvent.class.isAssignableFrom(clazz)) {
            if (config.isConsumeCpuOn()) {
                filters.add(t -> ((JfrParsedExecutionSampleEvent) t).isConsumesCpu());
            }
        }

        if (JfrParsedCommonStackTraceEvent.class.isAssignableFrom(clazz)) {
            if (config.isThreadFilterOn()) {
                filters.add(t -> ((JfrParsedCommonStackTraceEvent) t).getThreadName().equalsIgnoreCase(config.getThreadFilter()));
            }
            if (config.isEcidFilterOn()) {
                filters.add(t -> ((JfrParsedCommonStackTraceEvent) t).getCorrelationId() == config.getEcidFilter());
            }
            if (config.isStackTraceFilterOn()) {
                filters.add(t -> ((JfrParsedCommonStackTraceEvent) t).stackTraceContains(config.getStackTraceFilter()));
            }
        }

        if (JfrParsedEventWithTime.class.isAssignableFrom(clazz)) {
            if (config.isEndDurationOn()) {
                Locale locale = new Locale(config.getLocaleLanguage());
                SimpleDateFormat simpleDateFormat = new SimpleDateFormat(config.getEndDateDateTimeFormat(), locale);
                Date parsedDate = simpleDateFormat.parse(config.getEndDate());
                Instant endDateTmp = parsedDate.toInstant();
                Instant startDateTmp = endDateTmp.minus(config.getDuration(), ChronoUnit.MILLIS);
                Instant endDate = endDateTmp.with(ChronoField.MILLI_OF_SECOND, 999);
                Instant startDate = startDateTmp.with(ChronoField.MILLI_OF_SECOND, 0);
                filters.add(t -> {
                    JfrParsedEventWithTime event = (JfrParsedEventWithTime) t;
                    return !(event.getEventTime().isBefore(startDate) || event.getEventTime().isAfter(endDate));
                });
            } else if (config.isWarmupCooldownOn()) {
                Instant startDate = jfrParsedFile.getMinEventDate().plus(config.getWarmup(), ChronoUnit.SECONDS);
                Instant endDate = jfrParsedFile.getMaxEventDate().minus(config.getCooldown(), ChronoUnit.SECONDS);
                filters.add(t -> {
                    JfrParsedEventWithTime event = (JfrParsedEventWithTime) t;
                    return !(event.getEventTime().isBefore(startDate) || event.getEventTime().isAfter(endDate));
                });
            } else if (config.isWarmupDurationOn()) {
                Instant startDate = jfrParsedFile.getMinEventDate().plus(config.getWdWarmup(), ChronoUnit.SECONDS);
                Instant endDate = startDate.plus(config.getWdDuration(), ChronoUnit.SECONDS);
                filters.add(t -> {
                    JfrParsedEventWithTime event = (JfrParsedEventWithTime) t;
                    return !(event.getEventTime().isBefore(startDate) || event.getEventTime().isAfter(endDate));
                });
            } else if (config.isStartEndTimestampOn()) {
                filters.add(t -> {
                    JfrParsedEventWithTime event = (JfrParsedEventWithTime) t;
                    long startTs = config.getStartTs() * 1000;
                    long endTs = (config.getEndTs() * 1000) + 999;
                    return !(event.getEventTime().toEpochMilli() < startTs || event.getEventTime().toEpochMilli() > endTs);
                });
            }
        }

        return filters;
    }

    UUID trimToMethod(UUID parentUuid, String methodName, JfrParsedFile.Direction direction) {
        UUID childUuid = UUID.randomUUID();
        JfrParsedFile childFile = jfrParser.trim(getFile(parentUuid), methodName, direction);
        addNewFile(childUuid, childFile);
        return childUuid;
    }

    public CombinedTimeTable combineExecutionTotalSelfTimeTable(UUID uuid, Map<String, String> params) {
        TimeTable total = getExecutionSamplesTimeStats(uuid, createConfig(params), TOTAL_TIME);
        TimeTable self = getExecutionSamplesTimeStats(uuid, createConfig(params), SELF_TIME);
//        CombinedTimeTable combinedTimeTable = CombinedTimeTable.builder().build()
        return combineTotalSelfHelper(total,self,uuid);
    }

    private CombinedTimeTable combineTotalSelfHelper(TimeTable total, TimeTable self, UUID uuid) {
        HashMap<String, CombinedTimeTable.CombinedRow> temp = new HashMap<>();
        for(TimeTable.Row a : total.getRows()){
            temp.put(a.getMethodName(), CombinedTimeTable.CombinedRow.builder().methodName(a.getMethodName()).totalPercent(a.getPercent()).totalSamples(a.getSamples()).build());
        }
        List<CombinedTimeTable.CombinedRow> combinedRows = new ArrayList<>();
        for (TimeTable.Row a : self.getRows()){
            CombinedTimeTable.CombinedRow combinedRow = temp.get(a.getMethodName());
            combinedRow.setSelfPercent(a.getPercent());
            combinedRow.setSelfSamples(a.getSamples());
            combinedRows.add(combinedRow);
            temp.put(a.getMethodName(),combinedRow);
        }

        return CombinedTimeTable.builder().rows(combinedRows).fileId(uuid).build();
    }

    public CombinedTimeTable combineAllocationCountTotalSelfTimeStats(UUID uuid, Map<String, String> params) {
        TimeTable total = getAllocationCountSamplesTimeStats(uuid, createConfig(params), TOTAL_TIME);
        TimeTable self = getAllocationCountSamplesTimeStats(uuid, createConfig(params), SELF_TIME);
//        CombinedTimeTable combinedTimeTable = CombinedTimeTable.builder().build()
        return combineTotalSelfHelper(total,self,uuid);
    }

    public CombinedTimeTable combineAllocationSizeTotalSelfTimeStats(UUID uuid, Map<String, String> params) {
        TimeTable total = getAllocationSizeSamplesTimeStats(uuid, createConfig(params), TOTAL_TIME);
        TimeTable self = getAllocationSizeSamplesTimeStats(uuid, createConfig(params), SELF_TIME);
//        CombinedTimeTable combinedTimeTable = CombinedTimeTable.builder().build()
        return combineTotalSelfHelper(total,self,uuid);
    }

    public CombinedTimeTable combineLockCountSamplesTotalSelfTimeStats(UUID uuid, Map<String, String> params) {
        TimeTable total = getLockCountSamplesTimeStats(uuid, createConfig(params), TOTAL_TIME);
        TimeTable self = getLockCountSamplesTimeStats(uuid, createConfig(params), SELF_TIME);
//        CombinedTimeTable combinedTimeTable = CombinedTimeTable.builder().build()
        return combineTotalSelfHelper(total,self,uuid);
    }

    public Object combineLockTimeSamplesTotalSelfTimeStats(UUID uuid, Map<String, String> params) {
        TimeTable total = getLockTimeSamplesTimeStats(uuid, createConfig(params), TOTAL_TIME);
        TimeTable self = getLockTimeSamplesTimeStats(uuid, createConfig(params), SELF_TIME);
//        CombinedTimeTable combinedTimeTable = CombinedTimeTable.builder().build()
        return combineTotalSelfHelper(total,self,uuid);
    }


    @Value
    @Builder
    static public class CombinedTimeTable {
        UUID fileId;
        List<CombinedRow> rows;

//        @Value
        @Builder
        @Data
        public static class CombinedRow {
            String methodName;
            String totalPercent;
            long totalSamples;
            String selfPercent;
            long selfSamples;
        }

        public enum Type {
            TOTAL_TIME,
            SELF_TIME,
        }
    }

}
