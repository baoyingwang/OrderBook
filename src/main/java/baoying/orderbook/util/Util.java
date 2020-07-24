package baoying.orderbook.util;


import com.beust.jcommander.IStringConverter;
import io.vertx.core.buffer.Buffer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import quickfix.Message;

import java.nio.file.Path;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.*;

public class Util {
    private final static Logger log = LoggerFactory.getLogger(Util.class);

    //TODO high 这个formatter线程不安全，不能弄成public static。虽然我这里只用了一次，但是这是不好的习惯（pubic static）
    public static final DateTimeFormatter fileNameFormatter =
            DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss.SSS'Z'").withZone( ZoneOffset.UTC );

    //TODO high 这个formatter线程不安全，不能弄成public static。
    public static final DateTimeFormatter formterOfOutputTime =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'")
                    .withZone(ZoneId.of("UTC")) ;

    public static String toCsvString(long[] array){

        return toCsvString(array, 0, array.length);

    }


    public static String toCsvString(long[] array, int start, int endExclusive){

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
    public static List<String[]> loadTailCsvLines(Path csvFile, long n, long total) throws Exception{
        List<String[]> tailResponseLatencyData = new ArrayList<>();
        long skipLines = total>n? total-n : 1;
        //TODO performance improvement - read twice(here) above get latency_data_count_all
        java.nio.file.Files.lines(csvFile).skip(skipLines).forEach(line ->{
            tailResponseLatencyData.add(line.split(","));
        });

        return tailResponseLatencyData;
    }

    //header is skipped
    public static List<String[]> loadTailCsvLines(Path csvFile, long n) throws Exception{

        final long totalLineNum = java.nio.file.Files.lines(csvFile).count(); //http://www.adam-bien.com/roller/abien/entry/counting_lines_with_java_8
        return loadTailCsvLines(csvFile,  n,totalLineNum);
    }

    public static Buffer buildBuffer(Message fixMsg, String tailAsDelim){

        String fixString = fixMsg.toString();

        Buffer buffer = Buffer.buffer();
        buffer.appendInt(fixString.length());
        buffer.appendString(fixString);
        buffer.appendString(tailAsDelim);

        return buffer;
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

    public class CSVListConverter implements IStringConverter<List<String>> {

        @Override
        public List<String> convert(String csvValues) {
            String [] values = csvValues.split(",");
            return Arrays.asList(values);
        }
    }

}
