package baoying.orderbook;

import baoying.orderbook.CommonMessage.Side;
import baoying.orderbook.OrderBook.MatchingEnginOutputMessageFlag;
import baoying.orderbook.OrderBook.MatchingEngineInputMessageFlag;

public class TradeMessage {
    public static class OriginalOrder implements MatchingEngineInputMessageFlag {

		/**
		 * price is ignored, if orderType is Market.
         */
		public OriginalOrder(long recvFromClientEpochMS,String symbol, Side side, CommonMessage.OrderType type, double price, int qty,  String orderID,
		        String clientOrdID, String clientEntityID) {
            _recvFromClientEpochMS = recvFromClientEpochMS;
			_symbol = symbol;
			_side = side;
			_type = type;
			_price = price;
			_qty = qty;
			_orderID = orderID;
			_clientOrdID = clientOrdID;
			_clientEntityID = clientEntityID;
		}

        public final String _symbol;

        public final Side _side;
		public final CommonMessage.OrderType _type;
        public final double _price; //required for LIMIT order
        public final int _qty;

		public boolean _isLatencyTestOrder= false;
		public long _recvFromClient_sysNano_test;
		public long _enterInputQ_sysNano_test;

		public final long _recvFromClientEpochMS;
        public final String _orderID;
        public final String _clientOrdID;
        public final String _clientEntityID; // to avoid execution with himself
	}

    public enum SingleSideExecutionType{

		NEW,CANCELLED, REJECTED;
	}
	// maker: who sit in the book
    public static class SingleSideExecutionReport implements MatchingEnginOutputMessageFlag{

        public final long _msgID;
		public final long _msgEpochMS;
        public final SingleSideExecutionType _type;
        public final OriginalOrder _originOrder;
        public final int _leavesQty;
        public final String _description;

        public SingleSideExecutionReport(long msgID,long msgEpochMS, OriginalOrder originOrder, SingleSideExecutionType type, int leavesQty, String description){
			_msgID = msgID;
			_msgEpochMS = msgEpochMS;
			_originOrder = originOrder;
			_type = type;
			_leavesQty = leavesQty;
			_description = description;
		}
	}
	// maker: who sit in the book
    public static class MatchedExecutionReport implements MatchingEnginOutputMessageFlag{

        public final long _matchID;
		public final long _matchingEpochMS;

        public final double _lastPrice;
        public final int _lastQty;

        public final OriginalOrder _makerOriginOrder;
        public final OriginalOrder _takerOriginOrder;

        public final int _makerLeavesQty;
        public final int _takerLeavesQty;

		public final long _matching_sysNano_test;
        public final long _taker_enterInputQ_sysNano_test;
        public final long _taker_pickFromInputQ_sysNano_test;

        public MatchedExecutionReport(long matchID,
                                      long matchingEpochMS,
                                      double lastPrice, int lastQty,
                                      OriginalOrder makerOriginOrder, int makerLeavesQty,
                                      OriginalOrder takerOriginOrder, int takerLeavesQty){
            this(matchID,
             matchingEpochMS,
             lastPrice,  lastQty,
             makerOriginOrder,  makerLeavesQty,
             takerOriginOrder,  takerLeavesQty,
                    -1,-1,-1);
        }
        public MatchedExecutionReport(long matchID,
                                      long matchingEpochMS,
                                      double lastPrice, int lastQty,

                                      OriginalOrder makerOriginOrder, int makerLeavesQty,
                                      OriginalOrder takerOriginOrder, int takerLeavesQty,

                                      long taker_enterInputQ_sysNano_test,
                                      long taker_pickFromInputQ_sysNano_test,
                                      long matchingSysNanoTime
                                      ) {

			_matchID = matchID;

			_matchingEpochMS = matchingEpochMS;

			_lastPrice = lastPrice;
			_lastQty = lastQty;

			_makerOriginOrder = makerOriginOrder;
			_takerOriginOrder = takerOriginOrder;
			_makerLeavesQty = makerLeavesQty;
			_takerLeavesQty = takerLeavesQty;

            _taker_enterInputQ_sysNano_test =taker_enterInputQ_sysNano_test;
            _taker_pickFromInputQ_sysNano_test = taker_pickFromInputQ_sysNano_test;
            _matching_sysNano_test = matchingSysNanoTime;
		}
	}

}
