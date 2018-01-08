package baoying.orderbook;

import baoying.orderbook.MarketDataMessage.AggregatedOrderBookRequest;
import baoying.orderbook.TradeMessage.OriginalOrder;
import baoying.orderbook.app.Util;
import com.google.common.eventbus.AsyncEventBus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

public class MatchingEngine {

    public static String LATENCY_ENTITY_PREFIX = "LxTxCx";
    private final static Logger log = LoggerFactory.getLogger(MatchingEngine.class);

    public final List<String> _symbols;
	private final Map<String, OrderBook> _orderBooks;

	private final AsyncEventBus _outputMarketDataBus;
	private final AsyncEventBus _outputExecutionReportsBus;


	public MatchingEngine(List<String> symbols,
						  AsyncEventBus outputExecutionReportsBus,
						  AsyncEventBus outputMarketDataBus){

        _symbols = Collections.unmodifiableList(symbols);
        _orderBooks = new HashMap<>();
        for(String symbol: symbols){
            _orderBooks.put(symbol, new OrderBook(symbol));
        }

		_outputExecutionReportsBus = outputExecutionReportsBus;
		_outputMarketDataBus = outputMarketDataBus;

	}

	// check matching result(ExecutionReport) from _processResult
	public List<OrderBook.MatchingEnginOutputMessageFlag> addOrder(OriginalOrder order) {

		OrderBook orderBook = orderBook(order._symbol);

		OrderBook.ExecutingOrder executingOrder = new OrderBook.ExecutingOrder(order);

		Util.Tuple<List<OrderBook.MatchingEnginOutputMessageFlag>, List<MarketDataMessage.OrderBookDelta>> matchResult
				= orderBook.processInputOrder(executingOrder);

		matchResult._1.forEach( execRpt ->{
			_outputExecutionReportsBus.post(execRpt);
		} );

		matchResult._2.forEach( ordBookDelta ->{
			_outputMarketDataBus.post(ordBookDelta);
		} );

		return matchResult._1;
    }

    public void addAggOrdBookRequest(AggregatedOrderBookRequest aggOrdBookRequest) {

		OrderBook orderBook = orderBook(aggOrdBookRequest._symbol);
		MarketDataMessage.AggregatedOrderBook aggOrderBook = orderBook.buildAggregatedOrderBook(aggOrdBookRequest._depth);
		_outputMarketDataBus.post(aggOrderBook);
    }



	private OrderBook orderBook(String symbol){

	     OrderBook ob = _orderBooks.get(symbol);

	     if(ob == null){
	         throw new RuntimeException("cannot identify related order book from symbol:"+symbol);
         }
         return  ob;
    }


}
