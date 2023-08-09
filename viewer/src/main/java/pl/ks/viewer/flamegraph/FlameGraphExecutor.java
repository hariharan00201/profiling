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
package pl.ks.viewer.flamegraph;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.util.HashMap;
import java.util.List;

import lombok.*;
import lombok.extern.slf4j.Slf4j;
import pl.ks.collapsed.*;
import pl.ks.collapsed.CollapsedStackBufferedReader;

@Slf4j
@RequiredArgsConstructor
public class FlameGraphExecutor {
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    static class FGData {
        List<FlameGraph.FDataHolder> fDataHolderList;
        HashMap<String,Long> result;
    }
    public FGData generateFlameGraphHtml5(String inputFile, String outputFile, String title, boolean reversed) {
        try {
            String[] args;
            if (reversed) {
                args = new String[]{"--title", title, "--reverse", inputFile, outputFile};
            } else {
                args = new String[]{"--title", title, inputFile, outputFile};
            }
            FlameGraph flameGraph = new FlameGraph(args);
            flameGraph.parse();
            List<FlameGraph.FDataHolder> fDataHolderList = flameGraph.flameFDataGeneratorHelper();
            HashMap<String,Long> result = flameGraph.getResultHelper();


            flameGraph.dump();
            return FGData.builder().fDataHolderList(fDataHolderList).result(result).build();
        } catch (Exception e) {
            log.error("Fatal error", e);
            throw new RuntimeException(e);
        }
//         return null;
    }

    public byte[] generateFlameGraphHtml5(CollapsedStack inputFile, String title, boolean reversed) {
        try {
            String[] args;
            if (reversed) {
                args = new String[]{"--title", title, "--reverse"};
            } else {
                args = new String[]{"--title", title};
            }
            FlameGraph flameGraph = new FlameGraph(args);
            flameGraph.parse(new CollapsedStackBufferedReader(inputFile));
            ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
            flameGraph.dump(new PrintStream(byteArrayOutputStream));
            return byteArrayOutputStream.toByteArray();
        } catch (Exception e) {
            log.error("Fatal error", e);
            throw new RuntimeException(e);
        }
    }
}
