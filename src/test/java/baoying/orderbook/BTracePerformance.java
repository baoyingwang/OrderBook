package baoying.orderbook;

import com.sun.btrace.annotations.*;

import java.io.*;
import java.util.concurrent.TimeUnit;

//$ ./btrace -u 10472 baoying/orderbook/BTracePerformance.class
//usage : see https://github.com/btraceio/btrace/wiki
@BTrace(trusted=true)
public class BTracePerformance {


    final static int sampleMean = 100;

    static int bufferSize = 2*1024*1024;
    static BufferedOutputStream output;

    static {
        try {
            output = new BufferedOutputStream(
                    new FileOutputStream("log/btrace.csv"),
                    bufferSize
            );
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }
    }

    @OnMethod(clazz = "baoying.orderbook.core.OrderBook",
            location=@Location(Kind.RETURN),
            method = "matchOrder")
    @Sampled(kind = Sampled.Sampler.Const, mean=sampleMean)
    public static void match_ns(@Duration long duration){

        try {
            output.write(("match_ns,"+duration+"\n").getBytes());
        } catch (IOException e) {
            e.printStackTrace();
        }

    }

    @OnMethod(clazz = "baoying.orderbook.core.MatchingEngine",
            location=@Location(Kind.RETURN),
            method = "sendToBus")
    @Sampled(kind = Sampled.Sampler.Const, mean=sampleMean)
    public static void publish2bus_ns(@Duration long duration){

        try {
            output.write(("publish2bus_ns,"+duration+"\n").getBytes());
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @OnMethod(clazz = "baoying.orderbook.core.MatchingEngine",
            location=@Location(Kind.RETURN),
            method = "matchOrder")
    //@Sampled(kind = Sampled.Sampler.Adaptive) //http://btraceio.github.io/btrace/2015/02/sampled-profiling/
    // standard sampling; capture each mean-th invocation
    @Sampled(kind = Sampled.Sampler.Const, mean=sampleMean)
    public static void match_publish2bus_ns(@Duration long duration){

        try {
            output.write(("match_publish2bus_ns,"+duration+"\n").getBytes());
        } catch (IOException e) {
            e.printStackTrace();
        }
    }


    @OnTimer(30000) //in MS
    public static void flush(){
        try {
            output.flush();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }



}
