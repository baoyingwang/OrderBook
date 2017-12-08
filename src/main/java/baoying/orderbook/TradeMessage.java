package baoying.orderbook;

import baoying.orderbook.CommonMessage.Side;
import baoying.orderbook.MatchingEngine.MatchingEnginOutputMessageFlag;
import baoying.orderbook.MatchingEngine.MatchingEngineInputMessageFlag;

public class TradeMessage {
    public static class OriginalOrder implements MatchingEngineInputMessageFlag {

		public OriginalOrder(String symbol, Side side, double price, int qty,  String orderID,
		        String clientOrdID, String clientEntityID) {

			_symbol = symbol;
			_side = side;
			_price = price;
			_qty = qty;
			_orderID = orderID;
			_clientOrdID = clientOrdID;
			_clientEntityID = clientEntityID;
		}

		void setEnterEngineSysNanoTime(long enteringSystemNanoTime){
			_enteringEngineSysNanoTime = enteringSystemNanoTime;
		}

        public final String _symbol;

        public final Side _side;
		//final OrderType _type;
        public final double _price; //required for LIMIT order
        public final int _qty;
        public long _enteringEngineSysNanoTime;
        public final String _orderID;
        public final String _clientOrdID;
        public final String _clientEntityID; // to avoid execution with himself
	}

    public enum SingleSideExecutionType{

		PENDING_NEW,CANCELLED, REJECTED;
	}
	// maker: who sit in the book
    public static class SingleSideExecutionReport implements MatchingEnginOutputMessageFlag{

        public final long _msgID;
        public final SingleSideExecutionType _type;
        public final OriginalOrder _originOrder;
        public final int _leavesQty;
        public final String _description;

        public SingleSideExecutionReport(long msgID, OriginalOrder originOrder, SingleSideExecutionType type, int leavesQty, String description){
			_msgID = msgID;
			_originOrder = originOrder;
			_type = type;
			_leavesQty = leavesQty;
			_description = description;
		}
	}
	// maker: who sit in the book
    public static class MatchedExecutionReport implements MatchingEnginOutputMessageFlag{

        public final long _matchID;

        public final double _lastPrice;
        public final int _lastQty;

        public final OriginalOrder _makerOriginOrder;
        public final OriginalOrder _takerOriginOrder;

        public final int _makerLeavesQty;
        public final int _takerLeavesQty;

		public MatchedExecutionReport(long matchID, double lastPrice, int lastQty, OriginalOrder makerOriginOrder, int makerLeavesQty,
		        OriginalOrder takerOriginOrder, int takerLeavesQty) {

			_matchID = matchID;
			_lastPrice = lastPrice;
			_lastQty = lastQty;

			_makerOriginOrder = makerOriginOrder;
			_takerOriginOrder = takerOriginOrder;
			_makerLeavesQty = makerLeavesQty;
			_takerLeavesQty = takerLeavesQty;
		}

	}

}
