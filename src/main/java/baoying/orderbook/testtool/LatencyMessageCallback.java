package baoying.orderbook.testtool;

import baoying.orderbook.MatchingEngine;
import baoying.orderbook.app.Util;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ListMultimap;
import com.google.common.collect.Multimap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import quickfix.Message;
import quickfix.SessionID;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.Collection;
import java.util.List;

import static java.nio.file.StandardOpenOption.APPEND;
import static java.nio.file.StandardOpenOption.CREATE;

public class LatencyMessageCallback extends FirstMessageCallback {

    private final static Logger log = LoggerFactory.getLogger(LatencyMessageCallback.class);

    ListMultimap<String, Long> orderTimes = ArrayListMultimap.create();

    @Override
    public void fromApp(Message paramMessage, SessionID paramSessionID) {
        log.debug("fromApp session received : {} " , paramMessage);

        try {


            long erTimeNano = System.nanoTime();
            String clientOrdID = paramMessage.getString(11);
            orderTimes.put(clientOrdID, erTimeNano);

            //don't worry about multi-thread issue, since each client has its own thread.
            String clientCompmID = paramMessage.getHeader().getString(56);
            Path e2eTimeFile = Paths.get("log/e2e_"+clientCompmID+".csv");
            if (!Files.exists(e2eTimeFile)) {
                Files.write(e2eTimeFile, ("clientOrdID,sendTime,newER,matchER" + "\n").getBytes(), APPEND, CREATE);
            }

            List<Long> times = orderTimes.get(clientOrdID);
            if(times.size() == 4){
                String sendTimeString = Util.formterOfOutputTime.format(Instant.ofEpochMilli(times.get(0)));
                long sendTimeNano = times.get(1);
                long newAck =times.get(2) - sendTimeNano;
                long matchAck = times.get(3)-sendTimeNano;
                Files.write(e2eTimeFile, (clientOrdID+","+sendTimeString +","+newAck+","+matchAck+"\n").getBytes(), APPEND, CREATE);
                orderTimes.removeAll(clientOrdID);
            }


        }catch(Exception e){
            log.error("",e);
        }finally {

        }

    }

    @Override
    public void toApp(Message paramMessage, SessionID paramSessionID) {

        try{
            log.debug("toApp session send : {} ", paramMessage);

            String clientOrdID = paramMessage.getString(11);
            long sendTimeEpochMS = System.currentTimeMillis();
            long sendTimeNano = System.nanoTime();
            orderTimes.put(clientOrdID,sendTimeEpochMS);
            orderTimes.put(clientOrdID,sendTimeNano);

        }catch(Exception e){
            log.error("",e);
        }finally {

        }

    }
}
