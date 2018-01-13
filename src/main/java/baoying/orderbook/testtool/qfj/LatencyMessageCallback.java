package baoying.orderbook.testtool.qfj;

import baoying.orderbook.app.Util;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import quickfix.Message;
import quickfix.SessionID;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;

import static java.nio.file.StandardOpenOption.APPEND;
import static java.nio.file.StandardOpenOption.CREATE;

public class LatencyMessageCallback extends FirstMessageCallback {

    private final static Logger log = LoggerFactory.getLogger(LatencyMessageCallback.class);

    public static int latencyTimesField=58;

    @Override
    public void fromApp(Message paramMessage, SessionID paramSessionID) {
        log.debug("fromApp session received : {} " , paramMessage);

        try {


            long erTimeNano = System.nanoTime();
            String clientOrdID = paramMessage.getString(11);

            //don't worry about multi-thread issue, since each client has its own thread.
            String clientCompmID = paramMessage.getHeader().getString(56);
            Path e2eTimeFile = Paths.get("log/e2e_"+clientCompmID+".csv");
            if (!Files.exists(e2eTimeFile)) {
                Files.write(e2eTimeFile, ("sendTime,clientSendNano,svrRecvOrdNano,svrMatchedNano,clientRecvER,clientOrdID" + "\n").getBytes(), APPEND, CREATE);
            }

            String serverTimes = paramMessage.getString(latencyTimesField);
            Files.write(e2eTimeFile, (serverTimes+","+erTimeNano+","+clientOrdID+"\n").getBytes(), APPEND, CREATE);



        }catch(Exception e){
            log.error("",e);
        }finally {

        }

    }

    @Override
    public void toApp(Message message, SessionID paramSessionID) {

        try{
            log.debug("toApp session send : {} ", message);

            String sendTimeString = Util.formterOfOutputTime.format(Instant.now());
            long sendTimeNano = System.nanoTime();
            message.setString(latencyTimesField, sendTimeString +","+String.valueOf(sendTimeNano));

        }catch(Exception e){
            log.error("",e);
        }finally {

        }

    }
}
