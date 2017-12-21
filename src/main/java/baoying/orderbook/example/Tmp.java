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
import java.util.stream.IntStream;

public class Tmp {

    public static void main(String[] args) throws Exception{

        DateTimeFormatter formterOfOutputTime =
                DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSZ")
                        .withZone(ZoneId.of("UTC")) ;

        long start = System.currentTimeMillis();
        IntStream.range(1, 5000000).forEach(it-> {
            String t = formterOfOutputTime.format(Instant.now());
            //System.out.println(t);
            if(t.length()< 28){
                System.out.println(t);
            }
        });

        long end = System.currentTimeMillis();
        System.out.println(end-start);
    }
}
