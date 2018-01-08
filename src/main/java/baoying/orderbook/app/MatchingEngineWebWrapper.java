package baoying.orderbook.app;

import baoying.orderbook.*;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.PostConstruct;
import java.time.Instant;
import java.util.*;

//https://www.java2blog.com/spring-boot-web-application-example/
@RestController
@RequestMapping("/matching")
public class MatchingEngineWebWrapper {

    private final static Logger log = LoggerFactory.getLogger(MatchingEngineWebWrapper.class);

    private final MatchingEngine _engine;
    private final SimpleOMSEngine _simpleOMSEngine;
    private final SimpleMarkderDataEngine _simpleMarkderDataEngine ;


    MatchingEngineWebWrapper(MatchingEngine engine,
                             SimpleOMSEngine simpleOMSEngine,
                             SimpleMarkderDataEngine simpleMarkderDataEngine){

        _simpleOMSEngine=simpleOMSEngine;
        _simpleMarkderDataEngine=simpleMarkderDataEngine;
        _engine = engine;

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
/*
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

        TradeMessage.SingleSideExecutionReport erNew = _engine.addOrder(originalOrder);
        _simpleOMSEngine.perfTestDataForWeb.recordNewOrder(originalOrder);

        Gson gson = new GsonBuilder().create();
        String jsonString = gson.toJson(erNew);
        return jsonString;
*/
        return "";

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
        _simpleOMSEngine.perfTestDataForWeb.resetBeforeTest();
        log.info("===============reset_test_data===============");
        return "reset done";
    }

    @RequestMapping("/get_test_summary")
    public String endTest(){

        String jsonString ="";
        try {
            log.info("get_test_summary enter");
            Map<String, Object> data = new HashMap<>();

            long allOrderCount = _simpleOMSEngine.perfTestDataForWeb.count();
            data.put("order_count", allOrderCount);

            if (allOrderCount < 2) {
                log.error("get_test_summary - ERROR - no order during the test");
                return "ERROR - not calculate summary if order count less than 2, now:" + allOrderCount;
            }

            long startTimeInEpochMS = _simpleOMSEngine.perfTestDataForWeb.startInEpochMS();
            Instant instantStart = Instant.ofEpochMilli(startTimeInEpochMS);
            data.put("start_time", instantStart.toString());
            log.info("get_test_summary - start_time");

            final Instant instantEnd;
            final long endTimeInEpochMS;
            {
                endTimeInEpochMS = _simpleOMSEngine.perfTestDataForWeb.lastInEpochMS();
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

            final long latency_data_count_all = _simpleOMSEngine.perfTestDataForWeb.latencyOrdCount();
            data.put("latency_data_count", latency_data_count_all);
            data.put("latency_data_rate_per_second", String.format("%.2f",latency_data_count_all*1.0/durationInSecond));

            List<long[]> tailResponseLatencyData = _simpleOMSEngine.perfTestDataForWeb.getListCopy();
            data.put("latency_data", tailResponseLatencyData);

            Gson gson = new GsonBuilder().create();
            jsonString = gson.toJson(data);

            log.info("get_test_summary end");
        }catch(Exception e){
            log.error("exception during get_test_summary", e);
        }

        return jsonString;
    }

}
