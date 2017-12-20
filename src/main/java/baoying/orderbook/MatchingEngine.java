package baoying.orderbook;

import baoying.orderbook.MarketDataMessage.AggregatedOrderBookRequest;
import baoying.orderbook.TradeMessage.OriginalOrder;
import baoying.orderbook.TradeMessage.SingleSideExecutionReport;
import com.google.common.eventbus.AsyncEventBus;
import com.lmax.disruptor.BusySpinWaitStrategy;
import com.lmax.disruptor.RingBuffer;
import com.lmax.disruptor.WaitStrategy;
import com.lmax.disruptor.dsl.Disruptor;
import com.lmax.disruptor.dsl.ProducerType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;

public class MatchingEngine {

    public static String LATENCY_ENTITY_PREFIX = "LTC$$";
    private final static Logger log = LoggerFactory.getLogger(MatchingEngine.class);

	private final OrderBook _orderBook;

	//Assumption: our message generation speed will NOT be faster than 1000,000,000/second.
	private final long _msgIDBase = System.nanoTime();
	private final AtomicLong _msgIDIncreament = new  AtomicLong(0);

	private final Disruptor<MatchingEngineInputMessageEvent> _inputMessageDisruptor;
	private final RingBuffer<MatchingEngineInputMessageEvent> _inputMessageRingBuffer;

	private final AsyncEventBus _outputMarketDataBus;
	private final AsyncEventBus _outputExecutionReportsBus;


	public MatchingEngine(OrderBook orderBook, AsyncEventBus outputExecutionReportsBus, AsyncEventBus outputMarketDataBus) {
		_orderBook = orderBook;

		_outputExecutionReportsBus = outputExecutionReportsBus;
		_outputMarketDataBus = outputMarketDataBus;

		Executor executor = Executors.newSingleThreadExecutor();
		int bufferSize = 1024;
		WaitStrategy waitStrategy = new BusySpinWaitStrategy();
		_inputMessageDisruptor = new Disruptor<>(MatchingEngineInputMessageEvent::new, bufferSize, executor, ProducerType.MULTI,waitStrategy);
		_inputMessageRingBuffer = _inputMessageDisruptor.getRingBuffer();
		//_inputMessageDisruptor.handleEventsWith(this::handleEvent);
		_inputMessageDisruptor.handleEventsWith(
				(MatchingEngineInputMessageEvent event, long sequence, boolean endOfBatch) ->{
					OrderBook.MatchingEngineInputMessageFlag originalOrderORAggBookRequest = event._value;
					processInputMessage(originalOrderORAggBookRequest);
				});
	}

	public void start(){
		_inputMessageDisruptor.start();
	}

	// check matching result(ExecutionReport) from _processResult
	public SingleSideExecutionReport addOrder(OriginalOrder order) {

		if (!_orderBook._symbol.equals(order._symbol)) {	// it should never reach here
			throw new RuntimeException("not the expected symbol, expect:"+ _orderBook._symbol+", by it is:"+order._symbol+", client_entity:"+order._clientEntityID+" client ord id:"+order._clientOrdID);
		}

		if(order._clientEntityID.startsWith(LATENCY_ENTITY_PREFIX)){
			order._enterInputQ_sysNano_test = System.nanoTime();
			order._isLatencyTestOrder=true;
		}

		boolean published = _inputMessageRingBuffer.tryPublishEvent((event, sequence, buffer) -> event.set(order));
		if(published) {
			return new SingleSideExecutionReport(_msgIDBase + _msgIDIncreament.incrementAndGet(),
					System.currentTimeMillis(),
					order,
					TradeMessage.ExecutionType.NEW,
					order._qty,
					"Entered Order Book");
		}else{
			//TODO re-try 3 times before rejection
			return new SingleSideExecutionReport(_msgIDBase + _msgIDIncreament.incrementAndGet(),
					System.currentTimeMillis(),
					order,
					TradeMessage.ExecutionType.REJECTED,
					order._qty,
					"Fail to enter Order Book");
		}
    }

    public void addAggOrdBookRequest(AggregatedOrderBookRequest aggregatedOrderBookRequest) {

		_inputMessageRingBuffer.publishEvent((event, sequence, buffer) -> event.set(aggregatedOrderBookRequest));
    }

	void processInputMessage(OrderBook.MatchingEngineInputMessageFlag originalOrderORAggBookRequest)
	{
		if (originalOrderORAggBookRequest instanceof OriginalOrder) {

			OriginalOrder order = (OriginalOrder) originalOrderORAggBookRequest;
			OrderBook.ExecutingOrder executingOrder = new OrderBook.ExecutingOrder(order);
			if(order._isLatencyTestOrder){
				executingOrder._pickFromInputQ_sysNano_test = System.nanoTime();
			}

			OrderBook.Tuple<List<OrderBook.MatchingEnginOutputMessageFlag>, List<MarketDataMessage.OrderBookDelta>> matchResult
					= _orderBook.processInputOrder(executingOrder);

			matchResult._1.forEach( execRpt ->{
				_outputExecutionReportsBus.post(execRpt);
			} );

			matchResult._2.forEach( ordBookDelta ->{
				_outputMarketDataBus.post(ordBookDelta);
			} );

		} else if (originalOrderORAggBookRequest instanceof AggregatedOrderBookRequest) {
			AggregatedOrderBookRequest aggOrdBookRequest = (AggregatedOrderBookRequest) originalOrderORAggBookRequest;
			MarketDataMessage.AggregatedOrderBook aggOrderBook = _orderBook.buildAggregatedOrderBook(aggOrdBookRequest._depth);
			_outputMarketDataBus.post(aggOrderBook);
		} else {
			log.error("received unknown type : {}",
					originalOrderORAggBookRequest.getClass().toGenericString());
		}
	}


	private static class MatchingEngineInputMessageEvent
	{
		private OrderBook.MatchingEngineInputMessageFlag _value;

		public void set(OrderBook.MatchingEngineInputMessageFlag value)
		{
			_value = value;
		}
	}

}
