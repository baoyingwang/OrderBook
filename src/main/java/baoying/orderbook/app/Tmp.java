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


    public static void main(String[] args) throws Exception{

        int[] packagesCircle = new int[]{1,1,1,20,1,1,20};
        System.out.println(getMaxPackage(packagesCircle));


    }

    public static int getMaxPackage(int[] packagesCircle){

        if(packagesCircle.length == 0){
            return 0;
        }

        if(packagesCircle.length == 1){
            return packagesCircle[0];
        }

        if(packagesCircle.length == 2){
            return Math.max(packagesCircle[0],packagesCircle[1]);
        }

        int case1result_HeadNotTaken = getMax(packagesCircle, 1, packagesCircle.length-1);
        int case2result_HeadTaken = packagesCircle[0] + getMax(packagesCircle, 2, packagesCircle.length-2);

        return Math.max(case1result_HeadNotTaken,case2result_HeadTaken);
    }

    public static int getMax(int[] packageNoCircle, int start, int end_inclusive){

        if(start > end_inclusive){
            return 0;
        }

        return Math.max(
                packageNoCircle[start] + getMax(packageNoCircle, start + 2, end_inclusive),
                getMax(packageNoCircle, start + 1, end_inclusive)
        );
    }
}
