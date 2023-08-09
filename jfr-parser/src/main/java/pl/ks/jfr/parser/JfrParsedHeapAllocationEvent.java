package pl.ks.jfr.parser;

import lombok.Builder;
import lombok.Value;
import pl.ks.jfr.parser.tuning.AdditionalLevel;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@Value
@Builder
public class JfrParsedHeapAllocationEvent implements JfrParsedCommonStackTraceEvent {
    String[] stackTrace;
    int[] lineNumbers;
    long correlationId;
    String threadName;
    String filename;
    Instant eventTime;
    String objectClass;
    long size;
//    boolean outsideTLAB;

    public List<String[]> getFullStackTrace(Set<AdditionalLevel> additionalLevels) {
        List<String[]> fullStackTrace = new ArrayList<>();
        addCommonStackTraceElements(fullStackTrace, additionalLevels);
        fullStackTrace.add(new String[]{objectClass});// + (outsideTLAB ? "_[i]" : "_[k]")});
        return fullStackTrace;
    }
}
