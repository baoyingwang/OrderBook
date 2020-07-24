package baoying.orderbook.marketdata;


import baoying.orderbook.core.MarketDataMessage;
import com.google.common.eventbus.Subscribe;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


import java.util.Map;
import java.util.concurrent.*;


public class SimpleMarkderDataEngine {

    private final static Logger log = LoggerFactory.getLogger(SimpleMarkderDataEngine.class);

    private Map<String, MarketDataMessage.AggregatedOrderBook> orderbookBySymbol;
    private Map<String, MarketDataMessage.DetailOrderBook> detailOrderbookBySymbol;


    public SimpleMarkderDataEngine(){

        orderbookBySymbol = new ConcurrentHashMap<>();
        detailOrderbookBySymbol = new ConcurrentHashMap<>();
    }

    public MarketDataMessage.AggregatedOrderBook getOrderBookBySymbol(String symbol){
        return orderbookBySymbol.get(symbol);
    }

    public MarketDataMessage.DetailOrderBook getDetailOrderBookBySymbol(String symbol){
        return detailOrderbookBySymbol.get(symbol);
    }

    public void start(){

    }

    @Subscribe
    public void process(MarketDataMessage.AggregatedOrderBook aggBook) {
        orderbookBySymbol.put(aggBook._symbol,aggBook);
    }

    @Subscribe
    public void process(MarketDataMessage.DetailOrderBook detailBook) {
        detailOrderbookBySymbol.put(detailBook._symbol,detailBook);
    }

    @Subscribe
    public void process(MarketDataMessage.OrderBookDelta orderBookDelta) {
        //TODO i am going to write a standalone MarketEngine to build a FULL orderbook(full depth)
        //IGNORE in this example. We will send agg book request periodically & internally.
    }
}