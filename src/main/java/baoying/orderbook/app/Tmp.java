package baoying.orderbook.app;


import io.vertx.core.Context;
import io.vertx.core.Vertx;

import java.math.BigDecimal;

public class Tmp {



    // Driver method
    public static void main (String[] args) throws java.lang.Exception
    {

        Vertx vertx = Vertx.vertx();
        Context context = vertx.getOrCreateContext();

        context.runOnContext((v)->{
            System.out.println(" thread:" + Thread.currentThread());
        });
        context.runOnContext((v)->{
            System.out.println(" thread:" + Thread.currentThread());
        });
    }


}
