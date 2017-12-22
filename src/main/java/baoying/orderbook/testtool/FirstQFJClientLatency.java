package baoying.orderbook.testtool;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class FirstQFJClientLatency {

    private final static Logger log = LoggerFactory.getLogger(FirstQFJClientLatency.class);

	public static void main(String[] args) throws Exception {


        String symbol="USDJPY";
        String price = "112";
        String qty = "200";
        String ordType="Market"; //Market or Limit
        String side="Bid";//Bid or Offer

        String clientCompIDPrefix="LTC$$_FIX_";
        int fixClientNum = 1;
        int ratePerMinute = 10;

        new FirstQFJClientBatch().execute( symbol,
                 price,
                 qty ,
                 ordType,
                 side,

                 clientCompIDPrefix,
         fixClientNum,
         ratePerMinute);

	}


}
