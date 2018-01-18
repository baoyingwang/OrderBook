package baoying.orderbook.testtool;


import baoying.orderbook.app.MatchingEngineApp;
import baoying.orderbook.app.MatchingEngineVertxWrapper;
import baoying.orderbook.app.UniqIDGenerator;
import baoying.orderbook.app.Util;
import io.vertx.core.buffer.Buffer;
import quickfix.DataDictionary;
import quickfix.Message;

import java.io.BufferedOutputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

import static java.nio.file.StandardOpenOption.APPEND;
import static java.nio.file.StandardOpenOption.CREATE;

public class FIXMessageUtil {

    public static int latencyTimesField=58;

    public static Message buildNewOrderSingle(
                                        String clientCompID,
                                        String clientOrdID,
                                       String symbol,
                                       String price,
                                       String qty,
                                       String ordType,
                                       String side) {
        /**
         * <message name="NewOrderSingle" msgtype="D" msgcat="app">
         * <field name="ClOrdID" required="Y"/>
         * <component name="Instrument" required="Y"/>
         * <field name="Side" required="Y"/>
         * <field name="TransactTime" required="Y"/>
         * <component name="OrderQtyData" required="Y"/>
         * <field name="OrdType" required="Y"/> </message>
         */
        // NewOrderSingle
        Message newOrderSingle = new Message();
        // It is not required to set 8,49,56 if you know SessionID. See
        // DefaultSQFSingleSessionInitiator.java
        //newOrderSingle.getHeader().setString(8, "FIXT.1.1");

        //because FIX message is also used by vertx(for now at least)
        //client entity id is always assigned when building any FIX message.
        newOrderSingle.getHeader().setString(49, clientCompID);

        newOrderSingle.getHeader().setString(35, "D");
        newOrderSingle.setString(11, clientOrdID);
        newOrderSingle.setString(55, symbol); // non-repeating group
        newOrderSingle.setString(44, price); //
        // instrument->Symbol 55
        newOrderSingle.setString(54, "Bid".equals(side)?"1":"2");// Side 54 - 1:buy, 2:sell
        newOrderSingle.setUtcTimeStamp(60, LocalDateTime.ofInstant(Instant.now(), ZoneOffset.UTC), true); // TransactTime
        newOrderSingle.setString(38, qty); // non-repeating group
        // OrderQtyData->OrderQty
        // 38
        newOrderSingle.setString(40, "Market".equals(ordType)?"1":"2"); // OrdType 1:Market

        return newOrderSingle;

    }

    public static Message buildNewOrderSingleWithLatencyTimestamp(
            String clientCompID,
            String clientOrdID,
            String symbol,
            String price,
            String qty,
            String ordType,
            String side){

        Message order = FIXMessageUtil.buildNewOrderSingle(
                 clientCompID,
                 clientOrdID,
                 symbol,
                 price,
                 qty,
                 ordType,
                 side);

        FIXMessageUtil.addLatencyText(order);

        return order;
    }


    static Message buildHarcodedNewOrderSingleForTest() {
        /**
         * <message name="NewOrderSingle" msgtype="D" msgcat="app">
         * <field name="ClOrdID" required="Y"/>
         * <component name="Instrument" required="Y"/>
         * <field name="Side" required="Y"/>
         * <field name="TransactTime" required="Y"/>
         * <component name="OrderQtyData" required="Y"/>
         * <field name="OrdType" required="Y"/> </message>
         */
        // NewOrderSingle
        Message newOrderSingle = new Message();
        // It is not required to set 8,49,56 if you know SessionID. See
        // DefaultSQFSingleSessionInitiator.java

        newOrderSingle.getHeader().setString(35, "D");
        newOrderSingle.setString(11, "ClOrdID_" + System.currentTimeMillis());
        newOrderSingle.setString(55, "USDJPY"); // non-repeating group
        // instrument->Symbol 55
        newOrderSingle.setString(54, "1");// Side 54 - 1:buy, 2:sell
        newOrderSingle.setUtcTimeStamp(60, LocalDateTime.ofInstant(Instant.now(), ZoneOffset.UTC), true); // TransactTime
        newOrderSingle.setString(38, "200"); // non-repeating group
        // OrderQtyData->OrderQty
        // 38
        newOrderSingle.setString(40, "1"); // OrdType 1:Market

        return newOrderSingle;

    }

    //only required for Vert.x
    public static Message buildLogon(String senderCompID, String targetCompID){

        Message logon = new Message();
        // It is not required to set 8,49,56 if you know SessionID. See
        // DefaultSQFSingleSessionInitiator.java
        //newOrderSingle.getHeader().setString(8, "FIXT.1.1");

        //because FIX message is also used by vertx(for now at least)
        //client entity id is always assigned when building any FIX message.
        logon.getHeader().setString(49, senderCompID);
        logon.getHeader().setString(56, targetCompID);

        logon.getHeader().setString(35, "A");

        return logon;
    }

    public static void addLatencyText(Message order){
        String sendTimeString = Util.formterOfOutputTime.format(Instant.now());
        long sendTimeNano = System.nanoTime();
        order.setString(latencyTimesField, sendTimeString +","+String.valueOf(sendTimeNano));

    }

    public static void recordLetencyTimeStamps(Message er, long erTimeNano) throws Exception{

        String clientOrdID = er.getString(11);

        //don't worry about multi-thread issue, since each client has its own thread.
        String clientCompmID = er.getHeader().getString(56);
        Path e2eTimeFile = Paths.get("log/e2e_"+clientCompmID+".csv");
        if (!Files.exists(e2eTimeFile)) {
            Files.write(e2eTimeFile, ("sendTime,clientSendNano,svrRecvOrdNano,svrMatchedNano,clientRecvER,clientOrdID" + "\n").getBytes(), APPEND, CREATE);
        }

        String serverTimes = er.getString(FIXMessageUtil.latencyTimesField);
        Files.write(e2eTimeFile, (serverTimes+","+erTimeNano+","+clientOrdID+"\n").getBytes(), APPEND, CREATE);
    }

    public synchronized static void recordLetencyTimeStamps(Message er, long erTimeNano, OutputStream outputStream) throws Exception{

        String clientOrdID = er.getString(11);

        String serverTimes = er.getString(FIXMessageUtil.latencyTimesField);
        outputStream.write((serverTimes+","+erTimeNano+","+clientOrdID+"\n").getBytes());
        //Files.write(e2eTimeFile, (serverTimes+","+erTimeNano+","+clientOrdID+"\n").getBytes(), APPEND, CREATE);
    }

    static Message toMessage(String msg, String dictionary) throws Exception{
        DataDictionary dd  = new DataDictionary(dictionary); //"FIX50SP1.xml"
        boolean doValidation = false;
        Message er = new Message();
        er.fromString(msg,dd,doValidation);

        return er;
    }
}
