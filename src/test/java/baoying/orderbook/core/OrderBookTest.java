package baoying.orderbook.core;

import java.util.*;

import baoying.orderbook.util.Util;
import org.junit.Assert;
import org.junit.Test;

import baoying.orderbook.core.CommonMessage.Side;
import baoying.orderbook.core.MarketDataMessage.OrderBookDelta;
import baoying.orderbook.core.OrderBook.ExecutingOrder;
import baoying.orderbook.core.OrderBook.MEExecutionReportMessageFlag;
import baoying.orderbook.core.TradeMessage.MatchedExecutionReport;
import baoying.orderbook.core.TradeMessage.OriginalOrder;

public class OrderBookTest {

	OrderBook _exchange = new OrderBook("USDJPY");
	
	@Test
	public void testCreateBidBook(){
		
		String symbol = "USDJPY";
		CommonMessage.Side side = CommonMessage.Side.BID;
		
		PriorityQueue<ExecutingOrder> book = _exchange.createBidBook();
		
		OriginalOrder o_100_1mio = new OriginalOrder( System.currentTimeMillis(),symbol, side, CommonMessage.OrderType.LIMIT, 100.1, 1000_000,  "orderID", "clientOrdID", "clientEntityID");
		OriginalOrder o_120_1mio = new OriginalOrder( System.currentTimeMillis(),symbol, side, CommonMessage.OrderType.LIMIT,120.1, 1000_000,  "orderID", "clientOrdID", "clientEntityID");
		OriginalOrder o_130_1mio_sysT1 = new OriginalOrder( System.currentTimeMillis(),symbol, side,CommonMessage.OrderType.LIMIT, 130.1, 1000_000, "orderID", "clientOrdID", "clientEntityID");
		OriginalOrder o_130_1mio_sysT2 = new OriginalOrder( System.currentTimeMillis(),symbol, side, CommonMessage.OrderType.LIMIT,130.1, 1000_000, "orderID", "clientOrdID", "clientEntityID");
		
		book.add(new ExecutingOrder(o_100_1mio));
		book.add(new ExecutingOrder(o_130_1mio_sysT1));
		book.add(new ExecutingOrder(o_120_1mio));
		book.add(new ExecutingOrder(o_130_1mio_sysT2));

		List<ExecutingOrder> books = new ArrayList<>();
		while( !book.isEmpty() ){
			books.add(book.poll());
		}

		int[] expectedQties = new int[]{1000_000,1000_000,1000_000,1000_000};
		double[] expectedPrices = new double[]{130.1,130.1,120.1,100.1};

		for(int i=0; i<books.size(); i++){
			Assert.assertEquals(expectedQties[i],books.get(i)._origOrder._qty);
			Assert.assertTrue(Math.abs(expectedPrices[i]-books.get(i)._origOrder._price) < OrderBook.MIN_DIFF_FOR_PRICE);
		}


	}

	@Test
	public void testCreateAskBook(){

		String symbol = "USDJPY";
		CommonMessage.Side side = CommonMessage.Side.OFFER;


		OriginalOrder o_140_1mio = new OriginalOrder( System.currentTimeMillis(),symbol, side,CommonMessage.OrderType.LIMIT, 140.1, 1000_000, "orderID", "clientOrdID", "clientEntityID");
		OriginalOrder o_150_1mio = new OriginalOrder( System.currentTimeMillis(),symbol, side,CommonMessage.OrderType.LIMIT, 150.1, 1000_000, "orderID", "clientOrdID", "clientEntityID");
		OriginalOrder o_160_1mio_sysT1 = new OriginalOrder( System.currentTimeMillis(),symbol, side, CommonMessage.OrderType.LIMIT,160.1, 1000_000, "orderID", "clientOrdID", "clientEntityID");
		OriginalOrder o_160_1mio_sysT2 = new OriginalOrder( System.currentTimeMillis(),symbol, side, CommonMessage.OrderType.LIMIT,160.1, 1000_000,  "orderID", "clientOrdID", "clientEntityID");

		PriorityQueue<ExecutingOrder> book = _exchange.createAskBook();
		book.add(new ExecutingOrder(o_140_1mio));
		book.add(new ExecutingOrder(o_150_1mio));
		book.add(new ExecutingOrder(o_160_1mio_sysT1));
		book.add(new ExecutingOrder(o_160_1mio_sysT2));

		List<ExecutingOrder> books = new ArrayList<>();
		while( !book.isEmpty() ){
			books.add(book.poll());
		}

		int[] expectedQties = new int[]{1000_000,1000_000,1000_000,1000_000};
		double[] expectedPrices = new double[]{140.1,150.1,160.1,160.1};
        CommonMessage.Side[] expectedSides = new CommonMessage.Side[]{Side.OFFER,Side.OFFER,Side.OFFER,Side.OFFER};

		for(int i=0; i<books.size(); i++){
			Assert.assertEquals(expectedQties[i],books.get(i)._origOrder._qty);
			Assert.assertTrue(Math.abs(expectedPrices[i]-books.get(i)._origOrder._price) < OrderBook.MIN_DIFF_FOR_PRICE);
            Assert.assertEquals(expectedSides[i],books.get(i)._origOrder._side);
		}
	}


	@Test
	public void testMatch(){
		String symbol = "USDJPY";


		PriorityQueue<ExecutingOrder> bidBook = _exchange.createBidBook();
		{
			CommonMessage.Side side = CommonMessage.Side.BID;
			OriginalOrder o_100_1mio = new OriginalOrder( System.currentTimeMillis(),symbol, side, CommonMessage.OrderType.LIMIT,100.1, 1000_000,  "orderID", "clientOrdID", "clientEntityID");
			OriginalOrder o_120_1mio = new OriginalOrder( System.currentTimeMillis(),symbol, side,CommonMessage.OrderType.LIMIT, 120.1, 1000_000,  "orderID", "clientOrdID", "clientEntityID");
			OriginalOrder o_130_1mio_sysT1 = new OriginalOrder( System.currentTimeMillis(),symbol, side,CommonMessage.OrderType.LIMIT, 130.1, 1000_000,"orderID", "clientOrdID", "clientEntityID");
			OriginalOrder o_130_1mio_sysT2 = new OriginalOrder( System.currentTimeMillis(),symbol, side,CommonMessage.OrderType.LIMIT, 130.1, 1000_000,  "orderID", "clientOrdID", "clientEntityID");

			bidBook.add(new ExecutingOrder(o_100_1mio));
			bidBook.add(new ExecutingOrder(o_130_1mio_sysT1));
			bidBook.add(new ExecutingOrder(o_120_1mio));
			bidBook.add(new ExecutingOrder(o_130_1mio_sysT2));
		}

		PriorityQueue<ExecutingOrder> askBook = _exchange.createAskBook();
		{
			CommonMessage.Side side = CommonMessage.Side.OFFER;
			OriginalOrder o_140_1mio = new OriginalOrder( System.currentTimeMillis(),symbol, side, CommonMessage.OrderType.LIMIT,140.1, 1000_000,  "orderID", "clientOrdID", "clientEntityID2");
			OriginalOrder o_150_1mio = new OriginalOrder( System.currentTimeMillis(),symbol, side, CommonMessage.OrderType.LIMIT,150.1, 1000_000,  "orderID", "clientOrdID", "clientEntityID2");
			OriginalOrder o_160_1mio_sysT1 = new OriginalOrder( System.currentTimeMillis(),symbol, side,CommonMessage.OrderType.LIMIT, 160.1, 1000_000, "orderID", "clientOrdID", "clientEntityID2");
			OriginalOrder o_160_1mio_sysT2 = new OriginalOrder( System.currentTimeMillis(),symbol, side, CommonMessage.OrderType.LIMIT,160.1, 1000_000, "orderID", "clientOrdID", "clientEntityID2");


			askBook.add(new ExecutingOrder(o_140_1mio));
			askBook.add(new ExecutingOrder(o_160_1mio_sysT1));
			askBook.add(new ExecutingOrder(o_150_1mio));
			askBook.add(new ExecutingOrder(o_160_1mio_sysT2));
		}

		OriginalOrder bid_145_1point5Mio = new OriginalOrder( System.currentTimeMillis(),symbol, CommonMessage.Side.BID,CommonMessage.OrderType.LIMIT, 155, 1500_000,  "orderID", "clientOrdID", "clientEntityID");

		Util.Tuple<List<MEExecutionReportMessageFlag>, List<OrderBookDelta>> result = this._exchange.match(new ExecutingOrder(bid_145_1point5Mio), askBook, bidBook);
		List<MEExecutionReportMessageFlag> reports = result._1;
		List<OrderBookDelta> orderbookDeltas = result._2;

		List<String> expectedERs = Arrays.asList(new String[]{"last px:140.1 last qty:1000000",
				"last px:150.1 last qty:500000"
		});
		List<String> actualERs = new ArrayList<>();
		for(MEExecutionReportMessageFlag r : reports){
			MatchedExecutionReport er = (MatchedExecutionReport)r;
			actualERs.add("last px:"+er._lastPrice +" last qty:"+ er._lastQty);
		}
		Assert.assertEquals(expectedERs, actualERs);


		List<String> expectedDeltas = Arrays.asList(new String[]{"delta:USDJPY -1000000 140.1 OFFER",
				"delta:USDJPY -500000 150.1 OFFER"
		});
		List<String> actualDeltas = new ArrayList<>();
		for(OrderBookDelta r : orderbookDeltas){
			actualDeltas.add("delta:"+r._symbol+" "+ r._deltaQty_couldNegative+" " +r._px + " "+r._side);
		}
		Assert.assertEquals(expectedDeltas, actualDeltas);

		List<String> expectedBidBook = Arrays.asList(new String[]{
				"130.1 1000000",
				"130.1 1000000",
				"120.1 1000000",
				"100.1 1000000",
		});
		compareOrderBook(bidBook, expectedBidBook);

		List<String> expectedAskBook = Arrays.asList(new String[]{
				"150.1 500000",
				"160.1 1000000",
				"160.1 1000000"
		});
		compareOrderBook(askBook, expectedAskBook);
	}

	@Test
	public void testBuildOrderbookSnapshot(){

		String symbol = "USDJPY";

		PriorityQueue<ExecutingOrder> bidBook = _exchange.createBidBook();
		{
			CommonMessage.Side side = CommonMessage.Side.BID;
			OriginalOrder o_100_1mio = new OriginalOrder( System.currentTimeMillis(),symbol, side,CommonMessage.OrderType.LIMIT, 100.1, 1000_000,  "orderID", "clientOrdID", "clientEntityID");
			OriginalOrder o_120_1mio = new OriginalOrder( System.currentTimeMillis(),symbol, side,CommonMessage.OrderType.LIMIT, 120.1, 1000_000,  "orderID", "clientOrdID", "clientEntityID");
			OriginalOrder o_130_1mio_sysT1 = new OriginalOrder( System.currentTimeMillis(),symbol, side,CommonMessage.OrderType.LIMIT, 130.1, 1000_000,  "orderID", "clientOrdID", "clientEntityID");
			OriginalOrder o_130_1mio_sysT2 = new OriginalOrder( System.currentTimeMillis(),symbol, side,CommonMessage.OrderType.LIMIT, 130.1, 1000_000,  "orderID", "clientOrdID", "clientEntityID");

			bidBook.add(new ExecutingOrder(o_100_1mio));
			bidBook.add(new ExecutingOrder(o_130_1mio_sysT1));
			bidBook.add(new ExecutingOrder(o_120_1mio));
			bidBook.add(new ExecutingOrder(o_130_1mio_sysT2));
		}

		PriorityQueue<ExecutingOrder> askBook = _exchange.createAskBook();
		{
			CommonMessage.Side side = CommonMessage.Side.OFFER;
			OriginalOrder o_140_1mio = new OriginalOrder( System.currentTimeMillis(),symbol, side, CommonMessage.OrderType.LIMIT,140.1, 1000_000,  "orderID", "clientOrdID", "clientEntityID");
			OriginalOrder o_150_1mio = new OriginalOrder( System.currentTimeMillis(),symbol, side, CommonMessage.OrderType.LIMIT,150.1, 1000_000,  "orderID", "clientOrdID", "clientEntityID");
			OriginalOrder o_160_1mio_sysT1 = new OriginalOrder( System.currentTimeMillis(),symbol, side,CommonMessage.OrderType.LIMIT, 160.1, 1000_000,  "orderID", "clientOrdID", "clientEntityID");
			OriginalOrder o_160_1mio_sysT2 = new OriginalOrder( System.currentTimeMillis(),symbol, side, CommonMessage.OrderType.LIMIT,160.1, 1000_000,  "orderID", "clientOrdID", "clientEntityID");
			OriginalOrder o_170_1mio = new OriginalOrder( System.currentTimeMillis(),symbol, side, CommonMessage.OrderType.LIMIT,170.1, 1000_000,  "orderID", "clientOrdID", "clientEntityID");


			askBook.add(new ExecutingOrder(o_140_1mio));
			askBook.add(new ExecutingOrder(o_160_1mio_sysT1));
			askBook.add(new ExecutingOrder(o_150_1mio));
			askBook.add(new ExecutingOrder(o_160_1mio_sysT2));
			askBook.add(new ExecutingOrder(o_170_1mio));
		}

		List<String> expectedAggBidBook = Arrays.asList(new String[]{
				"130.1 2000000",
				"120.1 1000000",
				"100.1 1000000",
		});
		List<String> actualAggBidBook = new ArrayList<>();
		TreeMap<Double, Integer> bidBookSnapshot = _exchange.buildOneSideAggOrdBook(3, Side.BID, bidBook);
		for(Double price : bidBookSnapshot.keySet() ){
			int aggQty = bidBookSnapshot.get(price);
			actualAggBidBook.add(price  + " " + aggQty);
		}
		Assert.assertEquals(expectedAggBidBook, actualAggBidBook);


		List<String> expectedAggAskBook = Arrays.asList(new String[]{
				"140.1 1000000",
				"150.1 1000000",
				"160.1 2000000",
				"170.1 1000000",
		});
		List<String> actualAggAskBook = new ArrayList<>();
		TreeMap<Double, Integer> offerBookSnapshot = _exchange.buildOneSideAggOrdBook(5, Side.OFFER, askBook);
		for(Double price : offerBookSnapshot.keySet() ){
			int aggQty = offerBookSnapshot.get(price);
			actualAggAskBook.add(price  + " " + aggQty);
		}
		Assert.assertEquals(expectedAggAskBook, actualAggAskBook);
	}

	//TODO make a copy of the book, rather than drain it
	//NOTE: not easy to make a copy, because the comparator is customized.
	private void compareOrderBook(PriorityQueue<ExecutingOrder> book, List<String> expectedBook){

		List<String> acturalBidBook = new ArrayList<>();
		while( !book.isEmpty() ){
			ExecutingOrder o = book.poll();
			acturalBidBook.add(o._origOrder._price + " " + o._leavesQty);
		}
		Assert.assertEquals(expectedBook, acturalBidBook);

	}
}
