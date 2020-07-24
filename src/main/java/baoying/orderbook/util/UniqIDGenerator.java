package baoying.orderbook.util;

import java.util.concurrent.atomic.AtomicLong;

public class UniqIDGenerator {

    //TODO 这个长度要进行一下format
    private static String _msgIDBase = String.valueOf(System.currentTimeMillis());
    private static AtomicLong _msgIDCounter = new AtomicLong(0);

    //这个三个字符串相加 jvm默认用string builder方式，不必担心string concat的性能问题
    //只不过，这个id的格式不太好，效率与可以在考虑一下（benchmark一下）
    public static String next(){
        return _msgIDBase+"_"+_msgIDCounter.incrementAndGet();
    }
}
