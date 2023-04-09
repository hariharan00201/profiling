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
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import pl.ks.collapsed.*;
import pl.ks.collapsed.CollapsedStackBufferedReader;

@Slf4j
@RequiredArgsConstructor
public class FlameGraphExecutor {
    public void generateFlameGraphHtml5(String inputFile, String outputFile, String title, boolean reversed) {
        try {
            String[] args;
            if (reversed) {
                args = new String[]{"--title", title, "--reverse", inputFile, outputFile};
            } else {
                args = new String[]{"--title", title, inputFile, outputFile};
            }
            FlameGraph flameGraph = new FlameGraph(args);
            flameGraph.parse();
            flameGraph.dump();
        } catch (Exception e) {
            log.error("Fatal error", e);
            throw new RuntimeException(e);
        }
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
