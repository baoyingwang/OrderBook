package baoying.orderbook.app;


import baoying.orderbook.TradeMessage;
import com.google.common.eventbus.Subscribe;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;

public class SimpleOMSEngine {

    public static final String IGNORE_ENTITY_PREFIX ="BACKGROUND";
    private static Executor _executor = Executors.newSingleThreadExecutor(new ThreadFactory() {
        @Override
        public Thread newThread(Runnable r) {
            return new Thread(r, "Thread - SimpleOMS");
        }
    });

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

        _executor.execute(()->{
            internalProcess(singleSideExecutionReport);
        });
    }

    public void internalProcess(TradeMessage.SingleSideExecutionReport singleSideExecutionReport) {

        if( singleSideExecutionReport._originOrder._clientEntityID.startsWith(IGNORE_ENTITY_PREFIX)){
            return;
        }

        if( singleSideExecutionReport._originOrder._clientEntityID.startsWith(MatchingEngineApp.LATENCY_ENTITY_PREFIX)){
            return;
        }

        List<Map<String, String>> originalReports = executionReportsByOrderID.get(singleSideExecutionReport._originOrder._orderID);

        List<Map<String, String>> originalReportsNew = new ArrayList<>();
        if (originalReports != null) {
            originalReportsNew.addAll(originalReports);
        }
        originalReportsNew.add(buildExternalERfromInternalER(singleSideExecutionReport));
        executionReportsByOrderID.put(singleSideExecutionReport._originOrder._orderID, originalReportsNew);

    }

    @Subscribe
    public void process(TradeMessage.MatchedExecutionReport matchedExecutionReport){
        _executor.execute(()->{
            internalProcess(matchedExecutionReport);
        });
    }

    public void internalProcess(TradeMessage.MatchedExecutionReport matchedExecutionReport) {

        if(! matchedExecutionReport._makerOriginOrder._clientEntityID.startsWith(IGNORE_ENTITY_PREFIX)
                && ! matchedExecutionReport._makerOriginOrder._clientEntityID.startsWith(MatchingEngineApp.LATENCY_ENTITY_PREFIX)) {

            addERStore(matchedExecutionReport, MAKER_TAKER.MAKER, matchedExecutionReport._makerOriginOrder);

        }

        if(! matchedExecutionReport._takerOriginOrder._clientEntityID.startsWith(IGNORE_ENTITY_PREFIX)
                && ! matchedExecutionReport._takerOriginOrder._clientEntityID.startsWith(MatchingEngineApp.LATENCY_ENTITY_PREFIX)) {

            addERStore(matchedExecutionReport, MAKER_TAKER.TAKER, matchedExecutionReport._takerOriginOrder);

        }

    }

    private void addERStore(TradeMessage.MatchedExecutionReport matchedExecutionReport, MAKER_TAKER maker_taker, TradeMessage.OriginalOrder originalOrder){

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
        erMap.put("description", singleSideExecutionReport._description);

        return erMap;
    }

    enum MAKER_TAKER{
        MAKER, TAKER;
    }
}
