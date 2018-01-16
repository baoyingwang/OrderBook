package baoying.orderbook.app;


import baoying.orderbook.testtool.vertx.VertxClientRoundBatch;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;

public class Tmp {
    private final static Logger log = LoggerFactory.getLogger(Tmp.class);

    public static void main(String[] args) throws Exception{

        Map<String, String> x = new HashMap<>();
        x.put("x","x1");
        x.put("y","y1");

        log.error("{}",x);

    }
}
