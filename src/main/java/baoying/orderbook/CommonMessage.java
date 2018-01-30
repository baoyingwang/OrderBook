package baoying.orderbook;

public interface CommonMessage {

	enum ExternalSource {
		FIX,
		VertxTCP,
		Web
	}

	// buy or sell the base ccy
	enum Side {
		BID('1'), OFFER('2');

		private  final char _fix54Side;
		private Side(char fix54Side){
			_fix54Side = fix54Side;
		}

		public char getFIX54Side(){
			return _fix54Side;
		}


		public static Side fixValueOf(char fixTag54Side){
			final Side result ;
			switch(fixTag54Side){
				case '1' : result = BID; break;
				case '2' : result = OFFER; break;
				default: throw new RuntimeException("not supported side:"+ fixTag54Side);

			}

			return result;
		}
	}

	//https://www.onixs.biz/fix-dictionary/4.4/tagNum_40.html
	enum OrderType {
		MARKET, LIMIT;

		public static OrderType fixValueOf(char fixTag40OrdType){
			OrderType result ;
			switch(fixTag40OrdType){
				case '1' : result = MARKET; break;
				case '2' : result = LIMIT; break;
				default: throw new RuntimeException("not supported order type:"+ fixTag40OrdType);

			}

			return result;
		}
	}
	

}
