package baoying.orderbook.app;


import quickfix.DataDictionary;
import quickfix.DefaultMessageFactory;
import quickfix.Message;
import quickfix.MessageFactory;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;

public class Tmp {

    public static void main(String[] args) throws Exception{


        final MessageFactory messageFactory = new DefaultMessageFactory();
        final DataDictionary dataDictionary = new DataDictionary("FIX50SP1.xml");

        final String messageString1;
        final String messageString2;
        {
            String x1 ="8=FIXT.1.1%9=215%35=8%34=2%49=BaoyingMatchingCompID%52=20171229-02:54:07.319%56=BACKGROUND_FIX1514516040861_0%11=BACKGROUND_FIX1514516040861_01514516047312_1%14=0%17=2437234712035%37=1514515971769_42%39=0%54=2%55=USDJPY%150=0%151=2%10=075%";
            String x2 ="8=FIXT.1.1%9=215%35=8%34=2%49=BaoyingMatchingCompID%52=20171229-02:54:07.319%56=BACKGROUND_FIX1514516040861_0%11=BACKGROUND_FIX1514516040861_01514516047312_1%14=0%17=2437234712035%37=1514515971769_42%39=0%54=2%55=USDJPY%150=0%151=2%10=075%";
            messageString1 = x1.replace('%', (char) 0x01);
            messageString2 = x2.replace('%', (char) 0x01);
        }
        ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();
        final Message msg1 = quickfix.MessageUtils.parse(messageFactory, dataDictionary, messageString1);
        final Message msg2 = quickfix.MessageUtils.parse(messageFactory, dataDictionary, messageString2);
        Runnable command = new Runnable() {
            @Override
            public void run() {
                try {
                    for (int i = 0; i < 5; i++) {
                       String x1 = msg1.toString();
                       byte[] y = x1.getBytes();
                    }
                }catch (Exception e){
                    e.printStackTrace();
                }

            }
        };
        executor.scheduleAtFixedRate( command,  1, 1, TimeUnit.MILLISECONDS);

    }
}
