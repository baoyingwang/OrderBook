package baoying.orderbook.testtool.qfj;

import baoying.orderbook.app.Util;
import baoying.orderbook.testtool.FIXMessageUtil;
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



    @Override
    public void fromApp(Message paramMessage, SessionID paramSessionID) {
        log.debug("fromApp session received : {} " , paramMessage);

        try {

            FIXMessageUtil.recordLetencyTimeStamps(paramMessage);

        }catch(Exception e){
            log.error("",e);
        }finally {

        }

    }

    @Override
    public void toApp(Message message, SessionID paramSessionID) {

        try{
            log.debug("toApp session send : {} ", message);

            FIXMessageUtil.addLatencyText(message);

        }catch(Exception e){
            log.error("",e);
        }finally {

        }

    }
}
