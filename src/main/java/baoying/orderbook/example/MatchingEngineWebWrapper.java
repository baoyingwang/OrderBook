package baoying.orderbook.example;

import baoying.orderbook.*;
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
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.nio.file.StandardOpenOption.APPEND;
import static java.nio.file.StandardOpenOption.CREATE;

//https://www.java2blog.com/spring-boot-web-application-example/
@Configuration
@ComponentScan
@EnableAutoConfiguration
@SpringBootApplication
@RestController
@RequestMapping("/matching")
public class MatchingEngineWebWrapper {



    private final static Logger log = LoggerFactory.getLogger(MatchingEngineWebWrapper.class);

    private final MatchingEngine _matchingEngine_USDJPY;
    private final MatchingEngine _matchingEngine_USDHKD;

    private final DisruptorInputAcceptor _disruptorInputAcceptor_USDJPY;
    private final DisruptorInputAcceptor _disruptorInputAcceptor_USDHKD;

    private final EngineExecConsumingThread _execUSDJPYThread;
    private final EngineMDConsumingThread _mdUSDJPYThread;
    private final EngineExecConsumingThread _execUSDHKDThread;
    private final EngineMDConsumingThread _mdUSDHKDThread;

    private InternalTriggerOrderBookThread _internalTriggerOrderBookThread;
    //value: list of execution report(as Map<String, String)
    private Map<String, List<Map<String, String>>> executionReportsByOrderID;
    private Map<String, MarketDataMessage.AggregatedOrderBook> orderbookBySymbol;

    MatchingEngineWebWrapper(){
        orderbookBySymbol = new ConcurrentHashMap<>();
        executionReportsByOrderID = new ConcurrentHashMap<>();

        _matchingEngine_USDJPY = new MatchingEngine("USDJPY");
        _matchingEngine_USDHKD = new MatchingEngine("USDHKD");
        _disruptorInputAcceptor_USDJPY = new DisruptorInputAcceptor(_matchingEngine_USDJPY);
        _disruptorInputAcceptor_USDHKD = new DisruptorInputAcceptor(_matchingEngine_USDHKD);

        _execUSDJPYThread = new EngineExecConsumingThread("EngineExecConsumingThread-USDJPY", _matchingEngine_USDJPY._outputQueueForExecRpt );
        _mdUSDJPYThread = new EngineMDConsumingThread("EngineMDConsumingThread-USDJPY", "USDJPY",_matchingEngine_USDJPY._outputQueueForAggBookAndBookDelta );

        _execUSDHKDThread = new EngineExecConsumingThread("EngineExecConsumingThread-HKD", _matchingEngine_USDHKD._outputQueueForExecRpt );
        _mdUSDHKDThread = new EngineMDConsumingThread("EngineMDConsumingThread-HKD", "USDHKD",_matchingEngine_USDHKD._outputQueueForAggBookAndBookDelta );

        List<DisruptorInputAcceptor> engines = new ArrayList<>();
        engines.add(_disruptorInputAcceptor_USDJPY);
        engines.add(_disruptorInputAcceptor_USDHKD);
        _internalTriggerOrderBookThread =  new InternalTriggerOrderBookThread("InternalTriggerOrderBookThread",engines);

    }

    private AtomicLong _placedOrderCounter = new AtomicLong(0);
    private TradeMessage.OriginalOrder _firstOriginalOrderSinceTest = null;
    private TradeMessage.OriginalOrder _lastOriginalOrderSinceTest = null;
    private BlockingQueue<long[]> testTimeDataQueue = new LinkedBlockingQueue<long[]>();



    @PostConstruct
    public void start(){

        log.info("start the MatchingEngineWebWrapper");

        _disruptorInputAcceptor_USDJPY.start();
        _disruptorInputAcceptor_USDHKD.start();

        _execUSDJPYThread.start();
        _mdUSDJPYThread.start();
        _execUSDHKDThread.start();
        _mdUSDHKDThread.start();
        _internalTriggerOrderBookThread.start();
    }

    private DisruptorInputAcceptor getMatchingEngine(String symbol){

        if("USDJPY".equals(symbol)){
            return _disruptorInputAcceptor_USDJPY;
        }else if("USDHKD".equals(symbol)){
            return _disruptorInputAcceptor_USDHKD;
        } else{
            String errorInfo = "ERROR - Only USDJPY and USDHKD is supported. Unknown symbol:"+ symbol;
            log.error(errorInfo);
            throw new RuntimeException(errorInfo);
        }
    }

    @RequestMapping("/place_order")
    public String placeOrder(@RequestParam(value = "symboL", defaultValue = "USDJPY") String symbol,
                             @RequestParam(value = "client_entity", defaultValue = "BankA") String clientEntity,
                             @RequestParam(value = "side", defaultValue="Bid") String side,
                             @RequestParam(value = "price", defaultValue="126.0") double price,
                             @RequestParam(value = "qty", defaultValue = "5000") int qty){

        String clientOrdID = clientEntity+"_"+System.nanoTime();
        String orderID = symbol+"_"+clientEntity+"_"+System.nanoTime();

        log.info("received order request, symbol:{}, client ordID:{}, ordID:{}", new Object[]{symbol,clientOrdID,orderID });
        final CommonMessage.Side orderSide;
        if("Bid".equalsIgnoreCase(side)){
            orderSide = CommonMessage.Side.BID;
        }else if("Offer".equalsIgnoreCase(side)){
            orderSide = CommonMessage.Side.OFFER;
        }else{
            log.error("unknown side : " + side);
            return "ERROR - unknown side : " + side;
        }


        TradeMessage.OriginalOrder originalOrder  = new TradeMessage.OriginalOrder( System.currentTimeMillis(),symbol,orderSide , CommonMessage.OrderType.LIMIT, price, qty, orderID, clientOrdID, clientEntity);
        if(clientEntity.startsWith(MatchingEngine.LATENCY_ENTITY_PREFIX)){
            originalOrder._recvFromClient_sysNano_test = System.nanoTime();
        }
        DisruptorInputAcceptor engine = getMatchingEngine(originalOrder._symbol);
        if(engine != null) {
            TradeMessage.SingleSideExecutionReport erNew = engine.addOrder(originalOrder);

            long nthOrderSinceTest = _placedOrderCounter.incrementAndGet();
            if(nthOrderSinceTest == 1) { _firstOriginalOrderSinceTest = originalOrder; }
            else{_lastOriginalOrderSinceTest = originalOrder;}

            Gson gson = new GsonBuilder().create();
            String jsonString = gson.toJson(erNew);
            return jsonString;
        }else{
            return "ERROR - not supported symbol:" + originalOrder._symbol;
        }
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

    @RequestMapping("/reset_test_data")
    public void resetBeforeTest(){
        _placedOrderCounter.set(0);
        _firstOriginalOrderSinceTest = null;
        _lastOriginalOrderSinceTest = null;
        testTimeDataQueue.clear();
    }

    DateTimeFormatter finalNameFormatter =
            DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss.SSS'Z'").withZone( ZoneId.of("UTC") );
    @RequestMapping("/get_test_summary")
    public String endTest(){

        String jsonString ="";
        try {
            log.info("get_test_summary enter");
            Map<String, Object> data = new HashMap<>();

            long allOrderCount = _placedOrderCounter.get();
            data.put("order_count", allOrderCount);


            if (_firstOriginalOrderSinceTest == null || allOrderCount == 0) {
                log.error("get_test_summary - ERROR - no order during the test");
                return "ERROR - no order during the test";
            }
            long startTimeInEpochMS = _firstOriginalOrderSinceTest._recvFromClientEpochMS;
            Instant instantStart = Instant.ofEpochMilli(startTimeInEpochMS);
            data.put("start_time", instantStart.toString());
            log.info("get_test_summary - start_time");

            final Instant instantEnd;
            final long endTimeInEpochMS;
            {
                if (_lastOriginalOrderSinceTest == null) {
                    return "ERROR - get_test_summary - no summary since only single order for now";
                }
                endTimeInEpochMS = _lastOriginalOrderSinceTest._recvFromClientEpochMS;
                instantEnd = Instant.ofEpochMilli(endTimeInEpochMS);
            }
            data.put("end_time", instantEnd.toString());
            log.info("get_test_summary - end_time");

            long durationInSecond = (endTimeInEpochMS - startTimeInEpochMS) / 1000; //TODO use Instant diff
            if (durationInSecond < 1) {
                log.error("get_test_summary - ERROR - not proceed calculation for test within 1 second");
                return "ERROR - not proceed calculation for test within 1 second";
            }
            data.put("duration_in_second", durationInSecond);
            log.info("get_test_summary - duration_in_second");

            final double ratePerSecond = allOrderCount * 1.0 / durationInSecond;
            data.put("rate_per_second", String.format("%.2f", ratePerSecond));

            final List<long[]> deltaLatencyData = new ArrayList<>();
            testTimeDataQueue.drainTo(deltaLatencyData);
            //data.put("latency_data", deltaLatencyData);
            //data.put("un-purged_latency_data_count", deltaLatencyData.size()); //latencyData maybe has been purged to file periodically
            log.info("get_test_summary - latency_data");

            //https://stackoverflow.com/questions/30307382/how-to-append-text-to-file-in-java-8-using-specified-charset
            Path outputAppendingLatencyDataFile = Paths.get("log/LatencyData_OverallStart_" + finalNameFormatter.format(instantStart) + ".csv");
            //https://stackoverflow.com/questions/19676750/using-the-features-in-java-8-what-is-the-most-concise-way-of-transforming-all-t
            List<String> latencyDataCSVLines = deltaLatencyData.stream()
                    .map(it -> Instant.ofEpochMilli(it[0]) + "," + Util.toCsvString(it, 1, it.length)).collect(Collectors.toList());
            if (!Files.exists(outputAppendingLatencyDataFile)) {
                Files.createFile(outputAppendingLatencyDataFile); //need create in advance because the following check line count requires it.
                Files.write(outputAppendingLatencyDataFile, ("recvTime,put2InputQ,pickFromInputQ,match,pickFromOutputQ" + "\n").getBytes(), APPEND, CREATE);
            }
            Files.write(outputAppendingLatencyDataFile, latencyDataCSVLines, UTF_8, APPEND, CREATE);
            log.info("get_test_summary - outputAppendingLatencyDataFile");

            final long latency_data_count_all = java.nio.file.Files.lines(outputAppendingLatencyDataFile).count(); //http://www.adam-bien.com/roller/abien/entry/counting_lines_with_java_8
            data.put("latency_data_count", latency_data_count_all);

            List<String[]> tailResponseLatencyData = new ArrayList<>();
            AtomicLong latencyDataCounter = new AtomicLong(0);
            long maxNumberOfResponseLatencyData = 400;
            long startIndexOfReponseLatencyData = latency_data_count_all>400? latency_data_count_all-400 : 0;
            //TODO performance improvement - read twice(here) above get latency_data_count_all
            java.nio.file.Files.lines(outputAppendingLatencyDataFile).forEach(line ->{

                latencyDataCounter.incrementAndGet();
                if(latencyDataCounter.get() >= startIndexOfReponseLatencyData){
                    tailResponseLatencyData.add(line.split(","));
                }

            });
            data.put("latency_data", tailResponseLatencyData);

            //the last line is the latest information
            Path outputAppendingLatencySummaryFile = Paths.get("log/LatencySummary_OverallStart_" + finalNameFormatter.format(instantStart) + ".json.txt");
            Gson gson = new GsonBuilder().create();
            jsonString = gson.toJson(data);
            Files.write(outputAppendingLatencySummaryFile, (jsonString + "\n").getBytes(), APPEND, CREATE);


            log.info("get_test_summary json generated");

            //bad performance to process the same lines many times
            GCLogUtil.main(new String[]{"log/GC.txt", "log/GC.summary.csv"});

            log.info("get_test_summary end");
        }catch(Exception e){
            log.error("exception during get_test_summary", e);
        }

        return jsonString;
    }

    static enum MAKER_TAKER{
        MAKER, TAKER;
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
                        if(matchedExecutionReport._makerOriginOrder._clientEntityID.startsWith(MatchingEngine.LATENCY_ENTITY_PREFIX)) {
                            addERStore(matchedExecutionReport, MAKER_TAKER.MAKER, matchedExecutionReport._makerOriginOrder);
                        }
                        if(matchedExecutionReport._takerOriginOrder._clientEntityID.startsWith(MatchingEngine.LATENCY_ENTITY_PREFIX)) {
                            long outputConsumingNanoTime = System.nanoTime();
                            addERStore(matchedExecutionReport, MAKER_TAKER.TAKER, matchedExecutionReport._takerOriginOrder);

                            testTimeDataQueue.add(new long[]{
                                    matchedExecutionReport._takerOriginOrder._recvFromClientEpochMS,
                                    matchedExecutionReport._taker_enterInputQ_sysNano_test
                                            - matchedExecutionReport._takerOriginOrder._recvFromClient_sysNano_test,

                                    matchedExecutionReport._taker_pickFromInputQ_sysNano_test
                                            - matchedExecutionReport._taker_enterInputQ_sysNano_test,

                                    matchedExecutionReport._matching_sysNano_test
                                            - matchedExecutionReport._taker_pickFromInputQ_sysNano_test,

                                    outputConsumingNanoTime - matchedExecutionReport._matching_sysNano_test});
                        }
                    }else if(matchER_or_singleSideER instanceof TradeMessage.SingleSideExecutionReport){

                        TradeMessage.SingleSideExecutionReport singleSideExecutionReport = (TradeMessage.SingleSideExecutionReport) matchER_or_singleSideER;
                        if(singleSideExecutionReport._originOrder._clientEntityID.startsWith(MatchingEngine.LATENCY_ENTITY_PREFIX)) {
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
                    log.info("matching thread " + Thread.currentThread().getName() + " is interrupted", e);
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
        private List<DisruptorInputAcceptor> _engines;

        InternalTriggerOrderBookThread(String threadName, List<DisruptorInputAcceptor> engines) {
            super(threadName);
            _engines = engines;
        }

        @Override
        public void run() {

            while (!Thread.currentThread().isInterrupted() && !_stopFlag) {

                for(DisruptorInputAcceptor engine: _engines){
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

    public static void main(String[] args) {
         SpringApplication.run(MatchingEngineWebWrapper.class);
    }
}
