package baoying.orderbook.testtool;

import baoying.orderbook.app.Util;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ListMultimap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import quickfix.Message;
import quickfix.SessionID;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.List;

import static java.nio.file.StandardOpenOption.APPEND;
import static java.nio.file.StandardOpenOption.CREATE;

public class LatencyMessageCallback extends FirstMessageCallback {

    private final static Logger log = LoggerFactory.getLogger(LatencyMessageCallback.class);

    public static int latencyTimesField=58;

    ListMultimap<String, Long> erAckTimes = ArrayListMultimap.create();

    @Override
    public void fromApp(Message paramMessage, SessionID paramSessionID) {
        log.debug("fromApp session received : {} " , paramMessage);

        try {


            long erTimeNano = System.nanoTime();
            String clientOrdID = paramMessage.getString(11);
            erAckTimes.put(clientOrdID, erTimeNano);

            //don't worry about multi-thread issue, since each client has its own thread.
            String clientCompmID = paramMessage.getHeader().getString(56);
            Path e2eTimeFile = Paths.get("log/e2e_"+clientCompmID+".csv");
            if (!Files.exists(e2eTimeFile)) {
                Files.write(e2eTimeFile, ("clientOrdID,sendTimeEpochMS,sendTimeNano,_recvFromClient_sysNano,enterInputQ_sysNano,pickFromInputQ_sysNano,matched_sysNano,pickedFromOutputBus_nano,newER,matchER" + "\n").getBytes(), APPEND, CREATE);
            }

            List<Long> times = erAckTimes.get(clientOrdID);
            if(times.size() == 2){

                String serverTimes = paramMessage.getString(latencyTimesField);
                long newAck =times.get(0) ;
                long matchAck = times.get(1);
                Files.write(e2eTimeFile, (clientOrdID+","+serverTimes+","+newAck+","+matchAck+"\n").getBytes(), APPEND, CREATE);
                erAckTimes.removeAll(clientOrdID);
            }


        }catch(Exception e){
            log.error("",e);
        }finally {

        }

    }

    @Override
    public void toApp(Message message, SessionID paramSessionID) {

        try{
            log.debug("toApp session send : {} ", message);

            long sendTimeEpochMS = System.currentTimeMillis();
            long sendTimeNano = System.nanoTime();
            message.setString(latencyTimesField, String.valueOf(sendTimeEpochMS)+","+String.valueOf(sendTimeNano));

        }catch(Exception e){
            log.error("",e);
        }finally {

        }

    }
}
