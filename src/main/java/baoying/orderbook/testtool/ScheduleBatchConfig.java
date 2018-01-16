package baoying.orderbook.testtool;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.TimeUnit;

public class ScheduleBatchConfig {

    private final static Logger log = LoggerFactory.getLogger(ScheduleBatchConfig.class);

    public Config getBatchConfig(int ratePerMinute){
        final Config c;
        final int period;
        final TimeUnit unit;
        final int msgNumPerPeriod ;
            if (ratePerMinute <= 60) {

                period = 60 / ratePerMinute;
                unit = TimeUnit.SECONDS;
                msgNumPerPeriod = 1;
                c = new Config(period,unit, msgNumPerPeriod);

            } else if (ratePerMinute <= 60 * 2) {

                period = 1;
                unit = TimeUnit.SECONDS;
                msgNumPerPeriod = (int) Math.round(0.49 + ratePerMinute / 60* 1.0);
                c = new Config(period,unit, msgNumPerPeriod);

            } else if (ratePerMinute <= 60 * 10) {
                //2 intervals per second
                period = 500;
                unit = TimeUnit.MILLISECONDS;
                msgNumPerPeriod = (int) Math.round(0.49 + ratePerMinute / (60 * 2* 1.0));
                c = new Config(period,unit, msgNumPerPeriod);
            } else if (ratePerMinute <= 60 * 50) {
                //10 intervals
                period = 100;
                unit = TimeUnit.MILLISECONDS;
                msgNumPerPeriod = (int) Math.round(0.49 + ratePerMinute / (60 * 10* 1.0));
                c = new Config(period,unit, msgNumPerPeriod);
            } else if (ratePerMinute <= 60 * 100) {

                //20 intervals
                period = 50;
                unit = TimeUnit.MILLISECONDS;
                msgNumPerPeriod = (int) Math.round(0.49 + ratePerMinute / (60 * 20* 1.0));

                c = new Config(period,unit, msgNumPerPeriod);
            } else if (ratePerMinute <= 60 * 200) {

                //50 intervals
                period = 20;
                unit = TimeUnit.MILLISECONDS;
                msgNumPerPeriod = (int) Math.round(0.49 + ratePerMinute / (60 * 50* 1.0));
                c = new Config(period,unit, msgNumPerPeriod);
            } else if (ratePerMinute <= 60 * 1000) {

                //100 intervals
                period = 10;
                unit = TimeUnit.MILLISECONDS;
                msgNumPerPeriod = (int) Math.round(0.49 + ratePerMinute / (60 * 100* 1.0));
                c = new Config(period,unit, msgNumPerPeriod);
            } else {
                //1000 intervals
                period = 1;
                unit = TimeUnit.MILLISECONDS;
                msgNumPerPeriod = (int) Math.round(0.49 + ratePerMinute / (60 * 1000 * 1.0));
                c = new Config(period,unit, msgNumPerPeriod);
            }

        return c;
    }

    public static class Config {
        public final int _period;
        public final TimeUnit _unit;
        public final int _msgNumPerPeriod ;

        Config(int period,
               TimeUnit unit,
               int msgNumPerPeriod ){
            _period= period;
            _unit = unit;
            _msgNumPerPeriod = msgNumPerPeriod;

        }

        @Override
        public String toString(){
            return "period:"+ _period+" unit:"+_unit.toString()+" msgNumPerPeriod:"+_msgNumPerPeriod;
        }
    }

}
