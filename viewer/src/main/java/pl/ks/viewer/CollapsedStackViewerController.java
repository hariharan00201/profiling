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

import java.io.*;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import org.apache.commons.compress.utils.IOUtils;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.multipart.MultipartFile;
import pl.ks.viewer.flamegraph.*;
import pl.ks.viewer.io.*;
//import pl.ks.viewer.io.TempFileUtils;
import pl.ks.viewer.pages.*;

@Controller
@RequiredArgsConstructor
class CollapsedStackViewerController {
    private final CollapsedStackPageCreator collapsedStackPageCreator;
    private final FlameGraphExecutor flameGraphExecutor;

    @GetMapping("/upload-collapsed")
    String upload() {
        return "upload-collapsed";
    }

    @PostMapping("/upload-collapsed")
    ResponseEntity<?> upload(
                  @RequestParam("file") MultipartFile file,
                  @RequestParam("filter") String filter,
                  @RequestParam("title") String title,
                  @RequestParam("totalTimeThreshold") BigDecimal totalTimeThreshold,
                  @RequestParam("selfTimeThreshold") BigDecimal selfTimeThreshold) throws Exception {

        System.out.println("Entered post of upload "+totalTimeThreshold);
        String originalFilename = file.getOriginalFilename();
        InputStream inputStream = StorageUtils.createCopy(TempFileUtils.TEMP_DIR, originalFilename, file.getInputStream());
        String homeDir = System.getProperty("user.home")+"/diffFGrav/web/collapsed";
        String prefix = "collapsed-stack-" + UUID.randomUUID().toString();
        saveFileTo(file,homeDir+"/"+prefix+".collapsed");
        String uncompressedFileName = prefix + ".log";
        if (StringUtils.isEmpty(filter)) {
            IOUtils.copy(inputStream, new FileOutputStream(TempFileUtils.TEMP_DIR + uncompressedFileName));
        } else {
            copyFileWithFilter(filter, inputStream, uncompressedFileName);
        }

//        model.addAttribute("welcomePage", WelcomePage.builder()
//                .pages(collapsedStackPageCreator.generatePages(uncompressedFileName, title, totalTimeThreshold, selfTimeThreshold))
//                .title(title)
//                .build());
        Gson gson = new GsonBuilder().create();
        String json = gson.toJson(collapsedStackPageCreator.generatePages(uncompressedFileName, title, totalTimeThreshold, selfTimeThreshold));
        return ResponseEntity.ok(json);
    }

    private void saveFileTo(MultipartFile file, String filePath) throws IOException {

        InputStream inputStream = file.getInputStream();

        // Create a directory if it doesn't exist (optional)
        File directory = new File(filePath).getParentFile();
        if (!directory.exists()) {
            directory.mkdirs();
        }

        // Create an output stream to write the file
        OutputStream outputStream = new FileOutputStream(filePath);

        // Read from the input stream and write to the output stream in chunks
        byte[] buffer = new byte[4096]; // Adjust buffer size as needed
        int bytesRead;
        while ((bytesRead = inputStream.read(buffer)) != -1) {
            outputStream.write(buffer, 0, bytesRead);
        }

        // Close streams
        inputStream.close();
        outputStream.close();
    }

    @PostMapping("/upload-multi-collapsed")
    ResponseEntity<?> uploadMultiCollapsed(
            @RequestParam("start") Integer start,
            @RequestParam("end") Integer end,
            @RequestParam("filter") String filter,
            @RequestParam("title") String title,
            @RequestParam("totalTimeThreshold") BigDecimal totalTimeThreshold,
            @RequestParam("selfTimeThreshold") BigDecimal selfTimeThreshold)throws Exception {
        System.out.println("Entered post of multiple upload ");
        String originalFilename = start+" - "+end+" timestamp collapsed file";
        InputStream inputStream = StorageUtils.createCopy(TempFileUtils.TEMP_DIR, originalFilename, combineCollapsedFiles(start,end));
        String uncompressedFileName = "multi-collapsed-stack-" + UUID.randomUUID().toString() + ".log";
        if (StringUtils.isEmpty(filter)) {
            IOUtils.copy(inputStream, new FileOutputStream(TempFileUtils.TEMP_DIR + uncompressedFileName));
        } else {
            copyFileWithFilter(filter, inputStream, uncompressedFileName);
        }

//        model.addAttribute("welcomePage", WelcomePage.builder()
//                .pages(collapsedStackPageCreator.generatePages(uncompressedFileName, title, totalTimeThreshold, selfTimeThreshold))
//                .title(title)
//                .build());


        Gson gson = new GsonBuilder().create();
        String json = gson.toJson(collapsedStackPageCreator.generatePages(uncompressedFileName, title, totalTimeThreshold, selfTimeThreshold));
        return ResponseEntity.ok(json);
    }

    private InputStream combineCollapsedFiles(Integer start, Integer end) throws FileNotFoundException {
        String directoryPath = "viewer\\src\\main\\resources\\public\\Collapsed";
        File directory = new File(directoryPath);
        List<InputStream> inputStreams = new ArrayList<>();
        if (directory.isDirectory()) {
            File[] files = directory.listFiles();
            if (files != null) {
                for (File file : files) {
                    if (file.isFile()) {
                        String firstHalf = file.getName().split("\\.")[0];
//                        System.out.println(file.getName());
                        if(start <= Integer.parseInt(firstHalf) && end >= Integer.parseInt(firstHalf)){
                            inputStreams.add(new FileInputStream(directoryPath+"/"+file.getName()));
                        }
                    }
                }
            }
        } else {
            System.out.println("Not a valid directory path: " + directoryPath);
        }
        return new SequenceInputStream(Collections.enumeration(inputStreams));
    }

    @PostMapping("/upload-temp")
    String uploadTemp(Model model,
                      @RequestParam("tempFile") String tempFile,
                      @RequestParam("filter") String filter,
                      @RequestParam("title") String title,
                      @RequestParam("totalTimeThreshold") BigDecimal totalTimeThreshold,
                      @RequestParam("selfTimeThreshold") BigDecimal selfTimeThreshold) throws Exception {
        String targetFile = null;
        if (StringUtils.isEmpty(filter)) {
            targetFile = tempFile;
        } else {
            targetFile = "collapsed-stack-" + UUID.randomUUID().toString() + ".log";
            copyFileWithFilter(filter, new FileInputStream(TempFileUtils.TEMP_DIR + tempFile), targetFile);
        }
        model.addAttribute("welcomePage", WelcomePage.builder()
                .pages(collapsedStackPageCreator.generatePages(targetFile, title, totalTimeThreshold, selfTimeThreshold))
                .title(title)
                .build());
        return "collapsed";
    }

    @GetMapping(value = "/image/{name}", produces = "text/html")
    @SneakyThrows
    @ResponseBody
    byte[] getImage(@PathVariable("name") String name) {
        return IOUtils.toByteArray(new FileInputStream(TempFileUtils.getFilePath(name)));
    }

    @GetMapping(value = "/flame-graph", produces = "text/html")
    @ResponseBody
    byte[] getFlameGraph(@RequestParam("collapsed") String collapsed,
                         @RequestParam("title") String title) throws Exception {
        String collapsedFilePath = TempFileUtils.getFilePath(collapsed);
        String outputHtmlFilePath = TempFileUtils.getFilePath(collapsed + ".html");
        Gson gson = new GsonBuilder().create();
        String json = gson.toJson(flameGraphExecutor.generateFlameGraphHtml5(collapsedFilePath, outputHtmlFilePath, "Flame graph - " + title, false));


        byte[] response = null;
        try (InputStream inputStream = new FileInputStream(outputHtmlFilePath);) {
            response = IOUtils.toByteArray(inputStream);
        }
        System.out.println(response.length);
        Files.delete(Paths.get(outputHtmlFilePath));
//        return ResponseEntity.ok(json);
        return response;
    }

    @GetMapping(value = "/flame-graph-no-thread", produces = "text/html")
    @ResponseBody
    byte[] getFlameGraphNoThread(@RequestParam("collapsed") String collapsed,
                                 @RequestParam("title") String title,
                                 @RequestParam("skipped") int skipped) throws Exception {
        String collapsedFilepath = TempFileUtils.getFilePath(collapsed);
        String outputHtmlFilePath = TempFileUtils.getFilePath(collapsed + ".no-thread.html");
        String outputCollapsedFilePath = TempFileUtils.getFilePath(collapsed + "-no-thread.txt");

        try (InputStream inputStream = new FileInputStream(collapsedFilepath);
             OutputStream outputStream = new FileOutputStream(outputCollapsedFilePath);
             BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));
             BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(outputStream));) {
            while (reader.ready()) {
                String line = reader.readLine();
                for (int i = 0; i < skipped; i++) {
                    line = line.substring(line.indexOf(";") + 1);
                }
                writer.write(line);
                writer.newLine();
            }
        }

        flameGraphExecutor.generateFlameGraphHtml5(outputCollapsedFilePath, outputHtmlFilePath, "Flame graph - no thread division - " + title, false);
        byte[] response = null;
        try (InputStream inputStream = new FileInputStream(outputHtmlFilePath);) {
            response = IOUtils.toByteArray(inputStream);
        }
        Files.delete(Paths.get(outputHtmlFilePath));
        Files.delete(Paths.get(outputCollapsedFilePath));
        return response;
    }

    @GetMapping(value = "/flame-graph-hotspot-limited", produces = "text/html")
    @ResponseBody
    byte[] getFlameGraphHotspotLimited(@RequestParam("limit") int limit,
                                       @RequestParam("collapsed") String collapsed,
                                       @RequestParam("title") String title) throws Exception {
        UUID newUuid = UUID.randomUUID();
        String outputCollapsedFilePath = TempFileUtils.getFilePath(newUuid + "-method.txt");
        String outputHtmlFilePath = TempFileUtils.getFilePath(newUuid + "-method.html");
        try (InputStream inputStream = new FileInputStream(TempFileUtils.getFilePath(collapsed));
             OutputStream fromOutStream = new FileOutputStream(outputCollapsedFilePath);
             BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));
             BufferedWriter toWriter = new BufferedWriter(new OutputStreamWriter(fromOutStream));
        ) {
            while (reader.ready()) {
                String line = reader.readLine();
                int delimiterChar = line.lastIndexOf(" ");
                String stack = line.substring(0, delimiterChar);
                String num = line.substring(delimiterChar + 1);

                String[] splittedStack = stack.split(";");
                for (int i = Math.max(0, splittedStack.length - limit); i < splittedStack.length; i++) {
                    String frame = splittedStack[i];
                    toWriter.write(frame);
                    if (i != splittedStack.length - 1) {
                        toWriter.write(";");
                    }
                }

                toWriter.write(" ");
                toWriter.write(num);
                toWriter.newLine();
            }
        }

        flameGraphExecutor.generateFlameGraphHtml5(outputCollapsedFilePath, outputHtmlFilePath, "Flame graph - hotspot - " + title, true);
        byte[] response = null;
        try (InputStream inputStream = new FileInputStream(outputHtmlFilePath);) {
            response = IOUtils.toByteArray(inputStream);
        }
        Files.delete(Paths.get(outputHtmlFilePath));
        Files.delete(Paths.get(outputCollapsedFilePath));
        return response;
    }

    @GetMapping(value = "/flame-graph-hotspot", produces = "text/html")
    @ResponseBody
    byte[] getFlameGraphHotspot(@RequestParam("collapsed") String collapsed,
                                @RequestParam("title") String title) throws Exception {
        String collapsedFileName = TempFileUtils.getFilePath(collapsed);
        String outputHtmlFilePath = TempFileUtils.getFilePath(collapsed + ".hotspot.html");
        flameGraphExecutor.generateFlameGraphHtml5(collapsedFileName, outputHtmlFilePath, "Flame graph - hotspot - " + title, true);
        byte[] response = null;
        try (InputStream inputStream = new FileInputStream(outputHtmlFilePath);) {
            response = IOUtils.toByteArray(inputStream);
        }
        Files.delete(Paths.get(outputHtmlFilePath));
        return response;
    }


    @GetMapping(value = "/from-method", produces = "text/html")
    @ResponseBody
    byte[] fromMethod(@RequestParam("collapsed") String collapsed, @RequestParam("method") String method,
                      @RequestParam("title") String title) throws Exception {
        UUID newUuid = UUID.randomUUID();
        String outputCollapsedFilePath = TempFileUtils.getFilePath(newUuid + "-method.txt");
        String outputHtmlFilePath = TempFileUtils.getFilePath(newUuid + "-method.html");
        try (InputStream inputStream = new FileInputStream(TempFileUtils.getFilePath(collapsed));
             OutputStream fromOutStream = new FileOutputStream(outputCollapsedFilePath);
             BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));
             BufferedWriter fromWriter = new BufferedWriter(new OutputStreamWriter(fromOutStream));
        ) {
            while (reader.ready()) {
                String line = reader.readLine();
                int delimiterChar = line.lastIndexOf(" ");
                String stack = line.substring(0, delimiterChar);
                int pos = stack.indexOf(method);
                if (pos < 0) {
                    continue;
                }
                String fromStack = stack.substring(pos);
                String num = line.substring(delimiterChar + 1);
                fromWriter.write(fromStack);
                fromWriter.write(" ");
                fromWriter.write(num);
                fromWriter.newLine();
            }
        }
        flameGraphExecutor.generateFlameGraphHtml5(outputCollapsedFilePath, outputHtmlFilePath, "Callee - " + title + " - " + method, false);
        byte[] response = null;
        try (InputStream inputStream = new FileInputStream(outputHtmlFilePath);) {
            response = IOUtils.toByteArray(inputStream);
        }
        Files.delete(Paths.get(outputHtmlFilePath));
        Files.delete(Paths.get(outputCollapsedFilePath));
        return response;
    }

    @GetMapping(value = "/from-method-root", produces = "text/html")
    String fromMethodRoot(Model model,
                          @RequestParam("collapsed") String collapsed,
                          @RequestParam("method") String method,
                          @RequestParam("title") String title,
                          @RequestParam("totalTimeThreshold") BigDecimal totalTimeThreshold,
                          @RequestParam("selfTimeThreshold") BigDecimal selfTimeThreshold) throws Exception {
        UUID newUuid = UUID.randomUUID();
        String filename = newUuid + "from--method-root.txt";
        String outputCollapsedFilePath = TempFileUtils.getFilePath(filename);
        try (InputStream inputStream = new FileInputStream(TempFileUtils.getFilePath(collapsed));
             OutputStream fromOutStream = new FileOutputStream(outputCollapsedFilePath);
             BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));
             BufferedWriter fromWriter = new BufferedWriter(new OutputStreamWriter(fromOutStream));
        ) {
            while (reader.ready()) {
                String line = reader.readLine();
                int delimiterChar = line.lastIndexOf(" ");
                String stack = line.substring(0, delimiterChar);
                int pos = stack.indexOf(method);
                if (pos < 0) {
                    continue;
                }
                String fromStack = stack.substring(pos);
                String num = line.substring(delimiterChar + 1);
                fromWriter.write(fromStack);
                fromWriter.write(" ");
                fromWriter.write(num);
                fromWriter.newLine();
            }
        }
        model.addAttribute("welcomePage", WelcomePage.builder()
                .pages(collapsedStackPageCreator.generatePages(filename, title + " - " + method, totalTimeThreshold, selfTimeThreshold))
                .title(title + " " + method)
                .build());
        return "collapsed";
    }


    @GetMapping(value = "/to-method", produces = "text/html")
    @ResponseBody
    byte[] toMethod(@RequestParam("collapsed") String collapsed,
                    @RequestParam("method") String method,
                    @RequestParam("title") String title) throws Exception {
        UUID newUuid = UUID.randomUUID();
        String outputCollapsedFilePath = TempFileUtils.getFilePath(newUuid + "-method.txt");
        String outputHtmlFilePath = TempFileUtils.getFilePath(newUuid + "-method.html");
        try (InputStream inputStream = new FileInputStream(TempFileUtils.getFilePath(collapsed));
             OutputStream fromOutStream = new FileOutputStream(outputCollapsedFilePath);
             BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));
             BufferedWriter toWriter = new BufferedWriter(new OutputStreamWriter(fromOutStream));
        ) {
            while (reader.ready()) {
                String line = reader.readLine();
                int delimiterChar = line.lastIndexOf(" ");
                String stack = line.substring(0, delimiterChar);
                int pos = stack.indexOf(method);
                if (pos < 0) {
                    continue;
                }
                String toStack = stack.substring(0, pos + method.length());
                String num = line.substring(delimiterChar + 1);

                toWriter.write(toStack);
                toWriter.write(" ");
                toWriter.write(num);
                toWriter.newLine();
            }
        }
        flameGraphExecutor.generateFlameGraphHtml5(outputCollapsedFilePath, outputHtmlFilePath, "Callers - " + title + " - " + method, true);
        byte[] response = null;
        try (InputStream inputStream = new FileInputStream(outputHtmlFilePath);) {
            response = IOUtils.toByteArray(inputStream);
        }
        Files.delete(Paths.get(outputCollapsedFilePath));
        Files.delete(Paths.get(outputHtmlFilePath));
        return response;
    }

    @GetMapping(value = "/to-method-root", produces = "text/html")
    String toMethodRoot(Model model,
                        @RequestParam("collapsed") String collapsed,
                        @RequestParam("method") String method,
                        @RequestParam("title") String title,
                        @RequestParam("totalTimeThreshold") BigDecimal totalTimeThreshold,
                        @RequestParam("selfTimeThreshold") BigDecimal selfTimeThreshold) throws Exception {
        UUID newUuid = UUID.randomUUID();
        String filename = newUuid + "-to-method-root.txt";
        String outputCollapsedFilePath = TempFileUtils.getFilePath(filename);
        try (InputStream inputStream = new FileInputStream(TempFileUtils.getFilePath(collapsed));
             OutputStream fromOutStream = new FileOutputStream(outputCollapsedFilePath);
             BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));
             BufferedWriter toWriter = new BufferedWriter(new OutputStreamWriter(fromOutStream));
        ) {
            while (reader.ready()) {
                String line = reader.readLine();
                int delimiterChar = line.lastIndexOf(" ");
                String stack = line.substring(0, delimiterChar);
                int pos = stack.indexOf(method);
                if (pos < 0) {
                    continue;
                }
                String toStack = stack.substring(0, pos + method.length());
                String num = line.substring(delimiterChar + 1);

                toWriter.write(toStack);
                toWriter.write(" ");
                toWriter.write(num);
                toWriter.newLine();
            }
        }
        model.addAttribute("welcomePage", WelcomePage.builder()
                .pages(collapsedStackPageCreator.generatePages(filename, title + " - " + method, totalTimeThreshold, selfTimeThreshold))
                .title(title + " " + method)
                .build());
        return "collapsed";
    }

    private void copyFileWithFilter(String filter, InputStream inputStream, String outputFileName) throws IOException {
        filter = filter.trim();
        try (OutputStream outputStream = new FileOutputStream(TempFileUtils.TEMP_DIR + outputFileName);
             BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));
             BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(outputStream));) {
            while (reader.ready()) {
                String line = reader.readLine();
                if (line.contains(filter)) {
                    writer.write(line);
                    writer.newLine();
                }
            }
        } finally {
            inputStream.close();
        }
    }

}
