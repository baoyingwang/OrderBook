package baoying.orderbook.core;

import java.util.List;
import java.util.TreeMap;

import baoying.orderbook.core.CommonMessage.Side;
import baoying.orderbook.core.OrderBook.MDMarketDataMessageFlag;

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

	public static class DetailOrderBook implements MDMarketDataMessageFlag{

		public long _msgID;

		public final String _symbol;
		public final int _depth;
		TreeMap<Double, List<MDOrder>> _bidBookMap;
		TreeMap<Double, List<MDOrder>> _offerBookMap;

		public DetailOrderBook(String symbol, int depth, long msgID, TreeMap<Double, List<MDOrder>> bidBookMap, TreeMap<Double, List<MDOrder>> offerBookMap) {
			_symbol = symbol;
			_depth = depth;
			_msgID = msgID;
			_bidBookMap = bidBookMap;
			_offerBookMap= offerBookMap;
		}

		static class MDOrder{
            MDOrder(String clientName, String clientOrdID, int leavesQty, long enteringTimeMS, long internalID){
                _clientName = clientName;
                _clientOrdID = clientOrdID;
                _leavesQty = leavesQty;
                _enteringTimeMS = enteringTimeMS;
                _internalID = internalID;
            }
			final String _clientName;
            final String _clientOrdID;
            final int _leavesQty;
            final long _enteringTimeMS;
            final long _internalID;
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

    public static class DetailOrderBookRequest{

        final String _requestID;
        final String _symbol;
        final int _depth;
        public DetailOrderBookRequest(String requestID,String symbol, int depth){
            _requestID = requestID;
            _symbol = symbol;
            _depth = depth;
        }
    }
}
