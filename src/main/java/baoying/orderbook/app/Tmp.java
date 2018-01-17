package baoying.orderbook.app;


import baoying.orderbook.testtool.vertx.VertxClientRoundBatch;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import quickfix.DataDictionary;
import quickfix.DefaultMessageFactory;
import quickfix.Message;
import quickfix.MessageFactory;

import java.io.BufferedOutputStream;
import java.io.DataOutputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;

public class Tmp {
    private final static Logger log = LoggerFactory.getLogger(Tmp.class);

    public static void main(String[] args) throws Exception{

        String msgString ="8=FIXT.1.1|9=655|35=AE|34=8|1128=8|49=RTNSFIXUAT|56=HANWEIRECFIXUAT|52=20180117-06:43:59|571=17516430|150=F|570=N|460=4|167=OPT|60=20180117-12:09:47|75=20180117|55=EUR/USD|552=1|54=1|1057=N|37=20180117-5a_pips|453=5|448=HANW|452=3|447=D|802=1|523=HANWEI MAKER Group|803=5|448=MBY2|452=1|447=D|802=1|523=Bank 2|803=5|448=admin|452=44|447=D|448=HWEI|452=16|447=D|802=1|523=HWEI LTD|803=5|448=HW1|452=11|447=D|555=1|624=2|1418=50000|11012=2|1379=9.038|942=EUR|600=EUR/USD|1358=1|9126=1|588=20180124|687=71.9|566=0|11013=3|11019=3|11015=20180117|11016=EUR|612=1.24|611=20180122|11014=USNYC|1212=10:00:00|1420=0|670=1|671=UNSPECIFIED|673=50000|11254=62000|32=0.0|31=0.0|38=0.0|10=010|";
        msgString = msgString.replace('|', (char)0x01);

        DataDictionary dd = new DataDictionary("FIX50SP1_TRTN.xml");
        boolean doValidation = true;

        Message m = new Message();
        m.fromString(msgString, dd, doValidation);

        System.out.println(m.toString());


    }
}
