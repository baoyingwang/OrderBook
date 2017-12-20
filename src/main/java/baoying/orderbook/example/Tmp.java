package baoying.orderbook.example;


import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.FileReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

public class Tmp {

    public static void main(String[] args) throws Exception{

        Path outputAppendingLatencyDataFile = Paths.get("log/GC.txt");
        long maxNumberOfResponseLatencyData = 200;
        List<String[]> r = Util.loadTailCsvLines(outputAppendingLatencyDataFile, maxNumberOfResponseLatencyData);
        System.out.println(r.size());
    }
}
