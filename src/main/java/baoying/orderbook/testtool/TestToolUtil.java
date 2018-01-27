package baoying.orderbook.testtool;

import baoying.orderbook.app.UniqIDGenerator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import quickfix.ConfigError;
import quickfix.DataDictionary;
import quickfix.Message;

import java.io.BufferedOutputStream;
import java.io.FileOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;

import static java.nio.file.StandardOpenOption.APPEND;
import static java.nio.file.StandardOpenOption.CREATE;

public class TestToolUtil {

    private final static Logger log = LoggerFactory.getLogger(TestToolUtil.class);

    public static double getCurrentRateInSecond(Instant start, AtomicInteger totalSent){

        int totalSentNow = totalSent.get();

        Instant now = Instant.now();
        long durationInSecond = now.getEpochSecond() - start.getEpochSecond();
        double rateInSecond = totalSentNow*1.0 / durationInSecond;
        //double rateInMinute = rateInSecond * 60;

        return rateInSecond;
        //log.info("{} - totalSent:{}, rateInSecond:{}", _testToolArgs.clientCompIDPrefix, totalSentNow, String.format("%.2f", rateInSecond));
    }

    public static BufferedOutputStream setupOutputLatencyFile(String latencyDataFile, int bufferSize)throws Exception{

        Path e2eTimeFile = Paths.get(latencyDataFile);
        if (!Files.exists(e2eTimeFile)) {
            Files.write(e2eTimeFile, ("sendTime,clientSendNano,svrRecvOrdNano,svrMatchedNano,clientRecvER,clientOrdID" + "\n").getBytes(), APPEND, CREATE);
        }

        BufferedOutputStream output = new BufferedOutputStream(
                new FileOutputStream(latencyDataFile, true),
                bufferSize
        );

        return output;

    }

    public static String getLantecyRecord(Message er, long erTimeNano){

        String latencyRecord = "";
        try {
            String clientOrdID = er.getString(11);
            String serverTimes = er.getString(FIXMessageUtil.latencyTimesField);

            latencyRecord = serverTimes + "," + erTimeNano + "," + clientOrdID + "\n";
        }catch (Exception e){
            log.error("exception on:" + er.toString(), e);
            return "";
        }

        return latencyRecord;

    }

    static DataDictionary dd50sp1 ;
    static{
        try {
            dd50sp1= new DataDictionary("FIX50SP1.xml");
        } catch (ConfigError configError) {
            configError.printStackTrace();
        }
    }
    static boolean fixMsgDoValidation = false;


    public static String getLantecyRecord(String fixER, long erTimeNano){

        String latencyRecord = "";
        try {
            Message er = FIXMessageUtil.toMessage(fixER, dd50sp1, fixMsgDoValidation);
            if (!(er.getChar(150) == 'F')) {
                log.error("cannot record lantency for non fill(partial or full fill) execution report");
                return "";
            }

            String clientOrdID = er.getString(11);
            String serverTimes = er.getString(FIXMessageUtil.latencyTimesField);

            latencyRecord = serverTimes + "," + erTimeNano + "," + clientOrdID + "\n";
        }catch (Exception e){
            log.error("exception on:" + fixER, e);
            return "";
        }

        return latencyRecord;

    }

    public static List<String> generateClientList(final String clientCompIDPrefix, final int numOfClients){
        List<String> clientIDs = new ArrayList<>();
        {
            IntStream.range(0, numOfClients).forEach(it ->{
                String clientEntityID = clientCompIDPrefix +"_"+ UniqIDGenerator.next()+"_"+it;
                clientIDs.add(clientEntityID);
            });
        }

        return clientIDs;
    }

    public static List<OrderBrief> getOrderBriefList(TestToolArgs testToolArgs, List<String> clientIDs){

        List<String> localSides = new ArrayList<>();
        if(testToolArgs.sides.size()>0){
            localSides.addAll(testToolArgs.sides);
        }else{
            localSides.add(testToolArgs.side);
        }

        List<String> localPrices = new ArrayList<>();
        if(testToolArgs.prices.size()>0){
            localPrices.addAll(testToolArgs.prices);
        }else{
            localPrices.add(testToolArgs.px);
        }


        List<OrderBrief> result= new ArrayList<>();
        for(String side : localSides){
            for(String px : localPrices){
                for(String clientEntity : clientIDs){

                    result.add(new OrderBrief(clientEntity,
                            testToolArgs.symbol,
                            side,
                            testToolArgs.qty,
                            testToolArgs.orderType,
                            px));
                }
            }
        }

        return result;
    }

    public static class OrderBrief{
        public final String _clientEntity;
        public final String _symbol;
        public final String _side;
        public final String _qty;
        public final String _ordType;
        public final String _px;
        OrderBrief(
                String clientEntity,
                String symbol,
                String side,
                String qty,
                String ordType,
                String px){
            _clientEntity=clientEntity;
            _symbol=symbol;
            _side=side;
            _qty=qty;
            _ordType=ordType;
            _px=px;

        }
    }
}
