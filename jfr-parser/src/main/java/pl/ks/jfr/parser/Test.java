package pl.ks.jfr.parser;
import java.io.File;
import java.io.IOException;
//import java.nio.file.Files;
import java.io.PrintWriter;
import java.time.ZoneId;
import java.util.List;

import jdk.jfr.ValueDescriptor;
import jdk.jfr.consumer.RecordingFile;
import jdk.jfr.consumer.RecordedEvent;

public class Test {
    public static void main(String[] args) throws IOException {
        File file = new File("C:\\Users\\haris\\Downloads\\sample-1.jfr");
        try (RecordingFile recordingFile = new RecordingFile(file.toPath())) {
//            System.out.println(recordingFile.);
            int c = 0,s=0;
            double max = 0,avg=0,tot=0;
            PrintWriter writer = new PrintWriter("C:\\Users\\haris\\Downloads\\output.collapsed");
            while (recordingFile.hasMoreEvents()) {
                RecordedEvent event = recordingFile.readEvent();
//                if(event.getStackTrace() != null)
//                System.out.println(event.getEventType().getName());
                if ("jdk.JavaMonitorEnter".equals(event.getEventType().getName()) ) {
                    c++;
//                    if(! (c > 1))
//                    {
//                        s+=event.getStackTrace().getFrames().size();
//                    List<ValueDescriptor> fields = event.getFields();
//                    for(ValueDescriptor field : fields)
//                        if(field.getName().equals("heapUsed"))
//                    String temp = event.getValue("heapSpace").toString();
//                    String t1 =temp.substring(temp.indexOf("reservedSize")).split("=")[1];
//                    String t2 = t1.substring(0,t1.length()-4).trim();
//                    tot+=event.getDuration().toMillis();
//                    max = Math.max(event.getDuration().toMillis(),max);
                        System.out.println(event.getEndTime());
//                    }

//                    else continue;
                }
            }
            avg = tot/c;
            System.out.println(c+"  "+s+"   "+avg+"   "+max+"   "+tot);
            writer.close();
        }
    }
}

