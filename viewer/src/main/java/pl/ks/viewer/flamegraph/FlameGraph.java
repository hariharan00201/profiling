/*
 * Copyright 2020 Andrei Pangin
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

import com.google.gson.Gson;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.regex.Pattern;
public class FlameGraph {
    public static final byte FRAME_INTERPRETED = 0;
    public static final byte FRAME_JIT_COMPILED = 1;
    public static final byte FRAME_INLINED = 2;
    public static final byte FRAME_NATIVE = 3;
    public static final byte FRAME_CPP = 4;
    public static final byte FRAME_KERNEL = 5;
    public static final byte FRAME_C1_COMPILED = 6;

    private final Arguments args;
    private final Frame root = new Frame(FRAME_NATIVE);
    private int depth;
    private long mintotal;
    public Set<String> UniqueTitle = new HashSet<>();
    public HashSet<String> TitleByLevels = new HashSet<>();
    public HashMap<String,Long> Result = new HashMap<>();
    String json = "";
    public FlameGraph(Arguments args) {
        this.args = args;
    }

    public FlameGraph(String... args) {
        this(new Arguments(args));
    }

    public void parse() throws IOException {
        parse(new InputStreamReader(new FileInputStream(args.input), StandardCharsets.UTF_8));
    }
    public HashMap<String ,Long> getResultHelper(){
        return Result;
    }
    public void parse(Reader in) throws IOException {
        try (BufferedReader br = new BufferedReader(in)) {
            parse(br);
        }
    }

    public void parse(BufferedReader br) throws IOException {
        for (String line; (line = br.readLine()) != null; ) {
            int space = line.lastIndexOf(' ');
            if (space <= 0) continue;

            String[] trace = line.substring(0, space).split(";");
            long ticks = Long.parseLong(line.substring(space + 1));
            addSample(trace, ticks);
        }
        separateByLevels(UniqueTitle);
        for(String a : TitleByLevels){
            TreeMap<Long,Long> marked = new TreeMap<>();
            calculatePercentage(a,"all",root,0,0,marked);
            Long matched = totalMarked(marked);
//            float pct = (matched >= 4664L) ? 100 : (float) 100.0 * matched / 4664L;
//            System.out.println(a+" : "+pct);
            Result.put(a,matched);
        }
        Gson gson = new Gson();
        json = gson.toJson(Result);

    }

    public void separateByLevels(Set<String> uniqueTitle) {

//        for(int  i = 0;i<10;i++){
//            TitleByLevels.add(new HashSet<>());
//        }
        for(String a : UniqueTitle){
            String[] splitA = a.split("/");
            String tempString = "";
            for(int i = 0;i<splitA.length;i++){
//                HashSet<String> temp =  TitleByLevels.get(i);
                if(i == 0)
                    tempString+=splitA[i];
                else
                    tempString+="/"+splitA[i];
                TitleByLevels.add(tempString);
//                TitleByLevels.set(i,temp);
            }
        }

    }

    public void addSample(String[] trace, long ticks) {
        if (excludeTrace(trace)) {
            return;
        }

        Frame frame = root;
        if (args.reverse) {
            for (int i = trace.length; --i >= args.skip; ) {

                frame = frame.addChild(trace[i], ticks);
                if(frame.getType() == 1){
                    if (trace[i].indexOf('/') > 0 && trace[i].charAt(0) != '['
                            || trace[i].indexOf('.') > 0 && Character.isUpperCase(trace[i].charAt(0))) {UniqueTitle.add(trace[i]);}
                    else
                    UniqueTitle.add(trace[i].substring(0, trace[i].length() - 4));
                }
            }
        } else {
            for (int i = args.skip; i < trace.length; i++) {
//                UniqueTitle.add(trace[i].substring(0, trace[i].length() - 4));
                frame = frame.addChild(trace[i], ticks);
                if(frame.getType() == 1){
//
                    if (trace[i].indexOf('/') > 0 && trace[i].charAt(0) != '['
                            || trace[i].indexOf('.') > 0 && Character.isUpperCase(trace[i].charAt(0))) {
                        UniqueTitle.add(trace[i]);}
                    else
                        UniqueTitle.add(trace[i].substring(0, trace[i].length() - 4));
                }
            }
        }
        frame.addLeaf(ticks);

        depth = Math.max(depth, trace.length);
    }

    public void dump() throws IOException {
        System.out.println("args output  "+args.output);
        if (args.output == null) {
            dump(System.out);
        } else {
            try (BufferedOutputStream bos = new BufferedOutputStream(new FileOutputStream(args.output), 32768);
                 PrintStream out = new PrintStream(bos, false, "UTF-8")) {
                dump(out);
            }
        }
    }

    public void dump(PrintStream out) {
        mintotal = (long) (root.total * args.minwidth / 100);
        int depth = mintotal > 1 ? root.depth(mintotal) : this.depth + 1;

        String tail = getResource("/flame.html");

        tail = printTill(out, tail, "/*height:*/300");
        out.print(Math.min(depth * 16, 32767));

        tail = printTill(out, tail, "/*title:*/");
        out.print(args.title);

        tail = printTill(out, tail, "/*reverse:*/false");
        out.print(args.reverse);

        tail = printTill(out, tail, "/*depth:*/0");
        out.print(depth);

        tail = printTill(out, tail, "/*data:*/");
        out.print(json);

        tail = printTill(out, tail, "/*frames:*/");

        printFrame(out, "all", root, 0, 0);

        tail = printTill(out, tail, "/*highlight:*/");
        out.print(args.highlight != null ? "'" + escape(args.highlight) + "'" : "");
//        System.out.println("unique Titles == "+UniqueTitle.size());
        out.print(tail);
    }

    private String printTill(PrintStream out, String data, String till) {
        int index = data.indexOf(till);
        out.print(data.substring(0, index));
        return data.substring(index + till.length());
    }

//    public void getAllUnique(String title, Frame frame, int level, long x, Set<String> UniqueTitles){
//        UniqueTitles.add(title);
//        for (Map.Entry<String, Frame> e : frame.entrySet()) {
//            Frame child = e.getValue();
//            if (child.total >= mintotal) {
//                getAllUnique(e.getKey(), child, level + 1, x,marked);
//            }
//            x += child.total;
//        }
//    }

    public void mark(long left,long width,Map<Long,Long> marked) {
//        System.out.println("called");
        if(!marked.containsKey(left))
            marked.put(left,width);
        else if(marked.get(left) < width){
            marked.put(left,width);
        }
    }

    public Long totalMarked(TreeMap<Long,Long> marked) {
        Long total = 0L;
        Long left = 0L;

        for (Long key : marked.keySet()) {
            Long value = marked.get(key);
            if(key >= left) {
                total += value;
                left = (key + value);
            }
        }

//        Object.keys(marked).sort(function(a, b) { return a - b; }).forEach(function(x) {
//            if (+x >= left) {
//                total += marked[x];
//                left = +x + marked[x];
//            }
//        });
        return total;
    }
    public void calculatePercentage(String query, String title, Frame frame, int level, long x,TreeMap<Long,Long> marked){

        if(title.contains(query))
            mark(x,frame.total,marked);

        x += frame.self;
        for (Map.Entry<String, Frame> e : frame.entrySet()) {
            Frame child = e.getValue();
//            if (child.total >= mintotal) {

            calculatePercentage(query, e.getKey(), child, level + 1, x,marked);
//            }
            x += child.total;
        }
    }
    List<FDataHolder> fDataHolders = new ArrayList<>();
    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    @Builder
    static class FDataHolder {
        int level;
        long left;
        long width;
        int type;
        String title;
        long inln;
        long c1;
        long Int;
    }

    public List<FDataHolder> flameFDataGeneratorHelper(){
        return flameFDataGenerator("all",root,0,0);
    }

    private List<FDataHolder> flameFDataGenerator(String title, Frame frame, int level, long x) {
        int type = frame.getType();
        if (type == FRAME_KERNEL) {
            title = stripSuffix(title);
        }

        if ((frame.inlined | frame.c1 | frame.interpreted) != 0 && frame.inlined < frame.total && frame.interpreted < frame.total) {
            fDataHolders.add(FDataHolder.builder()
                    .level(level)
                    .left(x)
                    .width(frame.total)
                    .type(type)
                    .title(escape(title))
                    .inln(frame.inlined)
                    .c1(frame.c1)
                    .Int(frame.interpreted)
                    .build());
//            out.println("f(" + level + "," + x + "," + frame.total + "," + type + ",'" + escape(title) + "'," +
//                    frame.inlined + "," + frame.c1 + "," + frame.interpreted + ")");
        } else {
            fDataHolders.add(FDataHolder.builder()
                    .level(level)
                    .left(x)
                    .width(frame.total)
                    .type(type)
                    .title(escape(title))
                    .build());
//            out.println("f(" + level + "," + x + "," + frame.total + "," + type + ",'" + escape(title) + "')");
        }

        x += frame.self;
        for (Map.Entry<String, Frame> e : frame.entrySet()) {
            Frame child = e.getValue();
            if (child.total >= mintotal) {
                flameFDataGenerator(e.getKey(), child, level + 1, x);
            }
            x += child.total;
        }
        return fDataHolders;
    }


    private void printFrame(PrintStream out, String title, Frame frame, int level, long x) {
        int type = frame.getType();
        if (type == FRAME_KERNEL) {
            title = stripSuffix(title);
        }

        if ((frame.inlined | frame.c1 | frame.interpreted) != 0 && frame.inlined < frame.total && frame.interpreted < frame.total) {
//            fDataHolders.add(FDataHolder.builder()
//                            .level(level)
//                            .left(x)
//                            .width(frame.total)
//                            .type(type)
//                            .title(escape(title))
//                            .inln(frame.inlined)
//                            .c1(frame.c1)
//                            .Int(frame.interpreted)
//                            .build());
            out.println("f(" + level + "," + x + "," + frame.total + "," + type + ",'" + escape(title) + "'," +
                    frame.inlined + "," + frame.c1 + "," + frame.interpreted + ")");
        } else {
//            fDataHolders.add(FDataHolder.builder()
//                    .level(level)
//                    .left(x)
//                    .width(frame.total)
//                    .type(type)
//                    .title(escape(title))
//                    .build());
            out.println("f(" + level + "," + x + "," + frame.total + "," + type + ",'" + escape(title) + "')");
        }

        x += frame.self;
        for (Map.Entry<String, Frame> e : frame.entrySet()) {
            Frame child = e.getValue();
            if (child.total >= mintotal) {
                printFrame(out, e.getKey(), child, level + 1, x);
            }
            x += child.total;
        }

    }

    private boolean excludeTrace(String[] trace) {
        Pattern include = args.include;
        Pattern exclude = args.exclude;
        if (include == null && exclude == null) {
            return false;
        }

        for (String frame : trace) {
            if (exclude != null && exclude.matcher(frame).matches()) {
                return true;
            }
            if (include != null && include.matcher(frame).matches()) {
                include = null;
                if (exclude == null) break;
            }
        }

        return include != null;
    }

    static String stripSuffix(String title) {
        return title.substring(0, title.length() - 4);
    }

    static String escape(String s) {
        if (s.indexOf('\\') >= 0) s = s.replace("\\", "\\\\");
        if (s.indexOf('\'') >= 0) s = s.replace("'", "\\'");
        return s;
    }

    private static String getResource(String name) {
        try (InputStream stream = FlameGraph.class.getResourceAsStream(name)) {
            if (stream == null) {
                throw new IOException("No resource found");
            }

            ByteArrayOutputStream result = new ByteArrayOutputStream();
            byte[] buffer = new byte[64 * 1024];
            for (int length; (length = stream.read(buffer)) != -1; ) {
                result.write(buffer, 0, length);
            }
            return result.toString("UTF-8");
        } catch (IOException e) {
            throw new IllegalStateException("Can't load resource with name " + name);
        }
    }

    public static void main(String[] cmdline) throws IOException {
        Arguments args = new Arguments(cmdline);
        if (args.input == null) {
            System.out.println("Usage: java " + FlameGraph.class.getName() + " [options] input.collapsed [output.html]");
            System.out.println();
            System.out.println("Options:");
            System.out.println("  --title TITLE");
            System.out.println("  --reverse");
            System.out.println("  --minwidth PERCENT");
            System.out.println("  --skip FRAMES");
            System.out.println("  --include PATTERN");
            System.out.println("  --exclude PATTERN");
            System.out.println("  --highlight PATTERN");
            System.exit(1);
        }

        FlameGraph fg = new FlameGraph(args);
        fg.parse();
        fg.dump();
    }

    static class Frame extends TreeMap<String, Frame> {
        final byte type;
        long total;
        long self;
        long inlined, c1, interpreted;

        Frame(byte type) {
            this.type = type;
        }

        byte getType() {
            if (inlined * 3 >= total) {
                return FRAME_INLINED;
            } else if (c1 * 2 >= total) {
                return FRAME_C1_COMPILED;
            } else if (interpreted * 2 >= total) {
                return FRAME_INTERPRETED;
            } else {
                return type;
            }
        }

        private Frame getChild(String title, byte type) {
            Frame child = super.get(title);
            if (child == null) {
                super.put(title, child = new Frame(type));
            }
            return child;
        }

        Frame addChild(String title, long ticks) {
            total += ticks;

            Frame child;
            if (title.endsWith("_[j]")) {
                child = getChild(stripSuffix(title), FRAME_JIT_COMPILED);
            } else if (title.endsWith("_[i]")) {
                (child = getChild(stripSuffix(title), FRAME_JIT_COMPILED)).inlined += ticks;
            } else if (title.endsWith("_[k]")) {
                child = getChild(title, FRAME_KERNEL);
            } else if (title.endsWith("_[1]")) {
                (child = getChild(stripSuffix(title), FRAME_JIT_COMPILED)).c1 += ticks;
            } else if (title.endsWith("_[0]")) {
                (child = getChild(stripSuffix(title), FRAME_JIT_COMPILED)).interpreted += ticks;
            } else if (title.contains("::") || title.startsWith("-[") || title.startsWith("+[")) {
                child = getChild(title, FRAME_CPP);
            } else if (title.indexOf('/') > 0 && title.charAt(0) != '['
                    || title.indexOf('.') > 0 && Character.isUpperCase(title.charAt(0))) {
                child = getChild(title, FRAME_JIT_COMPILED);
            } else {
                child = getChild(title, FRAME_NATIVE);
            }
            return child;
        }

        void addLeaf(long ticks) {
            total += ticks;
            self += ticks;
        }

        int depth(long cutoff) {
            int depth = 0;
            if (size() > 0) {
                for (Frame child : values()) {
                    if (child.total >= cutoff) {
                        depth = Math.max(depth, child.depth(cutoff));
                    }
                }
            }
            return depth + 1;
        }
    }
}