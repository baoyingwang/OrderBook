package baoying.orderbook.app;

import baoying.orderbook.MatchingEngine;
import baoying.orderbook.TradeMessage;
import com.google.common.eventbus.Subscribe;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicLong;

public class SimpleOMSEngine {

    public static final String IGNORE_ENTITY_PREFIX ="BACKGROUND";

    PerfTestDataForWeb perfTestDataForWeb = new PerfTestDataForWeb();
    class PerfTestDataForWeb {

        AtomicLong _placedOrderCounter = new AtomicLong(0);
        AtomicLong _latencyOrderCounter = new AtomicLong(0);
        TradeMessage.OriginalOrder _firstOriginalOrderSinceTest = null;
        TradeMessage.OriginalOrder _lastOriginalOrderSinceTest = null;

        private BlockingQueue<long[]> _testTimeDataQueue = new LinkedBlockingQueue<long[]>();
        int maxQueueSize = 80;
        int popSizeAfterFull = 30;

        void resetBeforeTest(){
            _placedOrderCounter.set(0);
            _firstOriginalOrderSinceTest = null;
            _lastOriginalOrderSinceTest = null;

            _testTimeDataQueue.clear();
        }

        //TODO will thread issue? since maybe multi threads call this method.
        void recordNewOrder(TradeMessage.OriginalOrder originalOrder){
            long nthOrderSinceTest = _placedOrderCounter.incrementAndGet();

            if(originalOrder._clientEntityID.startsWith(MatchingEngine.LATENCY_ENTITY_PREFIX)){
                _latencyOrderCounter.incrementAndGet();
            }

            if(_testTimeDataQueue.size() >= maxQueueSize){ //not the nthOrderSinceTest, since the nthOrderSinceTest can be drained.
                for(int i=0; i<popSizeAfterFull; i++ ){
                    _testTimeDataQueue.poll();
                }
            }

            if(nthOrderSinceTest == 1) { _firstOriginalOrderSinceTest = originalOrder; }
            else{_lastOriginalOrderSinceTest = originalOrder;}
        }

        long count(){
            return _placedOrderCounter.get();
        }

        long latencyOrdCount(){
            return _latencyOrderCounter.get();
        }
        long startInEpochMS(){
            if(_firstOriginalOrderSinceTest == null){
                throw new RuntimeException("fail to get start in MS");
            }
            return _firstOriginalOrderSinceTest._recvFromClientEpochMS;
        }

        long lastInEpochMS(){
            if(_lastOriginalOrderSinceTest == null){
                throw new RuntimeException("fail to get end in MS");
            }
            return _lastOriginalOrderSinceTest._recvFromClientEpochMS;
        }

        List<long[]> getListCopy(){
             return new ArrayList<>(_testTimeDataQueue);
        }
    }

    //value: list of execution report(as Map<String, String)
    private final Map<String, List<Map<String, String>>> executionReportsByOrderID;


    SimpleOMSEngine(){
        executionReportsByOrderID = new ConcurrentHashMap<>();
    }

    List<Map<String, String>> getERsByOrderID(String orderID){
        return executionReportsByOrderID.get(orderID);
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

        //ignore maker side, because maker orders always sit in book during test
        if(matchedExecutionReport._takerOriginOrder._clientEntityID.startsWith(MatchingEngine.LATENCY_ENTITY_PREFIX)) {
            long outputConsumingNanoTime = System.nanoTime();
            perfTestDataForWeb._testTimeDataQueue.add(new long[]{
                    matchedExecutionReport._takerOriginOrder._recvFromClientEpochMS,
                    matchedExecutionReport._taker_enterInputQ_sysNano_test
                            - matchedExecutionReport._takerOriginOrder._recvFromClient_sysNano_test,

                    matchedExecutionReport._taker_pickFromInputQ_sysNano_test
                            - matchedExecutionReport._taker_enterInputQ_sysNano_test,

                    matchedExecutionReport._matched_sysNano_test
                            - matchedExecutionReport._taker_pickFromInputQ_sysNano_test,

                    outputConsumingNanoTime - matchedExecutionReport._matched_sysNano_test});
        }

        if(! matchedExecutionReport._makerOriginOrder._clientEntityID.startsWith(IGNORE_ENTITY_PREFIX)
                && matchedExecutionReport._makerOriginOrder._clientEntityID.startsWith(MatchingEngine.LATENCY_ENTITY_PREFIX)) {
            addERStore(matchedExecutionReport, MAKER_TAKER.MAKER, matchedExecutionReport._makerOriginOrder);
        }

        if(! matchedExecutionReport._takerOriginOrder._clientEntityID.startsWith(IGNORE_ENTITY_PREFIX)
                && ! matchedExecutionReport._takerOriginOrder._clientEntityID.startsWith(MatchingEngine.LATENCY_ENTITY_PREFIX)) {
            addERStore(matchedExecutionReport, MAKER_TAKER.TAKER, matchedExecutionReport._takerOriginOrder);
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
