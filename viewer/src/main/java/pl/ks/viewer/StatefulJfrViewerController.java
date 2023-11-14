/*
 * Copyright 2022 Krzysztof Slusarski
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package pl.ks.viewer;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import lombok.*;
import org.apache.commons.compress.utils.IOUtils;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.multipart.MultipartFile;
import pl.ks.jfr.parser.JfrParsedFile;
import pl.ks.viewer.io.TempFileUtils;

import java.io.FileOutputStream;
import java.util.*;

import static pl.ks.viewer.JfrControllerCommon.createConfig;
import static pl.ks.viewer.TimeTable.Type.SELF_TIME;
import static pl.ks.viewer.TimeTable.Type.TOTAL_TIME;

@Controller
@RequiredArgsConstructor
class StatefulJfrViewerController {
    public static final String ON = "on";
    private final StatefulJfrViewerService jfrViewerService;
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    static class Result {
        List<StatefulJfrFile> statefulJfrFiles;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    static class JFRData {
        JfrParsedFile jfrParsedFile;
        UUID uuid;
    }

    Gson gson = new GsonBuilder().create();

    @GetMapping("/upload-stateful-jfr")
    ResponseEntity<?> uploadJfr() {
//        model.addAttribute("files", jfrViewerService.getFiles());
//        System.out.println(jfrViewerService.getFiles().toString());
        Result res = Result.builder()
                .statefulJfrFiles(jfrViewerService.getFiles()).build();

        String json = gson.toJson(res);
        return ResponseEntity.ok(json);
    }

    @PostMapping("/upload-stateful-jfr")
    ResponseEntity<?> upload(@RequestParam("files") MultipartFile files) throws Exception {
        List<String> savedCopies = new ArrayList<>();
        for (MultipartFile file : Arrays.asList(files)) {
            String originalFilename = file.getOriginalFilename();
            String filePath = TempFileUtils.TEMP_DIR + originalFilename;
            System.out.println(filePath);
            IOUtils.copy(file.getInputStream(), new FileOutputStream(filePath));
            savedCopies.add(filePath);
        }
        jfrViewerService.parseNewFiles(savedCopies);
        return uploadJfr();
    }

    @GetMapping("/stateful-jfr/single")
    ResponseEntity<?> showJfr(@RequestParam("id") UUID uuid) {
        JfrParsedFile file = jfrViewerService.getFile(uuid);
//        model.addAttribute("file", file);
//        model.addAttribute("currentId", uuid);
        JFRData res = JFRData.builder().jfrParsedFile(file).uuid(uuid).build();
        String json = gson.toJson(res);
        return ResponseEntity.ok(json);
    }

    @GetMapping("/stateful-jfr/single/remove")
    ResponseEntity<?> removeJfr(@RequestParam("id") UUID uuid) {
        jfrViewerService.remove(uuid);
        return uploadJfr();
    }

    @GetMapping("/stateful-jfr/single/trim")
    ResponseEntity<?> trimToMethod(Model model, @RequestParam("id") UUID uuid,
                        @RequestParam("methodName") String methodName,
                        @RequestParam("direction") JfrParsedFile.Direction direction) {
        jfrViewerService.trimToMethod(uuid, methodName, direction);
        return uploadJfr();
    }

    @ResponseBody
    @GetMapping("/stateful-jfr/single/flames/execution")
    byte[] getExecutionSamplesFlameGraph(@RequestParam("id") UUID uuid, @RequestParam Map<String, String> params) {
//        for(Map.Entry a : params.entrySet())
//        System.out.println(a.getKey()+" = "+a.getValue());
        return jfrViewerService.getExecutionSamplesFlameGraph(uuid, createConfig(params));
    }


    @ResponseBody
    @GetMapping("/stateful-jfr/single/flames/allocation/count")
    byte[] getAllocationSamplesCountFlameGraph(@RequestParam("id") UUID uuid, @RequestParam Map<String, String> params) {
        return jfrViewerService.getAllocationCountSamplesFlameGraph(uuid, createConfig(params));
    }

    @ResponseBody
    @GetMapping("/stateful-jfr/single/flames/allocation/size")
    byte[] getAllocationSamplesSizeFlameGraph(@RequestParam("id") UUID uuid, @RequestParam Map<String, String> params) {
        return jfrViewerService.getAllocationSizeSamplesFlameGraph(uuid, createConfig(params));
    }

    @ResponseBody
    @GetMapping("/stateful-jfr/single/flames/lock/count")
    byte[] getLockCountSamplesFlameGraph(@RequestParam("id") UUID uuid, @RequestParam Map<String, String> params) {
        return jfrViewerService.getLockCountSamplesFlameGraph(uuid, createConfig(params));
    }

    @ResponseBody
    @GetMapping("/stateful-jfr/single/flames/lock/time")
    byte[] getLockTimeSamplesFlameGraph(@RequestParam("id") UUID uuid, @RequestParam Map<String, String> params) {
        return jfrViewerService.getLockTimeSamplesFlameGraph(uuid, createConfig(params));
    }

    @GetMapping("/stateful-jfr/single/correlation-id-stats")
    String getCorrelationIdStats(Model model, @RequestParam("id") UUID uuid, @RequestParam Map<String, String> params) {
        model.addAttribute("correlationIdStats", jfrViewerService.getCorrelationIdStats(uuid, createConfig(params)));
        return "uploaded-stateful-jfr-correlation-id-stats";
    }

    @GetMapping("/stateful-jfr/single/cpu-stats")
    String getCpuStats(Model model, @RequestParam("id") UUID uuid, @RequestParam Map<String, String> params) {
        model.addAttribute("cpuStats", jfrViewerService.getCpuStats(uuid, createConfig(params)));
        return "uploaded-stateful-jfr-cpu-stats";
    }

    @GetMapping("/stateful-jfr/single/table/total/execution")
    ResponseEntity<?> getExecutionTotalTimeTable(Model model, @RequestParam("id") UUID uuid, @RequestParam Map<String, String> params) {
//        model.addAttribute("table", jfrViewerService.getExecutionSamplesTimeStats(uuid, createConfig(params), TOTAL_TIME));
        String json = gson.toJson(jfrViewerService.getExecutionSamplesTimeStats(uuid, createConfig(params), TOTAL_TIME));
        return ResponseEntity.ok(json);
//        return "uploaded-stateful-total-time-table";
    }

    @GetMapping("/stateful-jfr/single/table/totalself/execution")
    ResponseEntity<?> getExecutionTotalSelfTimeTable(Model model, @RequestParam("id") UUID uuid, @RequestParam Map<String, String> params) {

//        model.addAttribute("table", jfrViewerService.combineTotalSelfTimeTable(uuid,params));
        String json = gson.toJson(jfrViewerService.combineTotalSelfTimeTable(uuid,params));
        return ResponseEntity.ok(json);
//        return "uploaded-stateful-self-total-time-table";
    }

    @GetMapping("/stateful-jfr/single/table/total/allocation/count")
    ResponseEntity<?> getAllocationCountTotalTimeStats(Model model, @RequestParam("id") UUID uuid, @RequestParam Map<String, String> params) {
//        model.addAttribute("table", jfrViewerService.getAllocationCountSamplesTimeStats(uuid, createConfig(params), TOTAL_TIME));
        String json = gson.toJson(jfrViewerService.getAllocationCountSamplesTimeStats(uuid, createConfig(params), TOTAL_TIME));
        return ResponseEntity.ok(json);
//        return "uploaded-stateful-total-time-table";
    }

    @GetMapping("/stateful-jfr/single/table/total/allocation/size")
    ResponseEntity<?> getAllocationSizeTotalTimeStats(Model model, @RequestParam("id") UUID uuid, @RequestParam Map<String, String> params) {
//        model.addAttribute("table", jfrViewerService.getAllocationSizeSamplesTimeStats(uuid, createConfig(params), TOTAL_TIME));
        String json = gson.toJson(jfrViewerService.getAllocationSizeSamplesTimeStats(uuid, createConfig(params), TOTAL_TIME));
        return ResponseEntity.ok(json);
//        return "uploaded-stateful-total-time-table";
    }

    @GetMapping("/stateful-jfr/single/table/total/lock/count")
    ResponseEntity<?> getLockCountTotalTimeStats(Model model, @RequestParam("id") UUID uuid, @RequestParam Map<String, String> params) {
//        model.addAttribute("table", jfrViewerService.getLockCountSamplesTimeStats(uuid, createConfig(params), TOTAL_TIME));
        String json = gson.toJson(jfrViewerService.getLockCountSamplesTimeStats(uuid, createConfig(params), TOTAL_TIME));
        return ResponseEntity.ok(json);
//        return "uploaded-stateful-total-time-table";
    }

    @GetMapping("/stateful-jfr/single/table/total/lock/time")
    ResponseEntity<?> getLockTimeTotalTimeStats(Model model, @RequestParam("id") UUID uuid, @RequestParam Map<String, String> params) {
//        model.addAttribute("table", jfrViewerService.getLockTimeSamplesTimeStats(uuid, createConfig(params), TOTAL_TIME));
        String json = gson.toJson(jfrViewerService.getLockTimeSamplesTimeStats(uuid, createConfig(params), TOTAL_TIME));
        return ResponseEntity.ok(json);
//        return "uploaded-stateful-total-time-table";
    }

    @GetMapping("/stateful-jfr/single/table/self/execution")
    ResponseEntity<?> getExecutionSelfTimeTable(Model model, @RequestParam("id") UUID uuid, @RequestParam Map<String, String> params) {
//        model.addAttribute("table", jfrViewerService.getExecutionSamplesTimeStats(uuid, createConfig(params), SELF_TIME));
        String json = gson.toJson(jfrViewerService.getExecutionSamplesTimeStats(uuid, createConfig(params), SELF_TIME));
        return ResponseEntity.ok(json);
//        return "uploaded-stateful-self-time-table";
    }

    @GetMapping("/stateful-jfr/single/table/self/allocation/count")
    ResponseEntity<?> getAllocationCountSelfTimeStats(Model model, @RequestParam("id") UUID uuid, @RequestParam Map<String, String> params) {
//        model.addAttribute("table", jfrViewerService.getAllocationCountSamplesTimeStats(uuid, createConfig(params), SELF_TIME));
        String json = gson.toJson(jfrViewerService.getAllocationCountSamplesTimeStats(uuid, createConfig(params), SELF_TIME));
        return ResponseEntity.ok(json);
//        return "uploaded-stateful-self-time-table";
    }

    @GetMapping("/stateful-jfr/single/table/self/allocation/size")
    ResponseEntity<?> getAllocationSizeSelfTimeStats(Model model, @RequestParam("id") UUID uuid, @RequestParam Map<String, String> params) {
//        model.addAttribute("table", jfrViewerService.getAllocationSizeSamplesTimeStats(uuid, createConfig(params), SELF_TIME));
        String json = gson.toJson(jfrViewerService.getAllocationSizeSamplesTimeStats(uuid, createConfig(params), SELF_TIME));
        return ResponseEntity.ok(json);
//        return "uploaded-stateful-self-time-table";
    }

    @GetMapping("/stateful-jfr/single/table/self/lock/count")
    ResponseEntity<?> getLockCountSelfTimeStats(Model model, @RequestParam("id") UUID uuid, @RequestParam Map<String, String> params) {
//        model.addAttribute("table", jfrViewerService.getLockCountSamplesTimeStats(uuid, createConfig(params), SELF_TIME));
        String json = gson.toJson(jfrViewerService.getLockCountSamplesTimeStats(uuid, createConfig(params), SELF_TIME));
        return ResponseEntity.ok(json);
//        return "uploaded-stateful-self-time-table";
    }

    @GetMapping("/stateful-jfr/single/table/self/lock/time")
    ResponseEntity<?> getLockTimeSelfTimeStats(Model model, @RequestParam("id") UUID uuid, @RequestParam Map<String, String> params) {
//        model.addAttribute("table", jfrViewerService.getLockTimeSamplesTimeStats(uuid, createConfig(params), SELF_TIME));
        String json = gson.toJson(jfrViewerService.getLockTimeSamplesTimeStats(uuid, createConfig(params), SELF_TIME));
        return ResponseEntity.ok(json);
//        return "uploaded-stateful-self-time-table";
    }
}
