package baoying.orderbook.app;


import io.vertx.core.buffer.Buffer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import quickfix.Message;

import java.nio.file.Path;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

public class Util {
    private final static Logger log = LoggerFactory.getLogger(Util.class);

    static final DateTimeFormatter fileNameFormatter =
            DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss.SSS'Z'").withZone( ZoneOffset.UTC );

    public static final DateTimeFormatter formterOfOutputTime =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'")
                    .withZone(ZoneId.of("UTC")) ;

    static String toCsvString(long[] array){

        return toCsvString(array, 0, array.length);

    }


    static String toCsvString(long[] array, int start, int endExclusive){

        StringBuilder csv = new StringBuilder();
        for(int i = start; i<endExclusive; i++){
            csv.append(array[i]).append(",");
        }

        if(csv.charAt(csv.length()-1) == ','){
            csv.deleteCharAt(csv.length()-1);
        }

        //TODO format the csv  based on standard https://tools.ietf.org/html/rfc4180
        return csv.toString();
    }

    //header is skipped
    static List<String[]> loadTailCsvLines(Path csvFile, long n, long total) throws Exception{
        List<String[]> tailResponseLatencyData = new ArrayList<>();
        long skipLines = total>n? total-n : 1;
        //TODO performance improvement - read twice(here) above get latency_data_count_all
        java.nio.file.Files.lines(csvFile).skip(skipLines).forEach(line ->{
            tailResponseLatencyData.add(line.split(","));
        });

        return tailResponseLatencyData;
    }

    //header is skipped
    static List<String[]> loadTailCsvLines(Path csvFile, long n) throws Exception{

        final long totalLineNum = java.nio.file.Files.lines(csvFile).count(); //http://www.adam-bien.com/roller/abien/entry/counting_lines_with_java_8
        return loadTailCsvLines(csvFile,  n,totalLineNum);
    }

    public static Buffer buildBuffer(Message fixER, String tailAsDelim){
        String fixERString = fixER.toString();
        Buffer erBuffer = Buffer.buffer();
        erBuffer.appendInt(fixERString.length());
        erBuffer.appendString(fixERString);
        erBuffer.appendString(tailAsDelim);

        return erBuffer;
    }

    //https://dzone.com/articles/whats-wrong-java-8-part-v
    //just internal use, don't public since it is NOT general for others.
    public static class Tuple<T, U> {
        public final T _1;
        public final U _2;
        public Tuple(T arg1, U arg2) {
            super();
            this._1 = arg1;
            this._2 = arg2;
        }
    }
}
