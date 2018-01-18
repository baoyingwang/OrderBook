package baoying.orderbook.app;

import baoying.orderbook.CommonMessage;
import baoying.orderbook.TradeMessage;
import baoying.orderbook.testtool.FIXMessageUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import quickfix.*;

import java.util.ArrayList;
import java.util.List;

//https://www.java2blog.com/spring-boot-web-application-example/
public class MatchingEngineFIXHelper {

    private final static Logger log = LoggerFactory.getLogger(MatchingEngineFIXHelper.class);


    public static Util.Tuple<Message,TradeMessage.OriginalOrder> processOneSideOfMatchingReport(TradeMessage.MatchedExecutionReport matchedExecutionReport, SimpleOMSEngine.MAKER_TAKER maker_taker){

        final TradeMessage.OriginalOrder originalOrder;
        switch(maker_taker){
            case MAKER :
                originalOrder = matchedExecutionReport._makerOriginOrder;
                break;
            case TAKER :
                originalOrder = matchedExecutionReport._takerOriginOrder;
                break;
            default :
                throw new RuntimeException("unknown side : "+maker_taker);
        }


        final int leavesQty ;
        final String executionID ;
        final TradeMessage.OriginalOrder _originOrder ;
        switch(maker_taker){
            case MAKER : leavesQty = matchedExecutionReport._makerLeavesQty;
                executionID = matchedExecutionReport._matchID + "_M";
                _originOrder = matchedExecutionReport._makerOriginOrder;
                break;
            case TAKER : leavesQty = matchedExecutionReport._takerLeavesQty;
                _originOrder = matchedExecutionReport._takerOriginOrder;
                executionID = matchedExecutionReport._matchID + "_T";
                break;
            default :
                throw new RuntimeException("unknown side : "+maker_taker);
        }

        Message executionReport = new Message();
        executionReport.getHeader().setString(35, "8"); // ExecutionReport

        //because FIX message is also used by vertx(for now at least)
        //client entity id is always assigned when building any FIX message.
        executionReport.getHeader().setString(56, originalOrder._clientEntityID);

        executionReport.setString(37, _originOrder._orderID); //OrderID
        executionReport.setString(17, executionID); //ExecID
        executionReport.setChar(150, TradeMessage.ExecutionType.TRADE.getFIX150Type()); // ExecType http://www.onixs.biz/fix-dictionary/5.0.SP1/tagNum_150.html

        executionReport.setString(31, String.valueOf(matchedExecutionReport._lastPrice)); //LastPx http://www.onixs.biz/fix-dictionary/5.0.SP1/tagNum_31.html
        executionReport.setString(17, String.valueOf(matchedExecutionReport._lastQty)); //lastQty http://www.onixs.biz/fix-dictionary/5.0.SP1/tagNum_32.html

        final TradeMessage.OrderStatus ordStatus = leavesQty==0?TradeMessage.OrderStatus.FILLED:TradeMessage.OrderStatus.PARTIALLY_FILLED;
        executionReport.setChar(39, ordStatus.getFIX39OrdStatus()); // NEW //OrderStatus http://www.onixs.biz/fix-dictionary/5.0.SP1/tagNum_39.html
        executionReport.setString(55, _originOrder._symbol); // non-repeating group
        executionReport.setChar(54, _originOrder._side.getFIX54Side());// Side 54 - 1:buy, 2:sell
        executionReport.setInt(151, leavesQty);// LeavesQty
        executionReport.setInt(14, _originOrder._qty - leavesQty); //CumQty http://www.onixs.biz/fix-dictionary/4.4/tagNum_14.html
        executionReport.setString(11, _originOrder._clientOrdID);

        return new Util.Tuple<Message,TradeMessage.OriginalOrder>(executionReport,originalOrder);

    }


    static TradeMessage.OriginalOrder buildOriginalOrder(CommonMessage.ExternalSource source,
                                                         Message paramMessage,
                                                         String orderID,
                                                         long zeroOLatencyOrdRrecvTimeNano)throws Exception{

        String symbol = paramMessage.getString(55);
        String clientEntity = paramMessage.getHeader().getString(49);
        CommonMessage.Side orderSide = CommonMessage.Side.fixValueOf(paramMessage.getChar(54));  //1 buy, 2 sell
        String clientOrdID = paramMessage.getString(11);

        final double price; //Price
        if(paramMessage.isSetField(44)){
            price = paramMessage.getDouble(44); //Price
        }else{
            price = -1;
        }
        CommonMessage.OrderType ordType = CommonMessage.OrderType.fixValueOf(paramMessage.getChar(40)); //OrdType 1:Market, 2:Limit
        int qty = paramMessage.getInt(38);

        TradeMessage.OriginalOrder originalOrder  = new TradeMessage.OriginalOrder(
                source,
                System.currentTimeMillis(),symbol,orderSide ,ordType, price, qty,
                orderID, clientOrdID, clientEntity);

        if(originalOrder._clientEntityID.startsWith(MatchingEngineApp.LATENCY_ENTITY_PREFIX)){
            originalOrder._isLatencyTestOrder = true;
            originalOrder._recvFromClient_sysNano_test = zeroOLatencyOrdRrecvTimeNano;
            if(paramMessage.isSetField(FIXMessageUtil.latencyTimesField)) {
                originalOrder._latencyTimesFromClient = paramMessage.getString(FIXMessageUtil.latencyTimesField);
            }
        }

        return originalOrder;
    }

    static List<Util.Tuple<Message,TradeMessage.OriginalOrder>> translateMatchedER(CommonMessage.ExternalSource source, TradeMessage.MatchedExecutionReport matchedExecutionReport) {
        List<Util.Tuple<Message,TradeMessage.OriginalOrder>> fixExecutionReports = new ArrayList<>();

        if(matchedExecutionReport._makerOriginOrder._source == source) {
            fixExecutionReports.add(processOneSideOfMatchingReport(matchedExecutionReport, SimpleOMSEngine.MAKER_TAKER.MAKER));
        }

        if(matchedExecutionReport._takerOriginOrder._source == source)
        {
            fixExecutionReports.add(processOneSideOfMatchingReport(matchedExecutionReport, SimpleOMSEngine.MAKER_TAKER.TAKER));
        }
        return fixExecutionReports;
    }

   static  Message translateSingeSideER(TradeMessage.SingleSideExecutionReport singleSideER){

        Message executionReport = new Message();
        executionReport.getHeader().setString(35, "8"); // ExecutionReport

       //because FIX message is also used by vertx(for now at least)
       //client entity id is always assigned when building any FIX message.
       executionReport.getHeader().setString(56, singleSideER._originOrder._clientEntityID);

        executionReport.setString(37, singleSideER._originOrder._orderID); //OrderID
        executionReport.setString(17, String.valueOf(singleSideER._msgID)); //ExecID
        executionReport.setChar(150, singleSideER._type.getFIX150Type()); // ExecType http://www.onixs.biz/fix-dictionary/5.0.SP1/tagNum_150.html

        final TradeMessage.OrderStatus ordStatus;
        switch (singleSideER._type){
            case NEW : ordStatus = TradeMessage.OrderStatus.NEW; break;
            case CANCELLED: ordStatus = TradeMessage.OrderStatus.CANCELLED; break;
            case  REJECTED: ordStatus = TradeMessage.OrderStatus.REJECTED; break;
            default: throw new RuntimeException("Cannot translateMatchedER execType:"+singleSideER._type.toString()+" to order status" );
        }
        executionReport.setChar(39, ordStatus.getFIX39OrdStatus()); // NEW //OrderStatus http://www.onixs.biz/fix-dictionary/5.0.SP1/tagNum_39.html
        executionReport.setString(55, singleSideER._originOrder._symbol); // non-repeating group
        executionReport.setChar(54, singleSideER._originOrder._side.getFIX54Side());// Side 54 - 1:buy, 2:sell
        executionReport.setInt(151, singleSideER._leavesQty);// LeavesQty
        executionReport.setInt(14, singleSideER._originOrder._qty - singleSideER._leavesQty);
        executionReport.setString(11, singleSideER._originOrder._clientOrdID);
        return executionReport;
    }
}
