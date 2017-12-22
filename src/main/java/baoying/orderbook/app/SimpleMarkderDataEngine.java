package baoying.orderbook.app;

import baoying.orderbook.MatchingEngine;
import baoying.orderbook.MarketDataMessage;
import com.google.common.eventbus.Subscribe;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class SimpleMarkderDataEngine {

    private final static Logger log = LoggerFactory.getLogger(SimpleMarkderDataEngine.class);

    private Map<String, MarketDataMessage.AggregatedOrderBook> orderbookBySymbol;
    private InternalTriggerOrderBookThread _internalTriggerOrderBookThread;

    SimpleMarkderDataEngine(List<MatchingEngine> engineInputAcceptors){
        orderbookBySymbol = new ConcurrentHashMap<>();
        _internalTriggerOrderBookThread =  new InternalTriggerOrderBookThread("InternalTriggerOrderBookThread",engineInputAcceptors);
    }

    MarketDataMessage.AggregatedOrderBook getOrderBookBySymbol(String symbol){
        return orderbookBySymbol.get(symbol);
    }

    public void start(){
        _internalTriggerOrderBookThread.start();
    }

    @Subscribe
    public void process(MarketDataMessage.AggregatedOrderBook aggBook) {
        orderbookBySymbol.put(aggBook._symbol,aggBook);
    }

    @Subscribe
    public void process(MarketDataMessage.OrderBookDelta orderBookDelta) {
        //TODO i am going to write a standalone MarketEngine to build a FULL orderbook(full depth)
        //IGNORE in this example. We will send agg book request periodically & internally.
    }

    class InternalTriggerOrderBookThread extends Thread {

        private volatile boolean _stopFlag = false;
        private List<MatchingEngine> _engineInputAcceptors;

        InternalTriggerOrderBookThread(String threadName, List<MatchingEngine> engines) {
            super(threadName);
            _engineInputAcceptors = engines;
        }

        @Override
        public void run() {

            while (!Thread.currentThread().isInterrupted() && !_stopFlag) {

                for(MatchingEngine engineInputAcceptor: _engineInputAcceptors){
                    engineInputAcceptor.addAggOrdBookRequest(new MarketDataMessage.AggregatedOrderBookRequest(String.valueOf(System.nanoTime()), 5));
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
}