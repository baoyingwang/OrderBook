package baoying.orderbook;

import baoying.orderbook.MarketDataMessage.AggregatedOrderBookRequest;
import baoying.orderbook.TradeMessage.OriginalOrder;
import baoying.orderbook.TradeMessage.SingleSideExecutionReport;
import com.google.common.eventbus.AsyncEventBus;
import com.lmax.disruptor.RingBuffer;
import com.lmax.disruptor.WaitStrategy;
import com.lmax.disruptor.dsl.Disruptor;
import com.lmax.disruptor.dsl.ProducerType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;

public class MatchingEngine {

    public static String LATENCY_ENTITY_PREFIX = "LxTxCx";
    private final static Logger log = LoggerFactory.getLogger(MatchingEngine.class);

    public final List<String> _symbols;
	private final Map<String, OrderBook> _orderBooks;

	//Assumption: our message generation speed will NOT be faster than 1000,000,000/second.
	private final long _msgIDBase = System.nanoTime();
	private final AtomicLong _msgIDIncreament = new  AtomicLong(0);

	private final Disruptor<MatchingEngineInputMessageEvent> _inputMessageDisruptor;
	private final RingBuffer<MatchingEngineInputMessageEvent> _inputMessageRingBuffer;

	private final AsyncEventBus _outputMarketDataBus;
	private final AsyncEventBus _outputExecutionReportsBus;
	private final int _bufferSize ;

	private final BlockingQueue<OrderBook.MatchingEngineInputMessageFlag> _inputMessagesBlockingQueue;

	private final QueueType _QueueType;

	enum QueueType {
		DISRUPTOR, BLOCKING_QUEUE;
	}


	public MatchingEngine(List<String> symbols,
						  AsyncEventBus outputExecutionReportsBus,
						  AsyncEventBus outputMarketDataBus,
						  int bufferSize){

        _symbols = Collections.unmodifiableList(symbols);
        _orderBooks = new HashMap<>();
        for(String symbol: symbols){
            _orderBooks.put(symbol, new OrderBook(symbol));
        }

		log.info("BlockingQueue with buffer size:{}", bufferSize);

		_QueueType = QueueType.BLOCKING_QUEUE;
		_inputMessageDisruptor = null;
		_inputMessageRingBuffer = null;

		_outputExecutionReportsBus = outputExecutionReportsBus;
		_outputMarketDataBus = outputMarketDataBus;

		_bufferSize = bufferSize;

		_inputMessagesBlockingQueue = new ArrayBlockingQueue<OrderBook.MatchingEngineInputMessageFlag>(_bufferSize);
		Executor blockingQueueStrategyExecutor = Executors.newSingleThreadExecutor(new ThreadFactory() {
            @Override
            public Thread newThread(Runnable r){ return new Thread(r, "Thread - matching engine - input Q processing"); }
        });
		blockingQueueStrategyExecutor.execute(new Runnable() {
			@Override
			public void run() {
				while(!Thread.currentThread().isInterrupted()){
                    try {
                        OrderBook.MatchingEngineInputMessageFlag request = _inputMessagesBlockingQueue.take();
                        processInputMessage(request);
                    }catch (InterruptedException e){
                        log.error("exception ", e);
                        Thread.currentThread().interrupt(); //restore the interrupt state, sicne the catch will reset it
                    }
            	}

                log.warn("exiting matching engine processing queue");
			}
		});
	}
	public MatchingEngine(List<String> symbols,
						  AsyncEventBus outputExecutionReportsBus,
						  AsyncEventBus outputMarketDataBus,
                          int disruptorBufferSize,
						  WaitStrategy waitStrategy) {

	    //TODO dupliate codes
        _symbols = Collections.unmodifiableList(symbols);
        _orderBooks = new HashMap<>();
        for(String symbol: symbols){
            _orderBooks.put(symbol, new OrderBook(symbol));
        }

		log.info("DISRUPTOR with buffer size:{}, and strategy:{}", disruptorBufferSize, waitStrategy.toString());
		_QueueType = QueueType.DISRUPTOR;
		_inputMessagesBlockingQueue = null;

		_outputExecutionReportsBus = outputExecutionReportsBus;
		_outputMarketDataBus = outputMarketDataBus;

		_bufferSize = disruptorBufferSize;

		Executor disruptorExecutor = Executors.newSingleThreadExecutor(new ThreadFactory() {
            @Override
            public Thread newThread(Runnable r){ return new Thread(r, "Thread - matching engine - input disruptor ringbuffer processing"); }
        });

		_inputMessageDisruptor = new Disruptor<>(MatchingEngineInputMessageEvent::new, disruptorBufferSize, disruptorExecutor, ProducerType.MULTI,waitStrategy);
		_inputMessageRingBuffer = _inputMessageDisruptor.getRingBuffer();
		//_inputMessageDisruptor.handleEventsWith(this::handleEvent);
		_inputMessageDisruptor.handleEventsWith(
				(MatchingEngineInputMessageEvent event, long sequence, boolean endOfBatch) ->{
					OrderBook.MatchingEngineInputMessageFlag originalOrderORAggBookRequest = event._value;
					processInputMessage(originalOrderORAggBookRequest);
				});
	}

	public void start(){

		switch (_QueueType){
			case DISRUPTOR:
				_inputMessageDisruptor.start();
				break;
			case BLOCKING_QUEUE:
				//nothing, since the executor is started in constructor
				break;
			default:
				throw new RuntimeException("unknown strategy : " + _QueueType);
		};
	}

	// check matching result(ExecutionReport) from _processResult
	public SingleSideExecutionReport addOrder(OriginalOrder order) {

        OrderBook orderBook = orderBook(order._symbol);

		if(order._clientEntityID.startsWith(LATENCY_ENTITY_PREFIX)){
			order._enterInputQ_sysNano_test = System.nanoTime();
			order._isLatencyTestOrder=true;
		}

		final boolean published ;
		switch (_QueueType){
			case DISRUPTOR:
				published = _inputMessageRingBuffer.tryPublishEvent((event, sequence, buffer) -> event.set(order));
				break;
			case BLOCKING_QUEUE:
				published = _inputMessagesBlockingQueue.offer(order);
				break;
			default:
				throw new RuntimeException("unknown strategy : " + _QueueType);
		}
		if(published) {
			return new SingleSideExecutionReport(_msgIDBase + _msgIDIncreament.incrementAndGet(),
					System.currentTimeMillis(),
					order,
					TradeMessage.ExecutionType.NEW,
					order._qty,
					"Entered Order Book");
		}else{
			log.warn("fail to add order to book, because of capacity, client:{}, client ord id:{}", order._clientEntityID, order._clientOrdID);
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

        switch (_QueueType){
            case DISRUPTOR:
                _inputMessageRingBuffer.publishEvent((event, sequence, buffer) -> event.set(aggregatedOrderBookRequest));
                break;
            case BLOCKING_QUEUE:
                _inputMessagesBlockingQueue.offer(aggregatedOrderBookRequest);
                break;
            default:
                throw new RuntimeException("unknown strategy : " + _QueueType);
        }

    }

	void processInputMessage(OrderBook.MatchingEngineInputMessageFlag originalOrderORAggBookRequest)
	{


		if (originalOrderORAggBookRequest instanceof OriginalOrder) {


			OriginalOrder order = (OriginalOrder) originalOrderORAggBookRequest;
            OrderBook _orderBook = orderBook(order._symbol);

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
            OrderBook orderBook = orderBook(aggOrdBookRequest._symbol);
			MarketDataMessage.AggregatedOrderBook aggOrderBook = orderBook.buildAggregatedOrderBook(aggOrdBookRequest._depth);
			_outputMarketDataBus.post(aggOrderBook);
		} else {
			log.error("received unknown type : {}",
					originalOrderORAggBookRequest.getClass().toGenericString());
		}
	}

	private OrderBook orderBook(String symbol){

	     OrderBook ob = _orderBooks.get(symbol);

	     if(ob == null){
	         throw new RuntimeException("cannot identify related order book from symbol:"+symbol);
         }
         return  ob;
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
