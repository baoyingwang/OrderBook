package baoying.orderbook.core;

import baoying.orderbook.core.CommonMessage.Side;
import baoying.orderbook.core.OrderBook.MEExecutionReportMessageFlag;

public class TradeMessage {

    public static class OriginalOrder{

		/**
		 * price is ignored, if orderType is Market.
         */
		public OriginalOrder(CommonMessage.ExternalSource source,
                             long recvFromClientEpochMS,String symbol, Side side, CommonMessage.OrderType type, double price, int qty,  String orderID,
		        String clientOrdID, String clientEntityID) {

            _source = source;
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

        public OriginalOrder(
                             long recvFromClientEpochMS,String symbol, Side side, CommonMessage.OrderType type, double price, int qty,  String orderID,
                             String clientOrdID, String clientEntityID) {

		    //TODO - high - remove this default
            _source = CommonMessage.ExternalSource.VertxTCP;
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

		public final CommonMessage.ExternalSource _source;
        public final String _symbol;

        public final Side _side;
		public final CommonMessage.OrderType _type;
        public final double _price; //required for LIMIT order
        public final int _qty;

		public final long _recvFromClientEpochMS;
        public final String _orderID;
        public final String _clientOrdID;
        public final String _clientEntityID; // to avoid execution with himself

		public boolean _isLatencyTestOrder= false;
		public String _latencyTimesFromClient="";
		public long _recvFromClient_sysNano_test=-1;
	}

	//https://stackoverflow.com/questions/8157755/how-to-convert-enum-value-to-int
    //http://www.onixs.biz/fix-dictionary/4.4/tagNum_150.html
    public enum ExecutionType {

		NEW('0'),CANCELLED('4'), REJECTED('8'),
        TRADE('F'); //F = Trade (partial fill or fill)

		private final char _fix150ExecType;

		private ExecutionType(char fix150ExecType){
			this._fix150ExecType = fix150ExecType;
		}

		public char getFIX150Type(){
			return _fix150ExecType;
		}
	}

    //http://www.onixs.biz/fix-dictionary/5.0.SP1/tagNum_39.html
    public enum OrderStatus{

        NEW('0'),
        PARTIALLY_FILLED('1'),
        FILLED('2'),
        CANCELLED('4'),
        REJECTED('8');

        private final char _fix39OrdStatus;

        private OrderStatus(char fix39OrdStatus){
            this._fix39OrdStatus = fix39OrdStatus;
        }

        public char getFIX39OrdStatus(){
            return _fix39OrdStatus;
        }
    }
	// maker: who sit in the book
    public static class SingleSideExecutionReport implements MEExecutionReportMessageFlag{

        public final long _msgID;
		public final long _msgEpochMS;
        public final ExecutionType _type; //http://www.onixs.biz/fix-dictionary/5.0.SP1/tagNum_150.html
        public final OriginalOrder _originOrder;
        public final int _leavesQty;
        public final String _description;

        public SingleSideExecutionReport(long msgID, long msgEpochMS, OriginalOrder originOrder, ExecutionType type, int leavesQty, String description){
			_msgID = msgID;
			_msgEpochMS = msgEpochMS;
			_originOrder = originOrder;
			_type = type;
			_leavesQty = leavesQty;
			_description = description;
		}
	}
	// maker: who sit in the book
    public static class MatchedExecutionReport implements MEExecutionReportMessageFlag{

        public final long _matchID;
		public final long _matchingEpochMS;

        public final double _lastPrice;
        public final int _lastQty;

        public final OriginalOrder _makerOriginOrder;
        public final OriginalOrder _takerOriginOrder;

        public final int _makerLeavesQty;
        public final int _takerLeavesQty;

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

		}
	}

}
