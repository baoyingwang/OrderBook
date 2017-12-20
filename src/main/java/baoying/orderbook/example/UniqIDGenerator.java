package baoying.orderbook.example;

import java.util.concurrent.atomic.AtomicLong;

public class UniqIDGenerator {

    private static String _msgIDBase = String.valueOf(System.currentTimeMillis());
    private static AtomicLong _msgIDCounter = new AtomicLong(0);

    static String next(){
        return _msgIDBase+"_"+_msgIDCounter.incrementAndGet();
    }
}
