package baoying.orderbook.testtool;


import baoying.orderbook.app.UniqIDGenerator;
import quickfix.Message;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

public class FIXOrderBuilder {

    static Message buildNewOrderSingle(String clientCompID,
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

        newOrderSingle.getHeader().setString(35, "D");
        newOrderSingle.setString(11, clientCompID+ UniqIDGenerator.next());
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
}
