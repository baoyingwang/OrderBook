package baoying.orderbook.testtool;

import baoying.orderbook.app.MatchingEngineFIXWrapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import quickfix.*;

import java.io.ByteArrayInputStream;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;

public class FirstQFJClientBackground {

    private final static Logger log = LoggerFactory.getLogger(FirstQFJClientBackground.class);

	public static void main(String[] args) throws Exception {


        String symbol="USDJPY";
        String price = "112";
        String qty = "200";
        String ordType="Market"; //Market or Limit
        String side="Bid";//Bid or Offer

        String clientCompIDPrefix="BACKGROUND_FIX_";
        int fixClientNum = 5;
        int ratePerMinute = 500 * 60;

        FirstQFJClientBatch firstQFJClientBatch = new FirstQFJClientBatch();

        firstQFJClientBatch.execute( symbol,price,qty ,ordType,"Bid"   ,clientCompIDPrefix+"Bid_"  ,fixClientNum,ratePerMinute);
        firstQFJClientBatch.execute( symbol,price,qty ,ordType,"Offer",clientCompIDPrefix+"Offer_",fixClientNum,ratePerMinute);
	}


}
