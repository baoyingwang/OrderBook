package baoying.orderbook.example;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.nio.file.StandardOpenOption.APPEND;
import static java.nio.file.StandardOpenOption.CREATE;

//TODO move this out of this project, to another java util project.
public class GCLogUtil {

    public static void main(String[] args) throws Exception{
        if(args.length<1){
            throw new RuntimeException("empty argument, expected $0 gcFile [gcSummaryCSVFile]");
        }

        final Path gcLogPath = Paths.get(args[0]);
        if(!Files.exists(gcLogPath)){
            throw new RuntimeException("gc file not exists:"+gcLogPath.toAbsolutePath());
        }

        List<GCLogEntry> GCLogEntryList = parseGCLog(gcLogPath);
        List<String> gcLogEntrySummaryCSVLines = GCLogEntryList.stream()
                .map(entry -> entry._logTime +","+entry._microTook+","+entry._type)
                .collect(Collectors.toList());

        if(args.length>=2){
            Path outputGCTookFile = Paths.get(args[1]);
            if(!Files.exists(outputGCTookFile)){
                Files.delete(outputGCTookFile);
            }

            Files.write( outputGCTookFile, ("gcLogTime,took_us,type\n").getBytes(),  CREATE);
            Files.write(outputGCTookFile, gcLogEntrySummaryCSVLines, UTF_8, APPEND, CREATE);
        }else{
            System.out.println("gcLogTime,took_us,type");
            gcLogEntrySummaryCSVLines.forEach(line -> System.out.println(line));
        }
    }

    //note: the Z could be +0800, etc. For more detail, pls see jdk manual
    private static final DateTimeFormatter formterOfInputGCLogTime =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSZ") ;
    private static String tAppThreadStopped = "Total time for which application threads were stopped";

    //2017-12-14T11:59:32.858+0800 + 145.5(note: from 0.0001455 seconds) + "Total time for which application threads were stopped" + empty comments
    //2017-12-14T11:59:32.858+0800: 0.796: Total time for which application threads were stopped: 0.0001455 seconds, Stopping threads took: 0.0000128 seconds

    static List<GCLogEntry> parseGCLog(Path gcLog) throws Exception{

        Stream<String> logStream = Files.lines(gcLog);

        //TODO filter to remove Optional
        List<GCLogEntry> pauseData = logStream
                .map(line -> parseGCLogLine(line))
                .filter( optionalEntry -> optionalEntry.isPresent()) //only keep those ones with vlaues
                .map(optionalEntry -> optionalEntry.get()) //get value
                .collect(Collectors.toList()); //to list

        return pauseData;

    }

    //String string ="2017-12-14T15:07:38.333+0800: 0.784: Total time for which application threads were stopped: 0.0001528 seconds, Stopping threads took: 0.0000186 seconds";
    private static Optional<GCLogEntry> parseAppThreadStopped(String string) {

        //?! at the heading of a group means ignore this group when counting the result group
        //the ? at .*?, means to reluctant(not-greedy). Without the '?', it will return the last double number in this line on the next group.
        String regex = "(^[0-9a-zA-Z\\-:\\.\\+]*).*? (?!Total time for which application threads were stopped: )([-+]?[0-9]*\\.?[0-9]+([eE][-+]?[0-9]+)?) seconds";
        Pattern pStart = Pattern.compile(regex);
        Matcher m = pStart.matcher(string);
        Instant logTimeInstant=null;
        double tookMacroS=-1;
        if(m.find()&&m.groupCount()>=2){

            String logTime = m.group(1);
            if(logTime.endsWith(":")){
                logTime = logTime.substring(0, logTime.length()-1);
            }
            logTimeInstant=Instant.from(formterOfInputGCLogTime.parse(logTime));

            tookMacroS = Double.parseDouble(m.group(2)) * 1000_000;
        }else{
            return Optional.empty();
        }

        return Optional.of(new GCLogEntry(logTimeInstant,tookMacroS,tAppThreadStopped));
    }

    //2017-12-14T11:59:32.951+0800 + 10000(note from +real=0.01 secs) + GC (Allocation Failure) + ""
    //2017-12-14T11:59:33.130+0800 + 10000(note from +real=0.01 secs) + GC (Metadata GC Threshold) + ""
    //2017-12-14T11:59:33.139+0800 + 30000(note from +real=0.03 secs) + Full GC (Metadata GC Threshold) + ""
    //2017-12-14T11:59:32.951+0800: 0.889: [GC (Allocation Failure) [PSYoungGen: 64512K->10743K(75264K)] 64512K->13406K(247296K), 0.0117521 secs] [Times: user=0.00 sys=0.00, real=0.01 secs]
    //2017-12-14T11:59:33.130+0800: 1.067: [GC (Metadata GC Threshold) [PSYoungGen: 30145K->6932K(139776K)] 32808K->9666K(311808K), 0.0085236 secs] [Times: user=0.03 sys=0.00, real=0.01 secs]
    //2017-12-14T11:59:33.139+0800: 1.076: [Full GC (Metadata GC Threshold) [PSYoungGen: 6932K->0K(139776K)] [ParOldGen: 2734K->6815K(108032K)] 9666K->6815K(247808K), [Metaspace: 20882K->20882K(1069056K)], 0.0342859 secs] [Times: user=0.06 sys=0.01, real=0.03 secs]
    private static  Optional<GCLogEntry> parseGCTook(String string) {

        //?! at the heading of a group means ignore this group when counting the result group
        //the ? at .*?, means to reluctant(not-greedy). Without the '?', it will return the last double number in this line on the next group.
        String regex = "(^[0-9a-zA-Z\\-:\\.\\+]*).*?\\[(.*?) \\[.*real=([-+]?[0-9]*\\.?[0-9]+([eE][-+]?[0-9]+)?) secs";
        Pattern pStart = Pattern.compile(regex);
        Matcher m = pStart.matcher(string);
        Instant logTimeInstant=null;
        double tookMacroS=-1;
        String type="";
        if(m.find()&&m.groupCount()>=3) {
            String logTime = m.group(1);
            if(logTime.endsWith(":")){
                logTime = logTime.substring(0, logTime.length()-1);
            }
            logTimeInstant=Instant.from(formterOfInputGCLogTime.parse(logTime));

            type = m.group(2);
            tookMacroS = Double.parseDouble(m.group(3)) * 1000_000;
        }else{
            return Optional.empty();
        }

        return Optional.of(new GCLogEntry(logTimeInstant,tookMacroS,type));
    }


    private static Optional<GCLogEntry> parseGCLogLine(String line) {

        final Optional<GCLogEntry> entry;
        if(line.indexOf(tAppThreadStopped)> 0){

            entry =  parseAppThreadStopped(line);

        }else if(line.indexOf("[GC " ) > 0 || line.indexOf("[Full GC ") >0){
            entry = parseGCTook(line);
        }else{
            entry = Optional.empty();
        }

        return  entry;
    }

    static class GCLogEntry{
        final Instant _logTime; //TODO use UTC
        final double _microTook;
        final String _type;
        GCLogEntry(Instant logTime, double microLatency, String type){
            _logTime = logTime;
            _microTook = microLatency;
            _type = type;
        }
    }



}
