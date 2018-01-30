package baoying.orderbook;

import java.util.TreeMap;

import baoying.orderbook.CommonMessage.Side;
import baoying.orderbook.OrderBook.MDMarketDataMessageFlag;

public class MarketDataMessage {
	
	public static class OrderBookDelta implements MDMarketDataMessageFlag {

		final String _symbol;
		final Side _side;
		final double _px;
		final int _deltaQty_couldNegative;

		public OrderBookDelta(String symbol, Side side, double px, int deltaQty_couldNegative) {
			_symbol = symbol;
			_side = side;
			_px = px;
			_deltaQty_couldNegative = deltaQty_couldNegative;
		}

	}

	public static class AggregatedOrderBook implements MDMarketDataMessageFlag{

		public long _msgID;

		public final String _symbol;
		public final int _depth;
		TreeMap<Double, Integer> _bidBookMap;
		TreeMap<Double, Integer> _offerBookMap;

		public AggregatedOrderBook(String symbol, int depth, long msgID, TreeMap<Double, Integer> bidBookMap, TreeMap<Double, Integer> offerBookMap) {
			_symbol = symbol;
			_depth = depth;
			_msgID = msgID;
			_bidBookMap = bidBookMap;
			_offerBookMap= offerBookMap;
		}

	}

	public static class AggregatedOrderBookRequest{

		final String _requestID;
		final String _symbol;
		final int _depth;
		public AggregatedOrderBookRequest(String requestID,String symbol, int depth){
			_requestID = requestID;
			_symbol = symbol;
			_depth = depth;
		}
	}
}
