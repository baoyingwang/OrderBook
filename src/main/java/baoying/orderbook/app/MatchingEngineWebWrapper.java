package baoying.orderbook.app;

import baoying.orderbook.*;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import io.vertx.core.Context;
import io.vertx.core.Vertx;
import io.vertx.core.eventbus.EventBus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
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

    //private final Vertx _vertx;
    private final Context _vertxContext;
    private final SimpleOMSEngine _simpleOMSEngine;
    private final SimpleMarkderDataEngine _simpleMarkderDataEngine ;


    MatchingEngineWebWrapper(MatchingEngine engine,
                             Context vertxContext,
                             SimpleOMSEngine simpleOMSEngine,
                             SimpleMarkderDataEngine simpleMarkderDataEngine){

        _engine = engine;
        _vertxContext = vertxContext;

        _simpleOMSEngine=simpleOMSEngine;
        _simpleMarkderDataEngine=simpleMarkderDataEngine;

    }

    @PostConstruct
    public void start(){
        log.info("start the MatchingEngineWebWrapper");
    }

    @RequestMapping(value="/place_order", method = { RequestMethod.GET, RequestMethod.POST })
    public String placeOrder(@RequestParam(value = "symbol", defaultValue = "USDJPY") String symbol,
                             @RequestParam(value = "client_entity", defaultValue = "BankA") String clientEntity,
                             @RequestParam(value = "side", defaultValue="Bid") String side,
                             @RequestParam(value = "price", defaultValue="126.0") double price,
                             @RequestParam(value = "qty", defaultValue = "5000") int qty,
                             @RequestParam(value = "ordType", defaultValue = "2") String fixOrdType //1 : market, 2 : limit
        ){

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

        CommonMessage.OrderType ordType = CommonMessage.OrderType.fixValueOf(fixOrdType.charAt(0));

        TradeMessage.OriginalOrder originalOrder  = new TradeMessage.OriginalOrder( System.currentTimeMillis(),symbol,orderSide ,ordType , price, qty, orderID, clientOrdID, clientEntity);


        _vertxContext.runOnContext((v)->{

            final List<OrderBook.MEExecutionReportMessageFlag> matchResult = _engine.matchOrder(originalOrder);

        });

        //Gson gson = new GsonBuilder().create();
        //String jsonString = gson.toJson(erNew);
        return "place order done, orderID:" + orderID;


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

    @RequestMapping("/query_detail_order_book")
    public String queryDetailOrderBook(@RequestParam(value = "symbol", defaultValue = "USDJPY") String symbol){

        log.info("query_detail_order_book on symbol:{}", symbol);

        Map<String, Object> orderBook = new HashMap<>();
        orderBook.put("symbol", symbol);
        orderBook.put("detail_order_book", _simpleMarkderDataEngine.getDetailOrderBookBySymbol(symbol));

        Gson gson = new GsonBuilder().create();
        String jsonString = gson.toJson(orderBook);
        return jsonString;
    }

    @RequestMapping("/reset_test_data")
    public String resetBeforeTest(){
        _engine.statistics.reset();
        log.info("===============reset_test_data===============");
        return "reset done";
    }

    @RequestMapping("/get_test_summary")
    public String summary(){

        MatchingDataStatistics stat = _engine.statistics;
        //return _engine.statistics.summary();

        String jsonString ="";
        try {

            Map<String, String> overall = _engine.statistics.overallSummary();

            List<Map<String, String>> periods = new ArrayList<>();
            List<MatchingDataStatistics.CurrentPeriod> periodsList = _engine.statistics.dataList();
            for(MatchingDataStatistics.CurrentPeriod p: periodsList){
                periods.add(p.summaryAsHash());
            }

            Map<String, Object> result = new HashMap<>();
            result.put("overall", overall);
            result.put("periods", periods);

            Gson gson = new GsonBuilder().create();
            jsonString = gson.toJson(result);

        }catch(Exception e){
            log.error("exception during get_test_summary", e);
        }

        return jsonString;
    }

}
