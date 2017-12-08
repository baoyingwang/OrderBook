package baoying.orderbook;

import java.util.TreeMap;

import baoying.orderbook.CommonMessage.Side;
import baoying.orderbook.MatchingEngine.MatchingEnginOutputMessageFlag;
import baoying.orderbook.MatchingEngine.MatchingEngineInputMessageFlag;

public class MarketDataMessage {
	
	public static class OrderBookDelta implements MatchingEnginOutputMessageFlag {

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

	public static class AggregatedOrderBook implements MatchingEnginOutputMessageFlag{

		long _msgID;

		final String _symbol;
		final int _depth;
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

	public static class AggregatedOrderBookRequest implements MatchingEngineInputMessageFlag{
		String _requestID;
		int _depth;
		public AggregatedOrderBookRequest(String requestID, int depth){
			_requestID = requestID;
			_depth = depth;
		}
	}
}
