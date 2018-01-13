package baoying.orderbook.testtool;

import com.beust.jcommander.Parameter;

import java.util.ArrayList;
import java.util.List;

public class TestToolArgs {
    @Parameter
    private List<String> parameters = new ArrayList<>();

    @Parameter(names =  "-clientNum", description = "number of clients")
    public int numOfClients = 1;

    @Parameter(names =  "-ratePerMinute", description = "rate of sending - per minute for overall(clients as a whole). ")
    public int ratePerMinute = 10;

    @Parameter(names =  "-client_prefix", description = "client compid prefix, e.g. LTC$$_FIX_, BACKGROUD_FIX_")
    public String clientCompIDPrefix = "BACKGROUND_FIX_";

    @Parameter(names =  "-symbol", description = "symbol")
    public String symbol = "USDJPY";

    @Parameter(names =  "-side", description = "side, Bid or  Offer")
    public String side = "Bid";

    @Parameter(names = "-qty", description = "quantity of base ccy")
    public String qty="100";

    @Parameter(names = "-ordType", description = "order type - Market, or Limit")
    public String orderType="Limit";

    @Parameter(names = "-px", description = "required for Limit order")
    public String px = "112";

    @Parameter(names =  "-d", description = "duration in second, after the X second, shutdown")
    public int durationInSecond = 60;
}