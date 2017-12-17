package baoying.orderbook;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.PriorityQueue;
import java.util.TreeMap;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;

import com.lmax.disruptor.BusySpinWaitStrategy;
import com.lmax.disruptor.RingBuffer;
import com.lmax.disruptor.WaitStrategy;
import com.lmax.disruptor.dsl.Disruptor;
import com.lmax.disruptor.dsl.ProducerType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import baoying.orderbook.CommonMessage.Side;
import baoying.orderbook.MarketDataMessage.AggregatedOrderBook;
import baoying.orderbook.MarketDataMessage.AggregatedOrderBookRequest;
import baoying.orderbook.MarketDataMessage.OrderBookDelta;
import baoying.orderbook.TradeMessage.MatchedExecutionReport;
import baoying.orderbook.TradeMessage.OriginalOrder;
import baoying.orderbook.TradeMessage.SingleSideExecutionReport;

/*-
 * 
 * A matching engine for a specific symbol.
 * 
 * note: the /*- prevent the eclipse to format the below TODO list with '-' as prefix
	- //TODO consider use other data structure(instead of simply priority queue) for book, to 1) simplify dump order book. 2) for FX, support match 2nd best price, if no relationship with 1st one(should this be supported?).
	- //TODO consider to apply LMAX DISRUPTOR to improve the performance, if required.
	- //note: for FX swap, we could use same logic, symbol like USDJPY_1W. But only support Spot+Fwd as standard way.
*/
public class MatchingEngine {

	public static String LATENCY_ENTITY_PREFIX = "LTC$$";

	// e.g. USDJPY for FX, or BABA for security exchange
	private final String _symbol;

	private final static Logger log = LoggerFactory.getLogger(MatchingEngine.class);
	public static double MIN_DIFF_FOR_PRICE = 0.00000001;

	//Assumption: our message generation speed will NOT be faster than 1000,000,000/second.
	private final long _msgIDBase = System.nanoTime();
	private final AtomicLong _msgIDIncreament = new  AtomicLong(0);

    //TODO how to promise the consumer will only consume?
	public BlockingQueue<MatchingEnginOutputMessageFlag> _outputQueueForExecRpt;
	//the book depth is determined by the request
	//the delta is for ALL prices in the full engine orderbook. 
	public BlockingQueue<MatchingEnginOutputMessageFlag> _outputQueueForAggBookAndBookDelta;

	// for FX, bid|ask the base ccy,
	// e.g. for USDJPY, USD is always the base ccy, and JPY is the terms ccy.
	// not required to be thread safe, since it will be operated by single
	// thread
	private PriorityQueue<ExecutingOrder> _bidBook;// higher price is on the top
	private PriorityQueue<ExecutingOrder> _offerBook;// lower price is on the
	                                                 // top

	Disruptor<MatchingEngineInputMessageEvent> _inputMessageDisruptor;
	RingBuffer<MatchingEngineInputMessageEvent> _inputMessageRingBuffer;

	public MatchingEngine(String symbol) {
		_symbol = symbol;

		_bidBook = createBidBook();
		_offerBook = createAskBook();


		Executor executor = Executors.newSingleThreadExecutor();
		int bufferSize = 1024;
		WaitStrategy waitStrategy = new BusySpinWaitStrategy();
		_inputMessageDisruptor = new Disruptor<>(MatchingEngineInputMessageEvent::new, bufferSize, executor, ProducerType.MULTI,waitStrategy);
		_inputMessageRingBuffer = _inputMessageDisruptor.getRingBuffer();
		//_inputMessageDisruptor.handleEventsWith(this::handleEvent);
		_inputMessageDisruptor.handleEventsWith(
				(MatchingEngineInputMessageEvent event, long sequence, boolean endOfBatch) ->{
					MatchingEngineInputMessageFlag originalOrderORAggBookRequest = event._value;
					processInputMessage(originalOrderORAggBookRequest);
				});

		_outputQueueForExecRpt = new LinkedBlockingQueue<MatchingEnginOutputMessageFlag>();
		_outputQueueForAggBookAndBookDelta = new LinkedBlockingQueue<MatchingEnginOutputMessageFlag>();

	}

	// check matching result(ExecutionReport) from _processResult
	public SingleSideExecutionReport addOrder(OriginalOrder order) {

		if (!_symbol.equals(order._symbol)) {	// it should never reach here
			throw new RuntimeException("not the expected symbol, expect:"+_symbol+", by it is:"+order._symbol+", client_entity:"+order._clientEntityID+" client ord id:"+order._clientOrdID);
		}

        final ExecutingOrder initialExecutingOrder;
		if(order._clientEntityID.startsWith(LATENCY_ENTITY_PREFIX)){
			initialExecutingOrder = new ExecutingOrder(order,System.nanoTime(), true);
		}else {
			initialExecutingOrder = new ExecutingOrder(order);
		}

		_inputMessageRingBuffer.publishEvent((event, sequence, buffer) -> event.set(initialExecutingOrder));

        return new SingleSideExecutionReport(_msgIDBase + _msgIDIncreament.incrementAndGet(),
                System.currentTimeMillis(),
                order,
                TradeMessage.SingleSideExecutionType.NEW,
                order._qty,
                "Entered Order Book");
    }

    public void addAggOrdBookRequest(AggregatedOrderBookRequest aggregatedOrderBookRequest) {

		_inputMessageRingBuffer.publishEvent((event, sequence, buffer) -> event.set(aggregatedOrderBookRequest));
    }

	private void processInputMessage(MatchingEngineInputMessageFlag originalOrderORAggBookRequest)
	{
		if (originalOrderORAggBookRequest instanceof ExecutingOrder) {

			ExecutingOrder executingOrder = (ExecutingOrder) originalOrderORAggBookRequest;
			if(executingOrder._isLatencyTestOrder){
				executingOrder._sysNanoTimeOfPickingFromInputQ4LatencyTestOrder = System.nanoTime();
			}

			processInputOrder(executingOrder);

		} else if (originalOrderORAggBookRequest instanceof AggregatedOrderBookRequest) {
			AggregatedOrderBookRequest aggOrdBookRequest = (AggregatedOrderBookRequest) originalOrderORAggBookRequest;
			AggregatedOrderBook aggOrderBook = buildAggregatedOrderBook(aggOrdBookRequest._depth);
			_outputQueueForAggBookAndBookDelta.add(aggOrderBook);
		} else {
			log.error("received unknown type : {}",
					originalOrderORAggBookRequest.getClass().toGenericString());
		}
	}

	void processInputOrder(ExecutingOrder executingOrder) {

		final PriorityQueue<ExecutingOrder> contraSideBook;
		final PriorityQueue<ExecutingOrder> sameSideBook;
		{
			switch (executingOrder._origOrder._side) {
			case BID:
				sameSideBook = _bidBook;
				contraSideBook = _offerBook;
				break;
			case OFFER:
				sameSideBook = _offerBook;
				contraSideBook = _bidBook;
				break;
			default:
				throw new RuntimeException("unknown side : " + executingOrder._origOrder._side);
			}
		}

		Tuple<List<MatchingEnginOutputMessageFlag>, List<OrderBookDelta>> matchResult = match(executingOrder, contraSideBook, sameSideBook);

		_outputQueueForExecRpt.addAll(matchResult._1);
		_outputQueueForAggBookAndBookDelta.addAll(matchResult._2);
	}

	/*-
	 * - executingOrder, contraSideBook, and sameSideBook will be updated accordingly 
	 * - TODO all clients could trade with each other. There is NO relationship/credit check. 
	 * - not private, because of UT
	 */
	Tuple<List<MatchingEnginOutputMessageFlag>, List<OrderBookDelta>> match(ExecutingOrder executingOrder,
																			PriorityQueue<ExecutingOrder> contraSideBook,
	        																PriorityQueue<ExecutingOrder> sameSideBook) {

		List<MatchingEnginOutputMessageFlag> execReportsAsResult = new ArrayList<>();
		List<OrderBookDelta> orderbookDeltasAsResult= new ArrayList<>();

		boolean rejected = false;
		while (true) {

			ExecutingOrder peekedContraBestPriceOrder = contraSideBook.peek();
			if (peekedContraBestPriceOrder == null) {
				break;
			}

			final boolean isExecutablePrice;
			switch(executingOrder._origOrder._type) {
				case MARKET :
					isExecutablePrice= true;
					break;
				case LIMIT :
					switch (executingOrder._origOrder._side) {
						case BID:
							isExecutablePrice = executingOrder._origOrder._price
									+ MIN_DIFF_FOR_PRICE > peekedContraBestPriceOrder._origOrder._price;
							break;
						case OFFER:
							isExecutablePrice = executingOrder._origOrder._price
									- MIN_DIFF_FOR_PRICE < peekedContraBestPriceOrder._origOrder._price;
							break;
						default:
							throw new RuntimeException("unknown side : " + executingOrder._origOrder._side);
					}
					break;
				default :
					//TODO reject this deal while process problem. Maybe by the outter exception catch.
					throw new RuntimeException("unknown order type");
			}
			if (!isExecutablePrice) {
				break;
			}

			if (peekedContraBestPriceOrder._origOrder._clientEntityID
			        .equals(executingOrder._origOrder._clientEntityID)) {
				execReportsAsResult.add(new SingleSideExecutionReport(_msgIDBase + _msgIDIncreament.incrementAndGet(), System.currentTimeMillis(),executingOrder._origOrder,
				        TradeMessage.SingleSideExecutionType.REJECTED, executingOrder._leavesQty,"Cannot trade with yourself"));
				rejected = true;
				break;
			}

			// pick the price on top of book, for both BID and ASK order
			final double lastPrice = peekedContraBestPriceOrder._origOrder._price;
			final int lastQty = peekedContraBestPriceOrder._leavesQty < executingOrder._leavesQty
			        ? peekedContraBestPriceOrder._leavesQty : executingOrder._leavesQty;

			peekedContraBestPriceOrder._leavesQty = peekedContraBestPriceOrder._leavesQty - lastQty;
			executingOrder._leavesQty = executingOrder._leavesQty - lastQty;

			final MatchedExecutionReport executionReport ;

			if(executingOrder._isLatencyTestOrder){
                //only track taker side latency, since maker maybe has sit in orderbook long time

				executionReport = new MatchedExecutionReport(_msgIDBase + _msgIDIncreament.incrementAndGet(),
						System.currentTimeMillis(),
						lastPrice,
						lastQty,

						peekedContraBestPriceOrder._origOrder, peekedContraBestPriceOrder._leavesQty,
						executingOrder._origOrder, executingOrder._leavesQty,

						executingOrder._sysNanoTimeOfOriginOrdEnteringEngine4LatencyTestOrder,
                        executingOrder._sysNanoTimeOfPickingFromInputQ4LatencyTestOrder,
                        System.nanoTime());
			}else{
				executionReport = new MatchedExecutionReport(_msgIDBase + _msgIDIncreament.incrementAndGet(),
						System.currentTimeMillis(),

						lastPrice,
						lastQty,

						peekedContraBestPriceOrder._origOrder, peekedContraBestPriceOrder._leavesQty,
						executingOrder._origOrder, executingOrder._leavesQty);
			}
			execReportsAsResult.add(executionReport);
			orderbookDeltasAsResult.add(
			        new OrderBookDelta(_symbol, peekedContraBestPriceOrder._origOrder._side, lastPrice, 0 - lastQty));

			if (peekedContraBestPriceOrder._leavesQty <= 0) {
				// remove it, since already all filled
				contraSideBook.poll();
			}

			if (executingOrder._leavesQty <= 0) {
				break;
			}
		}

		if (!rejected  && executingOrder._leavesQty > 0) {
                switch(executingOrder._origOrder._type) {
                    case MARKET :
                        execReportsAsResult.add(new SingleSideExecutionReport(_msgIDBase + _msgIDIncreament.incrementAndGet(),  System.currentTimeMillis(),executingOrder._origOrder,
                            TradeMessage.SingleSideExecutionType.CANCELLED, executingOrder._leavesQty,"No available liquidity for this market order"));
                        break;
                    case LIMIT :
                        sameSideBook.add(executingOrder);
                        orderbookDeltasAsResult.add(new OrderBookDelta(_symbol, executingOrder._origOrder._side,
                                executingOrder._origOrder._price, executingOrder._leavesQty));

                        break;
                    default:
            }
		}

		return new Tuple(execReportsAsResult,orderbookDeltasAsResult);
	}

	//https://dzone.com/articles/whats-wrong-java-8-part-v
	//just internal use, don't public since it is NOT general for others.
	static class Tuple<T, U> {
		public final T _1;
		public final U _2;
		public Tuple(T arg1, U arg2) {
			super();
			this._1 = arg1;
			this._2 = arg2;
		}
	}
	AggregatedOrderBook buildAggregatedOrderBook(int depth) {

		TreeMap<Double, Integer> bidBookMap = buildOneSideAggOrdBook(depth, Side.BID, _bidBook);
		TreeMap<Double, Integer> offerBookMap = buildOneSideAggOrdBook(depth, Side.OFFER, _offerBook);

		return new AggregatedOrderBook(_symbol, depth, _msgIDBase + _msgIDIncreament.incrementAndGet(), bidBookMap, offerBookMap);
	}

	/*-
	 * - the map key is sorted. bid - reverse, offer - natural ordering
	 * - the map value's order is same with the one of matching order book
	 */
	TreeMap<Double, Integer> buildOneSideAggOrdBook(int depth, Side side, PriorityQueue<ExecutingOrder> sameSideBook) {

		final PriorityQueue<ExecutingOrder> shadowCopyOfSameSideBook;
		final TreeMap<Double, Integer> bookMap;
		switch (side) {
		case BID:
			shadowCopyOfSameSideBook = createBidBook();
			shadowCopyOfSameSideBook.addAll(sameSideBook); // TODO possible
			                                               // performance issue
			                                               // on
			// huge book. Not found better way, yet.
			bookMap = new TreeMap<Double, Integer>(new Comparator<Double>() {

				@Override
				public int compare(Double o1, Double o2) {

					// TODO i assume there is no 1.300000000000000000001 and
			        // 1.29999999999999 case. why? I expect the incoming FIX
			        // message will be checked on the decimal scale of price.
					double r = o1 - o2;
					if (r > 0) {
						return -1;
					} else if (r < 0) {
						return 1;
					} else {
						return 0;
					}
				}
			});
			break;
		case OFFER:
			shadowCopyOfSameSideBook = createAskBook();
			shadowCopyOfSameSideBook.addAll(sameSideBook);// TODO possible
			                                              // performance issue
			                                              // on huge
			// book
			bookMap = new TreeMap<Double, Integer>();
			break;
		default:
			// TODO log error, and don't throw exception to avoid the engine
			// shutdown
			throw new RuntimeException("");
		}

		while (!shadowCopyOfSameSideBook.isEmpty()) {
			ExecutingOrder o = shadowCopyOfSameSideBook.poll();
			double price = o._origOrder._price;
			int leavesQty = o._leavesQty;

			int aggregatedLeavesQty = bookMap.get(price) == null ? 0 : bookMap.get(price);
			bookMap.put(price, aggregatedLeavesQty + leavesQty);

			if (bookMap.size() == depth + 1) {
				bookMap.remove(price);
				break;
			}
		}

		return bookMap;
	}

    public static interface MatchingEngineInputMessageFlag {
	}

    public static interface MatchingEnginOutputMessageFlag {
	}

	static class ExecutingOrder implements MatchingEngineInputMessageFlag{

		// this value will change on each matching
		int _leavesQty;

		final OriginalOrder _origOrder;

        final boolean _isLatencyTestOrder;
        final long _sysNanoTimeOfOriginOrdEnteringEngine4LatencyTestOrder;
        long _sysNanoTimeOfPickingFromInputQ4LatencyTestOrder;

		ExecutingOrder(OriginalOrder originalOrder) {
			this(originalOrder, -1, false);
		}

		ExecutingOrder(OriginalOrder originalOrder, long nanoTime, boolean isLatencyTestOrder) {
			_origOrder = originalOrder;
			_leavesQty = originalOrder._qty;

            _isLatencyTestOrder = isLatencyTestOrder;
			_sysNanoTimeOfOriginOrdEnteringEngine4LatencyTestOrder = nanoTime;
		}

	}

	public void start() {
		_inputMessageDisruptor.start();
		log.info("starting disruptor for "+ this._symbol );
	}

	public void stop() {
		this._inputMessageDisruptor.shutdown();
	}

	PriorityQueue<ExecutingOrder> createBidBook() {
		return new PriorityQueue<ExecutingOrder>(new Comparator<ExecutingOrder>() {

			@Override
			public int compare(ExecutingOrder o1, ExecutingOrder o2) {

				// note: not required, it should also be considered equal price, if the diff is
		        // very minor.
				if (o1._origOrder._price == o2._origOrder._price) {
					return (int) (o1._sysNanoTimeOfOriginOrdEnteringEngine4LatencyTestOrder - o2._sysNanoTimeOfOriginOrdEnteringEngine4LatencyTestOrder);
				}

				if (o1._origOrder._price > o2._origOrder._price) {
					return -1;
				} else {
					return 1;
				}

			}
		});
	}

	PriorityQueue<ExecutingOrder> createAskBook() {
		return new PriorityQueue<ExecutingOrder>(new Comparator<ExecutingOrder>() {

			@Override
			public int compare(ExecutingOrder o1, ExecutingOrder o2) {

				// TODO it should also be considered equal price, if the diff is
		        // very minor.
				if (o1._origOrder._price == o2._origOrder._price) {
					return (int) (o1._sysNanoTimeOfOriginOrdEnteringEngine4LatencyTestOrder - o2._sysNanoTimeOfOriginOrdEnteringEngine4LatencyTestOrder);
				}

				if (o1._origOrder._price > o2._origOrder._price) {
					return 1;
				} else {
					return -1;
				}

			}
		});

	}

	static class MatchingEngineInputMessageEvent
	{
		private MatchingEngineInputMessageFlag _value;

		public void set(MatchingEngineInputMessageFlag value)
		{
			this._value = value;
		}
	}

}
