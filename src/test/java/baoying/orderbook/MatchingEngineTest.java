package baoying.orderbook;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.PriorityQueue;
import java.util.TreeMap;

import org.junit.Test;

import baoying.orderbook.CommonMessage;
import baoying.orderbook.MatchingEngine;
import baoying.orderbook.CommonMessage.Side;
import baoying.orderbook.MarketDataMessage.OrderBookDelta;
import baoying.orderbook.MatchingEngine.ExecutingOrder;
import baoying.orderbook.MatchingEngine.MatchingEnginOutputMessageFlag;
import baoying.orderbook.TradeMessage.MatchedExecutionReport;
import baoying.orderbook.TradeMessage.OriginalOrder;

public class MatchingEngineTest {

	MatchingEngine _exchange = new MatchingEngine("USDJPY");
	
	@Test
	public void testCreateBidBook(){
		
		String symbol = "USDJPY";
		CommonMessage.Side side = CommonMessage.Side.BID;
		
		PriorityQueue<ExecutingOrder> book = _exchange.createBidBook();
		
		OriginalOrder o_100_1mio = new OriginalOrder(System.nanoTime(), System.currentTimeMillis(),symbol, side, 100.1, 1000_000,  "orderID", "clientOrdID", "clientEntityID");
		OriginalOrder o_120_1mio = new OriginalOrder(System.nanoTime(), System.currentTimeMillis(),symbol, side, 120.1, 1000_000,  "orderID", "clientOrdID", "clientEntityID");
		OriginalOrder o_130_1mio_sysT1 = new OriginalOrder(System.nanoTime(), System.currentTimeMillis(),symbol, side, 130.1, 1000_000, "orderID", "clientOrdID", "clientEntityID");
		OriginalOrder o_130_1mio_sysT2 = new OriginalOrder(System.nanoTime(), System.currentTimeMillis(),symbol, side, 130.1, 1000_000, "orderID", "clientOrdID", "clientEntityID");
		
		book.add(new ExecutingOrder(o_100_1mio));
		book.add(new ExecutingOrder(o_130_1mio_sysT1));
		book.add(new ExecutingOrder(o_120_1mio));
		book.add(new ExecutingOrder(o_130_1mio_sysT2));
		
		//TODO add assert, rather than view by eyes!
//		for(ExecutingOrder o : bidBook){
//			System.out.println(o._origOrder._price + " " + o._origOrder._enteringSystemTime);
//		}
		while( !book.isEmpty() ){
			ExecutingOrder o = book.poll();
			System.out.println(o._origOrder._price + " " + o._origOrder._recvFromClientEpochMS);
		}		
	}
	
	@Test
	public void testCreateAskBook(){

		String symbol = "USDJPY";
		CommonMessage.Side side = CommonMessage.Side.OFFER;
		
		
		OriginalOrder o_140_1mio = new OriginalOrder(System.nanoTime(), System.currentTimeMillis(),symbol, side, 140.1, 1000_000, "orderID", "clientOrdID", "clientEntityID");
		OriginalOrder o_150_1mio = new OriginalOrder(System.nanoTime(), System.currentTimeMillis(),symbol, side, 150.1, 1000_000, "orderID", "clientOrdID", "clientEntityID");
		OriginalOrder o_160_1mio_sysT1 = new OriginalOrder(System.nanoTime(), System.currentTimeMillis(),symbol, side, 160.1, 1000_000, "orderID", "clientOrdID", "clientEntityID");
		OriginalOrder o_160_1mio_sysT2 = new OriginalOrder(System.nanoTime(), System.currentTimeMillis(),symbol, side, 160.1, 1000_000,  "orderID", "clientOrdID", "clientEntityID");
		
		PriorityQueue<ExecutingOrder> book = _exchange.createAskBook();
		book.add(new ExecutingOrder(o_140_1mio));
		book.add(new ExecutingOrder(o_160_1mio_sysT1));
		book.add(new ExecutingOrder(o_150_1mio));
		book.add(new ExecutingOrder(o_160_1mio_sysT1));
		book.add(new ExecutingOrder(o_160_1mio_sysT2));
		
		while( !book.isEmpty() ){
			ExecutingOrder o = book.poll();
			System.out.println(o._origOrder._price + " " + o._origOrder._recvFromClientEpochMS);
		}
		
		//TODO add assert, rather than view by eyes!
//		for(ExecutingOrder o : book){
//			System.out.println(o._origOrder._price + " " + o._origOrder._enteringSystemTime);
//		}
	}
	
	
	@Test
	public void testMatch(){
		String symbol = "USDJPY";
		
		
		PriorityQueue<ExecutingOrder> bidBook = _exchange.createBidBook();
		{
			CommonMessage.Side side = CommonMessage.Side.BID;
			OriginalOrder o_100_1mio = new OriginalOrder(System.nanoTime(), System.currentTimeMillis(),symbol, side, 100.1, 1000_000,  "orderID", "clientOrdID", "clientEntityID");
			OriginalOrder o_120_1mio = new OriginalOrder(System.nanoTime(), System.currentTimeMillis(),symbol, side, 120.1, 1000_000,  "orderID", "clientOrdID", "clientEntityID");
			OriginalOrder o_130_1mio_sysT1 = new OriginalOrder(System.nanoTime(), System.currentTimeMillis(),symbol, side, 130.1, 1000_000,"orderID", "clientOrdID", "clientEntityID");
			OriginalOrder o_130_1mio_sysT2 = new OriginalOrder(System.nanoTime(), System.currentTimeMillis(),symbol, side, 130.1, 1000_000,  "orderID", "clientOrdID", "clientEntityID");
			
			bidBook.add(new ExecutingOrder(o_100_1mio));
			bidBook.add(new ExecutingOrder(o_130_1mio_sysT1));
			bidBook.add(new ExecutingOrder(o_120_1mio));
			bidBook.add(new ExecutingOrder(o_130_1mio_sysT2));
		}
		
		PriorityQueue<ExecutingOrder> askBook = _exchange.createAskBook();
		{
			CommonMessage.Side side = CommonMessage.Side.OFFER;
			OriginalOrder o_140_1mio = new OriginalOrder(System.nanoTime(), System.currentTimeMillis(),symbol, side, 140.1, 1000_000,  "orderID", "clientOrdID", "clientEntityID2");
			OriginalOrder o_150_1mio = new OriginalOrder(System.nanoTime(), System.currentTimeMillis(),symbol, side, 150.1, 1000_000,  "orderID", "clientOrdID", "clientEntityID2");
			OriginalOrder o_160_1mio_sysT1 = new OriginalOrder(System.nanoTime(), System.currentTimeMillis(),symbol, side, 160.1, 1000_000, "orderID", "clientOrdID", "clientEntityID2");
			OriginalOrder o_160_1mio_sysT2 = new OriginalOrder(System.nanoTime(), System.currentTimeMillis(),symbol, side, 160.1, 1000_000, "orderID", "clientOrdID", "clientEntityID2");
			
			
			askBook.add(new ExecutingOrder(o_140_1mio));
			askBook.add(new ExecutingOrder(o_160_1mio_sysT1));
			askBook.add(new ExecutingOrder(o_150_1mio));
			askBook.add(new ExecutingOrder(o_160_1mio_sysT2));
		}
		
		OriginalOrder bid_145_1point5Mio = new OriginalOrder(System.nanoTime(), System.currentTimeMillis(),symbol, CommonMessage.Side.BID, 155, 1500_000,  "orderID", "clientOrdID", "clientEntityID");

		MatchingEngine.Tuple<List<MatchingEnginOutputMessageFlag>, List<OrderBookDelta>> result = this._exchange.match(new ExecutingOrder(bid_145_1point5Mio), askBook, bidBook);
		List<MatchingEnginOutputMessageFlag> reports = result._1;
		List<OrderBookDelta> orderbookDeltas = result._2;
		for(MatchingEnginOutputMessageFlag r : reports){
			
			MatchedExecutionReport er = (MatchedExecutionReport)r;
			
			System.out.println("last px:"+er._lastPrice +" last qty:"+ er._lastQty);
		}

		System.out.println("bid book");
		while( !bidBook.isEmpty() ){
			ExecutingOrder o = bidBook.poll();
			System.out.println(o._origOrder._price + " " + o._leavesQty);
		}
		System.out.println("ask book");
		while( !askBook.isEmpty() ){
			ExecutingOrder o = askBook.poll();
			System.out.println(o._origOrder._price + " " + o._leavesQty);
		}
	}
	
	@Test
	public void testBuildOrderbookSnapshot(){
		
		String symbol = "USDJPY";
		
		PriorityQueue<ExecutingOrder> bidBook = _exchange.createBidBook();
		{
			CommonMessage.Side side = CommonMessage.Side.BID;
			OriginalOrder o_100_1mio = new OriginalOrder(System.nanoTime(), System.currentTimeMillis(),symbol, side, 100.1, 1000_000,  "orderID", "clientOrdID", "clientEntityID");
			OriginalOrder o_120_1mio = new OriginalOrder(System.nanoTime(), System.currentTimeMillis(),symbol, side, 120.1, 1000_000,  "orderID", "clientOrdID", "clientEntityID");
			OriginalOrder o_130_1mio_sysT1 = new OriginalOrder(System.nanoTime(), System.currentTimeMillis(),symbol, side, 130.1, 1000_000,  "orderID", "clientOrdID", "clientEntityID");
			OriginalOrder o_130_1mio_sysT2 = new OriginalOrder(System.nanoTime(), System.currentTimeMillis(),symbol, side, 130.1, 1000_000,  "orderID", "clientOrdID", "clientEntityID");
			
			bidBook.add(new ExecutingOrder(o_100_1mio));
			bidBook.add(new ExecutingOrder(o_130_1mio_sysT1));
			bidBook.add(new ExecutingOrder(o_120_1mio));
			bidBook.add(new ExecutingOrder(o_130_1mio_sysT2));
		}
		
		PriorityQueue<ExecutingOrder> askBook = _exchange.createAskBook();
		{
			CommonMessage.Side side = CommonMessage.Side.OFFER;
			OriginalOrder o_140_1mio = new OriginalOrder(System.nanoTime(), System.currentTimeMillis(),symbol, side, 140.1, 1000_000,  "orderID", "clientOrdID", "clientEntityID");
			OriginalOrder o_150_1mio = new OriginalOrder(System.nanoTime(), System.currentTimeMillis(),symbol, side, 150.1, 1000_000,  "orderID", "clientOrdID", "clientEntityID");
			OriginalOrder o_160_1mio_sysT1 = new OriginalOrder(System.nanoTime(), System.currentTimeMillis(),symbol, side, 160.1, 1000_000,  "orderID", "clientOrdID", "clientEntityID");
			OriginalOrder o_160_1mio_sysT2 = new OriginalOrder(System.nanoTime(), System.currentTimeMillis(),symbol, side, 160.1, 1000_000,  "orderID", "clientOrdID", "clientEntityID");
			OriginalOrder o_170_1mio = new OriginalOrder(System.nanoTime(), System.currentTimeMillis(),symbol, side, 170.1, 1000_000,  "orderID", "clientOrdID", "clientEntityID");
			
			
			askBook.add(new ExecutingOrder(o_140_1mio));
			askBook.add(new ExecutingOrder(o_160_1mio_sysT1));
			askBook.add(new ExecutingOrder(o_150_1mio));
			askBook.add(new ExecutingOrder(o_160_1mio_sysT2));
			askBook.add(new ExecutingOrder(o_170_1mio));
		}
		
		TreeMap<Double, Integer> bidBookSnapshot = _exchange.buildOneSideAggOrdBook(3, Side.BID, bidBook);
		for(Double price : bidBookSnapshot.keySet() ){
			int aggQty = bidBookSnapshot.get(price);
			System.out.println(price  + " : " + aggQty);
		}

		TreeMap<Double, Integer> offerBookSnapshot = _exchange.buildOneSideAggOrdBook(5, Side.OFFER, askBook);
		for(Double price : offerBookSnapshot.keySet() ){
			int aggQty = offerBookSnapshot.get(price);
			System.out.println(price  + " : " + aggQty);
		}

	}
}
