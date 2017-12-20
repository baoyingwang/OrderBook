package baoying.orderbook.example;

import baoying.orderbook.*;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.nio.file.StandardOpenOption.APPEND;
import static java.nio.file.StandardOpenOption.CREATE;

//https://www.java2blog.com/spring-boot-web-application-example/
@RestController
@RequestMapping("/matching")
public class MatchingEngineWebWrapper {

    private final static Logger log = LoggerFactory.getLogger(MatchingEngineWebWrapper.class);

    private final Map<String,MatchingEngine> _enginesBySimbol;
    private final SimpleOMSEngine _simpleOMSEngine;
    private final SimpleMarkderDataEngine _simpleMarkderDataEngine ;


    MatchingEngineWebWrapper(Map<String,MatchingEngine> engines,
                             SimpleOMSEngine simpleOMSEngine,
                             SimpleMarkderDataEngine simpleMarkderDataEngine){

        _simpleOMSEngine=simpleOMSEngine;
        _simpleMarkderDataEngine=simpleMarkderDataEngine;
        _enginesBySimbol = engines;

    }

    @PostConstruct
    public void start(){
        log.info("start the MatchingEngineWebWrapper");
    }

    @RequestMapping("/place_order")
    public String placeOrder(@RequestParam(value = "symboL", defaultValue = "USDJPY") String symbol,
                             @RequestParam(value = "client_entity", defaultValue = "BankA") String clientEntity,
                             @RequestParam(value = "side", defaultValue="Bid") String side,
                             @RequestParam(value = "price", defaultValue="126.0") double price,
                             @RequestParam(value = "qty", defaultValue = "5000") int qty){

        final String clientOrdID = clientEntity+"_"+ UniqIDGenerator.next();
        final String orderID = symbol+"_"+clientEntity+"_"+ UniqIDGenerator.next();

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

        MatchingEngine engine = _enginesBySimbol.get(originalOrder._symbol);
        if(engine == null){
            log.error("cannot identify the engine from symbol:{}", originalOrder._symbol);
            return "ERROR - wrong symbol - MORE DETAIL TO BE PROVIDED";
        }

        TradeMessage.SingleSideExecutionReport erNew = engine.addOrder(originalOrder);
        _simpleOMSEngine._perfTestData.recordNewOrder(originalOrder);

        Gson gson = new GsonBuilder().create();
        String jsonString = gson.toJson(erNew);
        return jsonString;

    }

    @RequestMapping("/query_exec_reports")
    public String requestExecReport(@RequestParam(value = "order_id", defaultValue = "") String orderID){

        List<Map<String, String>> ers = _simpleOMSEngine.getERsByOrderID(orderID);
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
        orderBook.put("order_book", _simpleMarkderDataEngine.getOrderBookBySymbol(symbol));

        Gson gson = new GsonBuilder().create();
        String jsonString = gson.toJson(orderBook);
        return jsonString;
    }

    @RequestMapping("/reset_test_data")
    public String resetBeforeTest(){
        _simpleOMSEngine._perfTestData.resetBeforeTest();
        log.info("===============reset_test_data===============");
        return "reset done";
    }

    DateTimeFormatter finalNameFormatter =
            DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss.SSS'Z'").withZone( ZoneId.of("UTC") );
    @RequestMapping("/get_test_summary")
    public String endTest(){

        String jsonString ="";
        try {
            log.info("get_test_summary enter");
            Map<String, Object> data = new HashMap<>();

            long allOrderCount = _simpleOMSEngine._perfTestData.count();
            data.put("order_count", allOrderCount);

            if (allOrderCount < 2) {
                log.error("get_test_summary - ERROR - no order during the test");
                return "ERROR - not calculate summary if order count less than 2, now:" + allOrderCount;
            }

            long startTimeInEpochMS = _simpleOMSEngine._perfTestData.startInEpochMS();
            Instant instantStart = Instant.ofEpochMilli(startTimeInEpochMS);
            data.put("start_time", instantStart.toString());
            log.info("get_test_summary - start_time");

            final Instant instantEnd;
            final long endTimeInEpochMS;
            {
                endTimeInEpochMS = _simpleOMSEngine._perfTestData.lastInEpochMS();
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

            final List<long[]> deltaLatencyData = _simpleOMSEngine.drainTestTimeDataQueue();
            data.put("drained_latency_data", deltaLatencyData.size());

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
            data.put("latency_data_count", latency_data_count_all-1);

            List<String[]> tailResponseLatencyData = Util.loadTailCsvLines(outputAppendingLatencyDataFile, 100, latency_data_count_all);
            data.put("latency_data", tailResponseLatencyData);

            try {
                //bad performance to process the same lines many times
                GCLogUtil.main(new String[]{"log/GC.txt", "log/GC.summary.csv"});
                List<String[]> tailGCTookData = Util.loadTailCsvLines(Paths.get("log/GC.summary.csv"), 100);
                data.put("gc_took", tailGCTookData);
            }catch (Exception e2){
                log.error("",e2);
            }
            //the last line is the latest information
            Path outputAppendingLatencySummaryFile = Paths.get("log/LatencySummary_OverallStart_" + finalNameFormatter.format(instantStart) + ".json.txt");
            Gson gson = new GsonBuilder().create();
            jsonString = gson.toJson(data);
            Files.write(outputAppendingLatencySummaryFile, (jsonString + "\n").getBytes(), APPEND, CREATE);


            log.info("get_test_summary json generated");



            log.info("get_test_summary end");
        }catch(Exception e){
            log.error("exception during get_test_summary", e);
        }

        return jsonString;
    }

}
