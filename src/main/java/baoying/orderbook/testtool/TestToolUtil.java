package baoying.orderbook.testtool;

import baoying.orderbook.app.MatchingEngineApp;
import baoying.orderbook.testtool.qfj.FirstQFJClientBatch;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import quickfix.Message;

import java.io.BufferedOutputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicInteger;

import static java.nio.file.StandardOpenOption.APPEND;
import static java.nio.file.StandardOpenOption.CREATE;

public class TestToolUtil {

    private final static Logger log = LoggerFactory.getLogger(TestToolUtil.class);

    public static double getCurrentRateInSecond(Instant start, AtomicInteger totalSent){

        int totalSentNow = totalSent.get();

        Instant now = Instant.now();
        long durationInSecond = now.getEpochSecond() - start.getEpochSecond();
        double rateInSecond = totalSentNow*1.0 / durationInSecond;
        //double rateInMinute = rateInSecond * 60;

        return rateInSecond;
        //log.info("{} - totalSent:{}, rateInSecond:{}", _testToolArgs.clientCompIDPrefix, totalSentNow, String.format("%.2f", rateInSecond));
    }

    public static BufferedOutputStream setupOutputLatencyFile(String latencyDataFile, int bufferSize)throws Exception{

        Path e2eTimeFile = Paths.get(latencyDataFile);
        if (!Files.exists(e2eTimeFile)) {
            Files.write(e2eTimeFile, ("sendTime,clientSendNano,svrRecvOrdNano,svrMatchedNano,clientRecvER,clientOrdID" + "\n").getBytes(), APPEND, CREATE);
        }

        BufferedOutputStream output = new BufferedOutputStream(
                new FileOutputStream(latencyDataFile, true),
                bufferSize
        );

        return output;

    }

    public static void writeLatencyData(Message er, long erTimeNano, OutputStream output) throws Exception{

        String clientCompID = er.getHeader().getString(56);
        FIXMessageUtil.recordLetencyTimeStamps(er, erTimeNano, output);

    }

    public static void writeLatencyData(String erString, long erTimeNano, OutputStream output) throws Exception{

        Message er = FIXMessageUtil.toMessage(erString, "FIX50SP1.xml");
        if(! (er.getChar(150) == 'F') ){
            log.error("cannot record lantency for non fill(partial or full fill) execution report");
            return;
        }
        writeLatencyData(er, erTimeNano, output);
    }
}
