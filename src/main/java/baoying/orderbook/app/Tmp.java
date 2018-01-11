package baoying.orderbook.app;


import quickfix.DataDictionary;
import quickfix.DefaultMessageFactory;
import quickfix.Message;
import quickfix.MessageFactory;

import java.io.BufferedOutputStream;
import java.io.DataOutputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;

public class Tmp {

    static BufferedOutputStream output;

    static {
        try {
            output = new BufferedOutputStream(
                    new FileOutputStream("log/btrace.csv")
            );
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }
    }

    public static void main(String[] args) throws Exception{


        output.write((Instant.now()+" hello\n").getBytes());

        output.write((Instant.now()+" hello\n").getBytes());

        output.close();
 }
}
