package baoying.orderbook.core;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.PriorityQueue;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicLong;

import baoying.orderbook.util.Util;

import baoying.orderbook.core.CommonMessage.Side;
import baoying.orderbook.core.MarketDataMessage.AggregatedOrderBook;
import baoying.orderbook.core.MarketDataMessage.OrderBookDelta;
import baoying.orderbook.core.TradeMessage.MatchedExecutionReport;
import baoying.orderbook.core.TradeMessage.OriginalOrder;
import baoying.orderbook.core.TradeMessage.SingleSideExecutionReport;

/**
 * //TODO check minimize qty - if the leaves qty is less than min_qty, it will be rejeted.
 * An OrderBook for a specific symbol.
 * note: NOT threadsafe. The {@link MatchingEngine} promises single thread context.
 * note: consider use other data structure(instead of simply priority queue) for book, to 1) simplify dump order book. 2) for FX, support match 2nd best price, if no relationship with 1st one(should this be supported?).
 * note: for FX swap, we could use same logic, symbol like USDJPY_1W. But only support Spot+Fwd as standard way.
 *
*/
public class OrderBook {

	public final String _symbol;

	public static double MIN_DIFF_FOR_PRICE = 0.00000001;

	//Assumption: our message generation speed will NOT be faster than 1000,000,000/second.
	private final long _msgIDBase = System.nanoTime();
	private final AtomicLong _msgIDIncreament = new  AtomicLong(0);


	// for FX, bid|ask the base ccy,
	// e.g. for USDJPY, USD is always the base ccy, and JPY is the terms ccy.
	// not required to be thread safe, since it will be operated by single
	// thread
	private PriorityQueue<ExecutingOrder> _bidBook;// higher price is on the top
	private PriorityQueue<ExecutingOrder> _offerBook;// lower price is on the
	                                                 // top

	public OrderBook(String symbol) {
		_symbol = symbol;
		_bidBook = createBidBook();
		_offerBook = createAskBook();
	}

    Util.Tuple<List<MEExecutionReportMessageFlag>, List<OrderBookDelta>> matchOrder(OriginalOrder order) {

        ExecutingOrder executingOrder = new ExecutingOrder(order);

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

		Util.Tuple<List<MEExecutionReportMessageFlag>, List<OrderBookDelta>> matchResult = match(executingOrder, contraSideBook, sameSideBook);

		return matchResult;
	}

	/*-
	 * - executingOrder, contraSideBook, and sameSideBook will be updated accordingly 
	 * - TODO all clients could trade with each other. There is NO relationship/credit check. 
	 * - not private, because of UT
	 */
	Util.Tuple<List<MEExecutionReportMessageFlag>, List<OrderBookDelta>> match(ExecutingOrder executingOrder,
																			PriorityQueue<ExecutingOrder> contraSideBook,
	        																PriorityQueue<ExecutingOrder> sameSideBook) {

		List<MEExecutionReportMessageFlag> execReportsAsResult = new ArrayList<>();
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
					    //MIN_DIFF_FOR_PRICE is not required, if we promises that all prices have been rounded with same scale, in advance
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
				        TradeMessage.ExecutionType.REJECTED, executingOrder._leavesQty,"Cannot trade with yourself"));
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

			executionReport = new MatchedExecutionReport(_msgIDBase + _msgIDIncreament.incrementAndGet(),
					System.currentTimeMillis(),
					lastPrice,
					lastQty,
					peekedContraBestPriceOrder._origOrder, peekedContraBestPriceOrder._leavesQty,
					executingOrder._origOrder, executingOrder._leavesQty);

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
                            TradeMessage.ExecutionType.CANCELLED, executingOrder._leavesQty,"No available liquidity for this market order"));
                        break;
                    case LIMIT :
                    	if(execReportsAsResult.size() ==0){
                    		//partially filled or fully filled, such NEW execution report is not required.
							execReportsAsResult.add(new SingleSideExecutionReport(_msgIDBase + _msgIDIncreament.incrementAndGet(),  System.currentTimeMillis(),executingOrder._origOrder,
									TradeMessage.ExecutionType.NEW, executingOrder._leavesQty,"Entered OrderBook"));
						}
                        sameSideBook.add(executingOrder);
                        orderbookDeltasAsResult.add(new OrderBookDelta(_symbol, executingOrder._origOrder._side,
                                executingOrder._origOrder._price, executingOrder._leavesQty));

                        break;
                    default:
            }
		}

		return new Util.Tuple<List<MEExecutionReportMessageFlag>, List<OrderBookDelta>>(execReportsAsResult,orderbookDeltasAsResult);
	}


	AggregatedOrderBook buildAggregatedOrderBook(int depth) {

		TreeMap<Double, Integer> bidBookMap   = buildOneSideAggOrdBook(depth, Side.BID, _bidBook);
		TreeMap<Double, Integer> offerBookMap = buildOneSideAggOrdBook(depth, Side.OFFER, _offerBook);

		return new AggregatedOrderBook(_symbol, depth, _msgIDBase + _msgIDIncreament.incrementAndGet(), bidBookMap, offerBookMap);
	}

	MarketDataMessage.DetailOrderBook buildDetailedOrderBook(int depth){

		TreeMap<Double, List<MarketDataMessage.DetailOrderBook.MDOrder>> bidBookMap   = buildOneSideDetailOrdBook(depth, Side.BID, _bidBook);
		TreeMap<Double, List<MarketDataMessage.DetailOrderBook.MDOrder>> offerBookMap = buildOneSideDetailOrdBook(depth, Side.OFFER, _offerBook);

		return new MarketDataMessage.DetailOrderBook(_symbol, depth, _msgIDBase + _msgIDIncreament.incrementAndGet(), bidBookMap, offerBookMap);

	}

    /*-
     * - the map key is sorted. bid - reverse, offer - natural ordering
     * - the map value's order is same with the one of matching order book
     */
    TreeMap<Double, List<MarketDataMessage.DetailOrderBook.MDOrder>> buildOneSideDetailOrdBook(int depth, Side side, PriorityQueue<ExecutingOrder> sameSideBook) {

        final PriorityQueue<ExecutingOrder> shadowCopyOfSameSideBook;
        final TreeMap<Double, List<MarketDataMessage.DetailOrderBook.MDOrder>> bookMap;
        switch (side) {
            case BID:
                shadowCopyOfSameSideBook = createBidBook();
                shadowCopyOfSameSideBook.addAll(sameSideBook); // TODO possible
                // performance issue
                // on
                // huge book. Not found better way, yet.
                bookMap = new TreeMap<Double, List<MarketDataMessage.DetailOrderBook.MDOrder>>(new Comparator<Double>() {

                    @Override
                    public int compare(Double o1, Double o2) {

                        // TODO i assume there is no 1.300000000000000000001 and
                        // 1.29999999999999 case. why? I expect the incoming FIX
                        // message will be checked on the decimal scale of price.
                        if( o1.equals(o2)) return 0;

                        return o1 - o2 > 0 ? -1 : 1;
                    }
                });
                break;
            case OFFER:
                shadowCopyOfSameSideBook = createAskBook();
                shadowCopyOfSameSideBook.addAll(sameSideBook);// TODO possible
                // performance issue
                // on huge
                // book
                bookMap = new TreeMap<Double, List<MarketDataMessage.DetailOrderBook.MDOrder>>(new Comparator<Double>() {

                    @Override
                    public int compare(Double o1, Double o2) {

                        // TODO i assume there is no 1.300000000000000000001 and
                        // 1.29999999999999 case. why? I expect the incoming FIX
                        // message will be checked on the decimal scale of price.
                        if( o1.equals(o2)) return 0;

                        return o1 - o2 > 0 ? 1 : -1;
                    }
                });                break;
            default:
                // TODO log error, and don't throw exception to avoid the engine
                // shutdown
                throw new RuntimeException("");
        }



        while (!shadowCopyOfSameSideBook.isEmpty()) {
            ExecutingOrder o = shadowCopyOfSameSideBook.poll();
            double price = o._origOrder._price;
            int leavesQty = o._leavesQty;


            List<MarketDataMessage.DetailOrderBook.MDOrder> mdOrders = bookMap.get(price) == null ?
                            new ArrayList<>(): bookMap.get(price);

            mdOrders.add(new MarketDataMessage.DetailOrderBook.MDOrder(o._origOrder._clientEntityID,
                    o._origOrder._clientOrdID,
                    o._leavesQty, o._enteringMS, o._increasingID));
            bookMap.put(price, mdOrders);

            if (bookMap.size() == depth + 1) {
                bookMap.remove(price);
                break;
            }
        }

        return bookMap;
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


                // TODO i assume there is no 1.300000000000000000001 and
                // 1.29999999999999 case. why? I expect the incoming FIX
                // message will be checked on the decimal scale of price.
                @Override
                public int compare(Double o1, Double o2) {

                    // TODO i assume there is no 1.300000000000000000001 and
                    // 1.29999999999999 case. why? I expect the incoming FIX
                    // message will be checked on the decimal scale of price.
                    if( o1.equals(o2)) return 0;

                    return o1 - o2 > 0 ? -1 : 1;
                }
			});
			break;
		case OFFER:
			shadowCopyOfSameSideBook = createAskBook();
			shadowCopyOfSameSideBook.addAll(sameSideBook);// TODO possible
			                                              // performance issue
			                                              // on huge
			// book
            bookMap = new TreeMap<Double, Integer>(new Comparator<Double>() {

                // TODO i assume there is no 1.300000000000000000001 and
                // 1.29999999999999 case. why? I expect the incoming FIX
                // message will be checked on the decimal scale of price.
                @Override
                public int compare(Double o1, Double o2) {

                    // TODO i assume there is no 1.300000000000000000001 and
                    // 1.29999999999999 case. why? I expect the incoming FIX
                    // message will be checked on the decimal scale of price.
                    if( o1.equals(o2)) return 0;

                    return o1 - o2 > 0 ? 1 : -1;
                }
            });
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

	public static interface MDMarketDataMessageFlag {
	}
	public static interface MEExecutionReportMessageFlag {
	}

	static class ExecutingOrder{

		final OriginalOrder _origOrder;

        private static final AtomicLong _msgIDIncreament = new  AtomicLong(0);
        final long _enteringMS;
        //_increasingID is only useful when comparing 2 executing orders with same _enteringMS
        //why only use System.nanoTime directly, but _enteringMS+_increasingID? Because
        //1. nanoTime would be reset of server restart
        //2. _enteringMS is more meaningful time
        final long _increasingID = _msgIDIncreament.getAndIncrement();


		// this value will change on each matching
		int _leavesQty;



		ExecutingOrder(OriginalOrder originalOrder) {
			_origOrder = originalOrder;
			_leavesQty = originalOrder._qty;
            _enteringMS = System.currentTimeMillis();
		}

	}

	PriorityQueue<ExecutingOrder> createBidBook() {
		return new PriorityQueue<ExecutingOrder>(new Comparator<ExecutingOrder>() {

			@Override
			public int compare(ExecutingOrder o1, ExecutingOrder o2) {

                //MIN_DIFF_FOR_PRICE - not required if we promise all input prices have been rounded with same scale
                //same for bid and offer
				if ( Math.abs(o1._origOrder._price - o2._origOrder._price) < MIN_DIFF_FOR_PRICE) {
				    long x = o1._enteringMS - o2._enteringMS != 0 ? o1._enteringMS - o2._enteringMS : o1._increasingID - o2._increasingID;
					return (int) x;
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

                //MIN_DIFF_FOR_PRICE - not required if we promise all input prices have been rounded with same scale
                //same for bid and offer
                if ( Math.abs(o1._origOrder._price - o2._origOrder._price) < MIN_DIFF_FOR_PRICE) {
                    long x = o1._enteringMS - o2._enteringMS != 0 ? o1._enteringMS - o2._enteringMS : o1._increasingID - o2._increasingID;
                    return (int) x;
                }

				if (o1._origOrder._price > o2._origOrder._price) {
					return 1;
				} else {
					return -1;
				}

			}
		});

	}

}
