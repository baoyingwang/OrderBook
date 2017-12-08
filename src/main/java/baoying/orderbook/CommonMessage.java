package baoying.orderbook;

public interface CommonMessage {

	// buy or sell the base ccy
	static enum Side {
		BID, OFFER;
	}

	//https://www.onixs.biz/fix-dictionary/4.4/tagNum_40.html
	static enum OrderType {
		MARKET, LIMIT;
	}
	

}
