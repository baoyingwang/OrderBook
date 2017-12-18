package baoying.orderbook;

import baoying.orderbook.MarketDataMessage.AggregatedOrderBookRequest;
import baoying.orderbook.TradeMessage.OriginalOrder;
import baoying.orderbook.TradeMessage.SingleSideExecutionReport;
import com.lmax.disruptor.BusySpinWaitStrategy;
import com.lmax.disruptor.RingBuffer;
import com.lmax.disruptor.WaitStrategy;
import com.lmax.disruptor.dsl.Disruptor;
import com.lmax.disruptor.dsl.ProducerType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;

public class DisruptorInputAcceptor {

	private final MatchingEngine _matchingEngine;

	private final static Logger log = LoggerFactory.getLogger(DisruptorInputAcceptor.class);

	//Assumption: our message generation speed will NOT be faster than 1000,000,000/second.
	private final long _msgIDBase = System.nanoTime();
	private final AtomicLong _msgIDIncreament = new  AtomicLong(0);

	Disruptor<MatchingEngineInputMessageEvent> _inputMessageDisruptor;
	RingBuffer<MatchingEngineInputMessageEvent> _inputMessageRingBuffer;

	public DisruptorInputAcceptor(MatchingEngine matchingEngine) {
		_matchingEngine = matchingEngine;

		Executor executor = Executors.newSingleThreadExecutor();
		int bufferSize = 1024;
		WaitStrategy waitStrategy = new BusySpinWaitStrategy();
		_inputMessageDisruptor = new Disruptor<>(MatchingEngineInputMessageEvent::new, bufferSize, executor, ProducerType.MULTI,waitStrategy);
		_inputMessageRingBuffer = _inputMessageDisruptor.getRingBuffer();
		//_inputMessageDisruptor.handleEventsWith(this::handleEvent);
		_inputMessageDisruptor.handleEventsWith(
				(MatchingEngineInputMessageEvent event, long sequence, boolean endOfBatch) ->{
					MatchingEngine.MatchingEngineInputMessageFlag originalOrderORAggBookRequest = event._value;
					_matchingEngine.processInputMessage(originalOrderORAggBookRequest);
				});
	}

	public void start(){
		_inputMessageDisruptor.start();
	}

	// check matching result(ExecutionReport) from _processResult
	public SingleSideExecutionReport addOrder(OriginalOrder order) {

		if (!_matchingEngine._symbol.equals(order._symbol)) {	// it should never reach here
			throw new RuntimeException("not the expected symbol, expect:"+_matchingEngine._symbol+", by it is:"+order._symbol+", client_entity:"+order._clientEntityID+" client ord id:"+order._clientOrdID);
		}

		if(order._clientEntityID.startsWith(MatchingEngine.LATENCY_ENTITY_PREFIX)){
			order._enterInputQ_sysNano_test = System.nanoTime();
			order._isLatencyTestOrder=true;
		}

		boolean published = _inputMessageRingBuffer.tryPublishEvent((event, sequence, buffer) -> event.set(order));
		if(published) {
			return new SingleSideExecutionReport(_msgIDBase + _msgIDIncreament.incrementAndGet(),
					System.currentTimeMillis(),
					order,
					TradeMessage.SingleSideExecutionType.NEW,
					order._qty,
					"Entered Order Book");
		}else{
			//TODO re-try 3 times before rejection
			return new SingleSideExecutionReport(_msgIDBase + _msgIDIncreament.incrementAndGet(),
					System.currentTimeMillis(),
					order,
					TradeMessage.SingleSideExecutionType.REJECTED,
					order._qty,
					"Fail to enter Order Book");
		}
    }


    public void addAggOrdBookRequest(AggregatedOrderBookRequest aggregatedOrderBookRequest) {

		_inputMessageRingBuffer.publishEvent((event, sequence, buffer) -> event.set(aggregatedOrderBookRequest));
    }


	private static class MatchingEngineInputMessageEvent
	{
		private MatchingEngine.MatchingEngineInputMessageFlag _value;

		public void set(MatchingEngine.MatchingEngineInputMessageFlag value)
		{
			_value = value;
		}
	}

}
