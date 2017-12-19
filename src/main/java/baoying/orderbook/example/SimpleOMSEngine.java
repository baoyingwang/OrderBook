package baoying.orderbook.example;

import baoying.orderbook.MatchingEngine;
import baoying.orderbook.TradeMessage;
import com.google.common.eventbus.Subscribe;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;

public class SimpleOMSEngine {

    //value: list of execution report(as Map<String, String)
    private final Map<String, List<Map<String, String>>> executionReportsByOrderID;
    private final BlockingQueue<long[]> _testTimeDataQueue;

    SimpleOMSEngine(){
        _testTimeDataQueue = new LinkedBlockingQueue<long[]>();
        executionReportsByOrderID = new ConcurrentHashMap<>();
    }

    List<Map<String, String>> getERsByOrderID(String orderID){
        return executionReportsByOrderID.get(orderID);
    }

    List<long[]> drainTestTimeDataQueue(){
        final List<long[]> deltaLatencyData = new ArrayList<>();
        _testTimeDataQueue.drainTo(deltaLatencyData);
        return deltaLatencyData;
    }
    void clearTestTimeDataQueue(){
        _testTimeDataQueue.clear();
    }

    @Subscribe
    public void process(TradeMessage.SingleSideExecutionReport singleSideExecutionReport) {

        if(singleSideExecutionReport._originOrder._clientEntityID.startsWith(MatchingEngine.LATENCY_ENTITY_PREFIX)) {
            List<Map<String, String>> originalReports = executionReportsByOrderID.get(singleSideExecutionReport._originOrder._orderID);
            List<Map<String, String>> originalReportsNew = new ArrayList<>();
            if (originalReports != null) {
                originalReportsNew.addAll(originalReports);
            }
            originalReportsNew.add(buildExternalERfromInternalER(singleSideExecutionReport));
            executionReportsByOrderID.put(singleSideExecutionReport._originOrder._orderID, originalReportsNew);
        }

    }

    @Subscribe
    public void process(TradeMessage.MatchedExecutionReport matchedExecutionReport) {

        //this is the only thread to modify the executionReportsByOrderID
        if(matchedExecutionReport._makerOriginOrder._clientEntityID.startsWith(MatchingEngine.LATENCY_ENTITY_PREFIX)) {
            addERStore(matchedExecutionReport, MAKER_TAKER.MAKER, matchedExecutionReport._makerOriginOrder);
        }

        if(matchedExecutionReport._takerOriginOrder._clientEntityID.startsWith(MatchingEngine.LATENCY_ENTITY_PREFIX)) {
            long outputConsumingNanoTime = System.nanoTime();
            addERStore(matchedExecutionReport, MAKER_TAKER.TAKER, matchedExecutionReport._takerOriginOrder);

            _testTimeDataQueue.add(new long[]{
                    matchedExecutionReport._takerOriginOrder._recvFromClientEpochMS,
                    matchedExecutionReport._taker_enterInputQ_sysNano_test
                            - matchedExecutionReport._takerOriginOrder._recvFromClient_sysNano_test,

                    matchedExecutionReport._taker_pickFromInputQ_sysNano_test
                            - matchedExecutionReport._taker_enterInputQ_sysNano_test,

                    matchedExecutionReport._matching_sysNano_test
                            - matchedExecutionReport._taker_pickFromInputQ_sysNano_test,

                    outputConsumingNanoTime - matchedExecutionReport._matching_sysNano_test});
        }
    }

    private void addERStore(TradeMessage.MatchedExecutionReport matchedExecutionReport, MAKER_TAKER maker_taker, TradeMessage.OriginalOrder originalOrder){

        long outputConsumingNanoTime = System.nanoTime();
        List<Map<String, String>> reportsBeforeUpdate = executionReportsByOrderID.get(originalOrder._orderID);
        List<Map<String, String>> reportsNew = reportsBeforeUpdate==null?new ArrayList<>():new ArrayList<>(reportsBeforeUpdate);
        reportsNew.add(buildExternalERfromInternalER(matchedExecutionReport, maker_taker));
        //replace with a new List, rather than update the exists List, to avoid concurrent modification issue.WHY not use copy on write?
        executionReportsByOrderID.put(originalOrder._orderID, reportsNew);
    }

    private Map<String, String> buildExternalERfromInternalER(TradeMessage.MatchedExecutionReport matchedExecutionReport, MAKER_TAKER maker_taker ){

        Map<String, String> erMap = new HashMap<>();
        erMap.put("lastPx", String.valueOf(matchedExecutionReport._lastPrice));
        erMap.put("lastQty", String.valueOf(matchedExecutionReport._lastQty));

        final int leavesQty ;
        final String executionID ;
        final TradeMessage.OriginalOrder originalOrder ;
        final TradeMessage.OriginalOrder contraOriginalOrder ;
        switch(maker_taker){
            case MAKER : leavesQty = matchedExecutionReport._makerLeavesQty;
                executionID = matchedExecutionReport._matchID + "_M";
                originalOrder = matchedExecutionReport._makerOriginOrder;
                contraOriginalOrder= matchedExecutionReport._takerOriginOrder;
                break;
            case TAKER : leavesQty = matchedExecutionReport._takerLeavesQty;
                originalOrder = matchedExecutionReport._takerOriginOrder;
                executionID = matchedExecutionReport._matchID + "_T";
                contraOriginalOrder= matchedExecutionReport._makerOriginOrder;
                break;
            default :
                throw new RuntimeException("unknown side : "+maker_taker);
        }

        erMap.put("cumQty", String.valueOf(originalOrder._qty - leavesQty));
        erMap.put("execType", "Trade"); //150 - F - http://www.onixs.biz/fix-dictionary/4.4/tagNum_150.html
        erMap.put("ordSatus", leavesQty==0?"Filled":"Partially filled");//39 - 1 - http://www.onixs.biz/fix-dictionary/4.4/tagNum_39.html

        return erMap;
    }


    private Map<String, String> buildExternalERfromInternalER(TradeMessage.SingleSideExecutionReport singleSideExecutionReport){

        Map<String, String> erMap = new HashMap<>();
        final TradeMessage.OriginalOrder originalOrder = singleSideExecutionReport._originOrder;
        erMap.put("leavesQty", String.valueOf(singleSideExecutionReport._leavesQty));
        erMap.put("cumQty",singleSideExecutionReport._msgID + "_S");
        erMap.put("cumQty", String.valueOf(originalOrder._qty - singleSideExecutionReport._leavesQty));
        erMap.put("execType", singleSideExecutionReport._type.toString());
        //TODO maybe this order status is NOT good/correct
        erMap.put("ordSatus", singleSideExecutionReport._type.toString());

        return erMap;
    }

    enum MAKER_TAKER{
        MAKER, TAKER;
    }
}
