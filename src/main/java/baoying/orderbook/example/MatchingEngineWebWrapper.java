package baoying.orderbook.example;

import baoying.orderbook.CommonMessage;
import baoying.orderbook.MarketDataMessage;
import baoying.orderbook.MatchingEngine;
import baoying.orderbook.TradeMessage;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.PostConstruct;
import java.util.*;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

//https://www.java2blog.com/spring-boot-web-application-example/
@Configuration
@ComponentScan
@EnableAutoConfiguration

@SpringBootApplication

@RestController
@RequestMapping("/matching")
public class MatchingEngineWebWrapper {

    private final static Logger log = LoggerFactory.getLogger(MatchingEngine.class);

    private final MatchingEngine _matchingEngine_USDJPY;
    private final MatchingEngine _matchingEngine_USDHKD;

    private final EngineExecConsumingThread _execUSDJPYThread;
    private final EngineMDConsumingThread _mdUSDJPYThread;
    private final EngineExecConsumingThread _execUSDHKDThread;
    private final EngineMDConsumingThread _mdUSDHKDThread;

    private InternalTriggerOrderBookThread _internalTriggerOrderBookThread;
    //value: list of er(as Map<String, String)
    private Map<String, List<Map<String, String>>> executionReportsByOrderID;
    private Map<String, MarketDataMessage.AggregatedOrderBook> orderbookBySymbol;

    MatchingEngineWebWrapper(){
        orderbookBySymbol = new ConcurrentHashMap<>();
        executionReportsByOrderID = new ConcurrentHashMap<>();

        _matchingEngine_USDJPY = new MatchingEngine("USDJPY");
        _matchingEngine_USDHKD = new MatchingEngine("USDHKD");

        _execUSDJPYThread = new EngineExecConsumingThread("EngineExecConsumingThread-USDJPY", _matchingEngine_USDJPY._outputQueueForExecRpt );
        _mdUSDJPYThread = new EngineMDConsumingThread("EngineMDConsumingThread-USDJPY", "USDJPY",_matchingEngine_USDJPY._outputQueueForAggBookAndBookDelta );

        _execUSDHKDThread = new EngineExecConsumingThread("EngineExecConsumingThread-HKD", _matchingEngine_USDHKD._outputQueueForExecRpt );
        _mdUSDHKDThread = new EngineMDConsumingThread("EngineMDConsumingThread-HKD", "USDHKD",_matchingEngine_USDHKD._outputQueueForAggBookAndBookDelta );

        List<MatchingEngine> engines = new ArrayList<>();
        engines.add(_matchingEngine_USDJPY);
        engines.add(_matchingEngine_USDHKD);
        _internalTriggerOrderBookThread =  new InternalTriggerOrderBookThread("InternalTriggerOrderBookThread",engines);

    }

    @PostConstruct
    public void start(){

        log.info("start the MatchingEngineWebWrapper");
        _matchingEngine_USDJPY.start();
        _matchingEngine_USDHKD.start();

        _execUSDJPYThread.start();
        _mdUSDJPYThread.start();

        _execUSDHKDThread.start();
        _mdUSDHKDThread.start();

        _internalTriggerOrderBookThread.start();

        placeOrderBookForTest();
    }

    private MatchingEngine getMatchingEngine(String symbol){

        if("USDJPY".equals(symbol)){

            return _matchingEngine_USDJPY;

        }else if("USDHKD".equals(symbol)){
            return _matchingEngine_USDHKD;
        } else{
            String errorInfo = "ERROR - Only USDJPY and USDHKD is supported. Unknown symbol:"+ symbol;
            log.error(errorInfo);

            //TODO return Optional
            return null;
        }
    }

    @RequestMapping("/place_order")
    public String placeOrder(@RequestParam(value = "symboL", defaultValue = "USDJPY") String symbol,
                             @RequestParam(value = "client_entity", defaultValue = "BankA") String clientEntity,
                             @RequestParam(value = "side", defaultValue="Bid") String side,
                             @RequestParam(value = "price", defaultValue="124.0") double price,
                             @RequestParam(value = "qty", defaultValue = "1000000") int qty){

        String clientOrdID = clientEntity+"_"+System.nanoTime();
        String orderID = symbol+"_"+clientEntity+"_"+System.nanoTime();

        CommonMessage.Side orderSide;
        if("Bid".equalsIgnoreCase(side)){
            orderSide = CommonMessage.Side.BID;
        }else if("Offer".equalsIgnoreCase(side)){
            orderSide = CommonMessage.Side.OFFER;
        }else{
            log.error("unknown side : " + side);
            return "ERROR - unknown side : " + side;
        }

        TradeMessage.OriginalOrder originalOrder  = new TradeMessage.OriginalOrder(symbol,orderSide , price, qty, orderID, clientOrdID, clientEntity);
        MatchingEngine engine = getMatchingEngine(originalOrder._symbol);
        if(engine != null) {
            log.info("received order request, symbol:{}, client ordID:{}, ordID:{}", new String[]{symbol,clientOrdID,orderID });
            engine.addOrder(originalOrder);
            return orderID;
        }else{
            return "ERROR - not supported symbol:" + originalOrder._symbol;
        }

    }

    @RequestMapping("/test_build_order_book")
    public String placeOrderBookForTest() {

        String symbol="USDJPY";
        MatchingEngine engine = _matchingEngine_USDJPY;
        CommonMessage.Side side = CommonMessage.Side.BID;
        CommonMessage.Side oSide = CommonMessage.Side.OFFER;
        List<TradeMessage.OriginalOrder> ordList = new ArrayList<>();
        ordList.add(new TradeMessage.OriginalOrder(symbol, side, 140.1, 1000_000,  "orderID", "clientOrdID1", "clientEntityID1"));
        ordList.add(new TradeMessage.OriginalOrder(symbol, side, 150.1, 1500_000,  "orderID", "clientOrdID2", "clientEntityID2"));
        ordList.add(new TradeMessage.OriginalOrder(symbol, side, 160.1, 2000_000,  "orderID", "clientOrdID3", "clientEntityID3"));
        ordList.add(new TradeMessage.OriginalOrder(symbol, side, 160.1, 3000_000,  "orderID", "clientOrdID4", "clientEntityID4"));
        ordList.add(new TradeMessage.OriginalOrder(symbol, side, 170.1, 1500_000,  "orderID", "clientOrdID5", "clientEntityID5"));
        ordList.add(new TradeMessage.OriginalOrder(symbol, oSide, 180.1, 1000_000,  "orderID", "clientOrdID1", "clientEntityID7"));
        ordList.add(new TradeMessage.OriginalOrder(symbol, oSide, 180.2, 1500_000,  "orderID", "clientOrdID2", "clientEntityID8"));
        ordList.add(new TradeMessage.OriginalOrder(symbol, oSide, 190.3, 2000_000,  "orderID", "clientOrdID3", "clientEntityID9"));
        ordList.add(new TradeMessage.OriginalOrder(symbol, oSide, 190.4, 3000_000,  "orderID", "clientOrdID4", "clientEntityID10"));
        ordList.add(new TradeMessage.OriginalOrder(symbol, oSide, 190.5, 1500_000,  "orderID", "clientOrdID5", "clientEntityID11"));
        for(TradeMessage.OriginalOrder o : ordList){
            engine.addOrder(o);
        }

        List<TradeMessage.OriginalOrder> hdkOrdList = new ArrayList<>();
        hdkOrdList.add(new TradeMessage.OriginalOrder("USDHKD", side, 11, 3000_000,  "orderID", "clientOrdID4", "clientEntityID10"));
        hdkOrdList.add(new TradeMessage.OriginalOrder("USDHKD", oSide, 12, 1500_000,  "orderID", "clientOrdID5", "clientEntityID11"));
        for(TradeMessage.OriginalOrder o : hdkOrdList){
            _matchingEngine_USDHKD.addOrder(o);
        }
        return "";
    }
    @RequestMapping("/query_exec_reports")
    public String requestExecReport(@RequestParam(value = "order_id", defaultValue = "") String orderID){

        List<Map<String, String>> ers = executionReportsByOrderID.get(orderID);
        Map<String, Object> ersMap = new HashMap<>();
        ersMap.put("order_id", orderID);
        ersMap.put("execution_reports", ers);

        Gson gson = new GsonBuilder().create();
        String jsonString = gson.toJson(ersMap);
        return jsonString;
    }

    @RequestMapping("/query_order_book")
    public String queryOrderBook(@RequestParam(value = "symbol", defaultValue = "USDJPY") String symbol){

        log.info("query_order_book on symbol:{}", symbol);

        Map<String, Object> orderBook = new HashMap<>();
        orderBook.put("symbol", symbol);
        orderBook.put("order_book", orderbookBySymbol.get(symbol));

        Gson gson = new GsonBuilder().create();
        String jsonString = gson.toJson(orderBook);
        return jsonString;
    }

    class EngineExecConsumingThread extends Thread {

        private final BlockingQueue<MatchingEngine.MatchingEnginOutputMessageFlag> _engineOutputQueueForExecRpt;
        private volatile boolean _stopFlag = false;

        EngineExecConsumingThread(String threadName, BlockingQueue<MatchingEngine.MatchingEnginOutputMessageFlag> engineOutputQueueForExecRpt) {
            super(threadName);
            _engineOutputQueueForExecRpt = engineOutputQueueForExecRpt;
        }

        @Override
        public void run() {
            while (!Thread.currentThread().isInterrupted() && !_stopFlag) {
                try {
                    MatchingEngine.MatchingEnginOutputMessageFlag matchER_or_singleSideER = _engineOutputQueueForExecRpt
                            .poll(5, TimeUnit.SECONDS);
                    if (matchER_or_singleSideER == null) {
                        continue;
                    }


                    if(matchER_or_singleSideER instanceof TradeMessage.MatchedExecutionReport){
                        TradeMessage.MatchedExecutionReport matchedExecutionReport = (TradeMessage.MatchedExecutionReport)matchER_or_singleSideER;

                        //this is the only thread to modify the executionReportsByOrderID
                        if(!matchedExecutionReport._makerOriginOrder._clientEntityID.startsWith("ROBOT")) {
                            processOneSideOfMatchedER(matchedExecutionReport, MAKER_TAKER.MAKER);
                        }
                        if(!matchedExecutionReport._takerOriginOrder._clientEntityID.startsWith("ROBOT")) {
                            processOneSideOfMatchedER(matchedExecutionReport, MAKER_TAKER.TAKER);
                        }
                    }else if(matchER_or_singleSideER instanceof TradeMessage.SingleSideExecutionReport){

                        TradeMessage.SingleSideExecutionReport singleSideExecutionReport = (TradeMessage.SingleSideExecutionReport) matchER_or_singleSideER;
                        if(!singleSideExecutionReport._originOrder._clientEntityID.startsWith("ROBOT")) {
                            List<Map<String, String>> originalReports = executionReportsByOrderID.get(singleSideExecutionReport._originOrder._orderID);
                            List<Map<String, String>> originalReportsNew = new ArrayList<>();
                            if (originalReports != null) {
                                originalReportsNew.addAll(originalReports);
                            }
                            originalReportsNew.add(buildExternalERfromInternalER(singleSideExecutionReport));
                            executionReportsByOrderID.put(singleSideExecutionReport._originOrder._orderID, originalReportsNew);
                        }
                    }else{

                        log.error("unknown type: {}", matchER_or_singleSideER);
                    }

                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    log.info("matching thread " + Thread.currentThread().getName() + " is interruped", e);
                }
            }
            _stopFlag = true;
        }

        private void processOneSideOfMatchedER(TradeMessage.MatchedExecutionReport matchedExecutionReport, MAKER_TAKER maker_taker){
            long outputConsumingNanoTime = System.nanoTime();
            final TradeMessage.OriginalOrder originalOrder ;
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
            List<Map<String, String>> makerOriginalReport = executionReportsByOrderID.get(originalOrder._orderID);
            List<Map<String, String>> makerOriginalReportNew = new ArrayList<>(makerOriginalReport);
            makerOriginalReportNew.add(buildExternalERfromInternalER(matchedExecutionReport, maker_taker));
            //replace with a new List, rather than update the exists List, to avoid concurrent modification issue.WHY not use copy on write?
            executionReportsByOrderID.put(originalOrder._orderID, makerOriginalReportNew);

            log.info("performance:{},{},{},{},{}", new Object[]{
                    originalOrder._clientEntityID,
                    originalOrder._clientOrdID,
                    originalOrder._enteringEngineSysNanoTime,
                    matchedExecutionReport._matchingSysNanoTime,
                    outputConsumingNanoTime});
            }

        public void stopIt() {
            this._stopFlag = true;
        }
    }


    class EngineMDConsumingThread extends Thread {

        private final BlockingQueue<MatchingEngine.MatchingEnginOutputMessageFlag> _engineOutputQueueForAggBookAndBookDelta;
        private volatile boolean _stopFlag = false;
        private final String _symbol;

        EngineMDConsumingThread(String threadName, String symbol, BlockingQueue<MatchingEngine.MatchingEnginOutputMessageFlag> engineOutputQueueForAggBookAndBookDelta) {
            super(threadName);
            _symbol = symbol;
            _engineOutputQueueForAggBookAndBookDelta = engineOutputQueueForAggBookAndBookDelta;
        }

        @Override
        public void run() {
            while (!Thread.currentThread().isInterrupted() && !_stopFlag) {
                try {
                    MatchingEngine.MatchingEnginOutputMessageFlag aggBook_or_bookDelta = _engineOutputQueueForAggBookAndBookDelta
                            .poll(5, TimeUnit.SECONDS);
                    if (aggBook_or_bookDelta == null) {
                        continue;
                    }

                    if(aggBook_or_bookDelta instanceof MarketDataMessage.AggregatedOrderBook){
                        MarketDataMessage.AggregatedOrderBook aggOrdBook = (MarketDataMessage.AggregatedOrderBook)aggBook_or_bookDelta;
                        orderbookBySymbol.put(_symbol,aggOrdBook);

                    }else if(aggBook_or_bookDelta instanceof MarketDataMessage.OrderBookDelta){
                        MarketDataMessage.OrderBookDelta matchedExecutionReport = (MarketDataMessage.OrderBookDelta)aggBook_or_bookDelta;
                        //TODO i am going to write a standalone MarketEngine to build a FULL orderbook(full depth)
                        //IGNORE in this example. We will send agg book request periodically & internally.
                    }else{
                        log.error("unknown type: {}", aggBook_or_bookDelta);
                    }

                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    log.info("matching thread " + Thread.currentThread().getName() + " is interruped", e);
                }
            }

            _stopFlag = true;

        }

        public void stopIt() {
            this._stopFlag = true;
        }
    }

    class InternalTriggerOrderBookThread extends Thread {

        private volatile boolean _stopFlag = false;
        private List<MatchingEngine> _engines;

        InternalTriggerOrderBookThread(String threadName, List<MatchingEngine> engines) {
            super(threadName);
            _engines = engines;
        }

        @Override
        public void run() {

            while (!Thread.currentThread().isInterrupted() && !_stopFlag) {

                for(MatchingEngine engine: _engines){
                    engine.addAggOrdBookRequest(new MarketDataMessage.AggregatedOrderBookRequest(String.valueOf(System.nanoTime()), 5));
                }

                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
            _stopFlag = true;
            log.error(Thread.currentThread().getName()+" exit!");

        }

        public void stopIt() {
            this._stopFlag = true;
        }
    }

    enum MAKER_TAKER{
        MAKER, TAKER;
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

     public static void main(String[] args) {
        SpringApplication.run(MatchingEngineWebWrapper.class);
    }
}
