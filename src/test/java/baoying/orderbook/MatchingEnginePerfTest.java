package baoying.orderbook;

import baoying.orderbook.CommonMessage.Side;
import baoying.orderbook.MarketDataMessage.OrderBookDelta;
import baoying.orderbook.MatchingEngine.ExecutingOrder;
import baoying.orderbook.MatchingEngine.MatchingEnginOutputMessageFlag;
import baoying.orderbook.TradeMessage.MatchedExecutionReport;
import baoying.orderbook.TradeMessage.OriginalOrder;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.PriorityQueue;
import java.util.TreeMap;

public class MatchingEnginePerfTest {

	MatchingEngine _exchange = new MatchingEngine("USDJPY");

	PriorityQueue<ExecutingOrder> getInitialBidBook() {
		String symbol = "USDJPY";
		Side bid = Side.BID;

		PriorityQueue<ExecutingOrder> bidBook = _exchange.createBidBook();
		{
			OriginalOrder b_100_1mio = new OriginalOrder(System.nanoTime(), System.currentTimeMillis(), symbol, bid, 100.1, 1000_000, "orderID", "clientOrdID", "clientEntityID");
			OriginalOrder b_120_1mio = new OriginalOrder(System.nanoTime(), System.currentTimeMillis(), symbol, bid, 120.1, 1000_000, "orderID", "clientOrdID", "clientEntityID");
			OriginalOrder b_130_1mio_sysT1 = new OriginalOrder(System.nanoTime(), System.currentTimeMillis(), symbol, bid, 130.1, 1000_000, "orderID", "clientOrdID", "clientEntityID");
			OriginalOrder b_130_1mio_sysT2 = new OriginalOrder(System.nanoTime(), System.currentTimeMillis(), symbol, bid, 130.1, 1000_000, "orderID", "clientOrdID", "clientEntityID");
			bidBook.add(new ExecutingOrder(b_100_1mio));
			bidBook.add(new ExecutingOrder(b_130_1mio_sysT1));
			bidBook.add(new ExecutingOrder(b_120_1mio));
			bidBook.add(new ExecutingOrder(b_130_1mio_sysT2));
		}

		return bidBook;
	}

	PriorityQueue<ExecutingOrder> getInitialOfferBook() {
		String symbol = "USDJPY";
		Side bid = Side.BID;
		Side offer = Side.OFFER;

		PriorityQueue<ExecutingOrder> offerBook = _exchange.createAskBook();
		{
			OriginalOrder o_140_1mio = new OriginalOrder(System.nanoTime(), System.currentTimeMillis(), symbol, offer, 140.1, 1000_000, "orderID", "clientOrdID", "clientEntityID");
			OriginalOrder o_150_1mio = new OriginalOrder(System.nanoTime(), System.currentTimeMillis(), symbol, offer, 150.1, 1000_000, "orderID", "clientOrdID", "clientEntityID");
			OriginalOrder o_160_1mio_sysT1 = new OriginalOrder(System.nanoTime(), System.currentTimeMillis(), symbol, offer, 160.1, 1000_000, "orderID", "clientOrdID", "clientEntityID");
			OriginalOrder o_160_1mio_sysT2 = new OriginalOrder(System.nanoTime(), System.currentTimeMillis(), symbol, offer, 160.1, 1000_000, "orderID", "clientOrdID", "clientEntityID");
			offerBook.add(new ExecutingOrder(o_140_1mio));
			offerBook.add(new ExecutingOrder(o_150_1mio));
			offerBook.add(new ExecutingOrder(o_160_1mio_sysT1));
			offerBook.add(new ExecutingOrder(o_160_1mio_sysT2));
		}

		return offerBook;

	}

	@Test
	public void testMatchPerformance(){

		String symbol = "USDJPY";
		Side bid = Side.BID;

		PriorityQueue<ExecutingOrder> bidBook = getInitialBidBook();
		PriorityQueue<ExecutingOrder> offerBook = getInitialOfferBook();

		OriginalOrder b_160_1mio = new OriginalOrder(System.nanoTime(), System.currentTimeMillis(),symbol, bid, 160.1, 2,  "orderID", "clientOrdID", "clientEntityID_latnecy");

		int loopCount = 1000_000;
 		List<MatchingEnginOutputMessageFlag> execReportsAsResult = new ArrayList<>();
		List<OrderBookDelta> orderbookDeltasAsResult = new ArrayList<>();
		long start = System.nanoTime();
		for(int i=0; i<loopCount; i++ ){
			//_exchange.match(new ExecutingOrder(b_160_1mio,System.nanoTime()),offerBook,bidBook,execReportsAsResult, orderbookDeltasAsResult);
			System.nanoTime();
		}
		long end = System.nanoTime();
		System.out.println("avg nano second per matching:" + (end - start)/loopCount);

	}

}
