package baoying.orderbook.testtool;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;

public class ScheduleSender {

    private final static Logger log = LoggerFactory.getLogger(ScheduleSender.class);

    public void execut(int ratePerMinute, Runnable task){
        final int period;
        final TimeUnit unit;
        final int msgNumPerPeriod ;
        {
            if(ratePerMinute <= 60){

                period = 60 / ratePerMinute;
                unit = TimeUnit.SECONDS;
                msgNumPerPeriod = 1;

            }else if(ratePerMinute <= 60 *2) {

                period = 1;
                unit = TimeUnit.SECONDS;
                msgNumPerPeriod = (int)Math.round(0.49 + ratePerMinute/60);

            }else if(ratePerMinute <= 60 * 10) {
                //2 intervals per second
                period = 500;
                unit = TimeUnit.MILLISECONDS;
                msgNumPerPeriod = (int)Math.round(0.49 + ratePerMinute/(60 * 2));

            }else if(ratePerMinute <= 60 *50) {
                //10 intervals
                period = 100;
                unit = TimeUnit.MILLISECONDS;
                msgNumPerPeriod = (int)Math.round(0.49 + ratePerMinute/(60 * 10));

            }else if(ratePerMinute <= 60 *100) {

                //20 intervals
                period = 50;
                unit = TimeUnit.MILLISECONDS;
                msgNumPerPeriod = (int)Math.round(0.49 + ratePerMinute/(60 * 20));

            }else if(ratePerMinute <= 60 *200) {

                //50 intervals
                period = 20;
                unit = TimeUnit.MILLISECONDS;
                msgNumPerPeriod = (int)Math.round(0.49 + ratePerMinute/(60 * 50));

            }else{
                //100 intervals
                period = 10;
                unit = TimeUnit.MILLISECONDS;
                msgNumPerPeriod = (int)Math.round(0.49 + ratePerMinute/(60 * 100));            }
        }

        ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();
        AtomicInteger totalSent = new AtomicInteger(0);

        Runnable command = new Runnable() {
            @Override
            public void run(){
                try {
                    IntStream.range(0, msgNumPerPeriod).forEach(it -> {

                        try {
                            task.run();
                        } catch (Exception e) {
                            log.error("exception while sending",e);
                        }

                    });

                }catch (Exception e){
                    log.error("exception while schedule sending",e);
                }
            }
        };
        long initialDelay = 0;
        executor.scheduleAtFixedRate( command,  initialDelay, period,   unit);

    }

}
