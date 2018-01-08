package baoying.orderbook;

import com.sun.btrace.annotations.*;

//usage : see https://github.com/btraceio/btrace/wiki
@BTrace(trusted=true)
public class BTracePerformance {


    BTracePerformance(){

    }

    @OnMethod(clazz = "baoying.orderbook.MatchingEngine",
            location=@Location(Kind.RETURN),
            method = "addOrder")
    public static void match_done(@Duration long duration){
        System.out.println("match_done - duration:" + duration+ ", thread:"+Thread.currentThread().getName() );

    }

    @OnMethod(clazz = "baoying.orderbook.OrderBook",
            location=@Location(Kind.RETURN),
            method = "processInputOrder")
    public static void ob_done(@Duration long duration){
        System.out.println("ob_done - duration:" + duration+ ", thread:"+Thread.currentThread().getName() );

    }

    @OnMethod(clazz = "com.google.common.eventbus.EventBus",
            location=@Location(Kind.RETURN),
            method = "post")
    public static void guava_evtbus_post(@Duration long duration){
        System.out.println("guava_evtbus_post - duration:" + duration+ ", thread:"+Thread.currentThread().getName() );

    }

}
