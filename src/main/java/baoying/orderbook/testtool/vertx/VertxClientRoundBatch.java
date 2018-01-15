package baoying.orderbook.testtool.vertx;

import baoying.orderbook.app.MatchingEngineApp;
import baoying.orderbook.app.MatchingEngineVertxWrapper;
import baoying.orderbook.app.Util;
import baoying.orderbook.testtool.FIXMessageUtil;
import baoying.orderbook.testtool.ScheduleSender;
import baoying.orderbook.testtool.TestToolArgs;
import com.beust.jcommander.JCommander;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.net.NetClient;
import io.vertx.core.net.NetClientOptions;
import io.vertx.core.net.NetSocket;
import io.vertx.core.parsetools.RecordParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import quickfix.DataDictionary;
import quickfix.Message;

import java.io.BufferedOutputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;

import static java.nio.file.StandardOpenOption.APPEND;
import static java.nio.file.StandardOpenOption.CREATE;

//This is a simplified version.   
//For production code, please read common.DefaultQFJSingleSessionInitiator.
public class VertxClientRoundBatch {

    private final static Logger log = LoggerFactory.getLogger(VertxClientRoundBatch.class);

    private final Vertx _vertx = Vertx.vertx();

    private final Map<String, NetSocket> sockets = new HashMap<>();
    private final AtomicInteger totalSent = new AtomicInteger(0);
    private Instant start = null;
    private final String _clientCompIDPrefix;
    private String _latencyDataFile ;

    private final ExecutorService _fixERResult = Executors.newFixedThreadPool(4,new ThreadFactory() {
        @Override
        public Thread newThread(Runnable r) {
            return new Thread(r, "Thread - testtool processes ER result");
        }
    });

    static int bufferSize = 10*1024*1024;
    static BufferedOutputStream output = null;

    void printCurrentStatistics(){

        int totalSentNow = totalSent.get();

        Instant now = Instant.now();
        long durationInSecond = now.getEpochSecond() - start.getEpochSecond();
        double rateInSecond = totalSentNow*1.0 / durationInSecond;
        double rateInMinute = rateInSecond * 60;

        log.info("{} - totalSent:{}, rateInSecond:{}", _clientCompIDPrefix, totalSentNow, String.format("%.2f", rateInSecond));
    }

    private  final int _vertx_tcp_port;
    VertxClientRoundBatch(String clientCompIDPrefix, int vertx_tcp_port){
        _clientCompIDPrefix = clientCompIDPrefix;
        _vertx_tcp_port = vertx_tcp_port;
    }

    public void execute( String symbol,
                            String price,
                            String qty ,
                            String ordType,
                            String side,

                            int fixClientNum,
                            int ratePerMinute  ) throws  Exception{

        _latencyDataFile = "log/e2e_"+_clientCompIDPrefix+".csv";
        Path e2eTimeFile = Paths.get(_latencyDataFile);
        if (!Files.exists(e2eTimeFile)) {
            Files.write(e2eTimeFile, ("sendTime,clientSendNano,svrRecvOrdNano,svrMatchedNano,clientRecvER,clientOrdID" + "\n").getBytes(), APPEND, CREATE);
        }
        try {
            output = new BufferedOutputStream(
                    new FileOutputStream(_latencyDataFile, true),
                    bufferSize
            );
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }


        ScheduleSender sender = new ScheduleSender();
        ScheduleSender.BatchConfig c = sender.getBatchConfig(ratePerMinute);
        log.info("testool - {} - schedule config:{}",_clientCompIDPrefix, c);

        ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();

        List<String> clientIDs = new ArrayList<>();
        {
            IntStream.range(0, fixClientNum).forEach(it -> clientIDs.add(_clientCompIDPrefix + String.valueOf(it)));
        }

        NetClientOptions options = new NetClientOptions().setConnectTimeout(10000).setTcpNoDelay(true);
        AtomicInteger completedInCurrentPeriodCounter = new AtomicInteger(0);
        final AtomicInteger connectedClients = new AtomicInteger(0);
        for(String clientID: clientIDs){

            NetClient client = _vertx.createNetClient(options);
            client.connect(_vertx_tcp_port, "localhost", res -> {
                if (res.succeeded()) {
                    log.info("{} - vertx client connected on port:{}", _clientCompIDPrefix, _vertx_tcp_port);

                    NetSocket socket = res.result();

                    sockets.put(clientID, socket);

                    Message logon = FIXMessageUtil.buildLogon(clientID);
                    Buffer logonAsBuffer = Util.buildBuffer(logon, MatchingEngineVertxWrapper.vertxTCPDelimiter);
                    socket.write(logonAsBuffer);

                    //http://vertx.io/docs/vertx-core/java/#_record_parser
                    final RecordParser parser = RecordParser.newDelimited(MatchingEngineVertxWrapper.vertxTCPDelimiter, buffer -> {

                        int completedInCurrentPeriod = completedInCurrentPeriodCounter.incrementAndGet();
                        long erTimeNano = System.nanoTime();

                        int length = buffer.getInt(0);
                        String erString = buffer.getString(4, length+4);
                        log.debug("test tool received fix:{}",erString);

                        //TODO add SOH in the index
                        if(erString.indexOf("56="+MatchingEngineApp.LATENCY_ENTITY_PREFIX) > 0){
                            _fixERResult.submit(()->{
                                writeLatencyData(erString, erTimeNano);
                            });
                        }

                        if(completedInCurrentPeriod < c._msgNumPerPeriod) {
                            int nextClientCompIDIndex = totalSent.get() % fixClientNum;
                            String clientCompID = clientIDs.get(nextClientCompIDIndex);
                            Buffer orderAsBuffer = buildOrderBuffer(clientCompID, clientCompID + System.nanoTime(), symbol, price, qty, ordType, side);
                            socket.write(orderAsBuffer);
                            totalSent.incrementAndGet();
                        }else{
                            completedInCurrentPeriodCounter.set(0);
                        }


                    });

                    socket.handler(buffer -> {
                        parser.handle(buffer);
                    });

                    String clientCompID = clientIDs.get(0);
                    Buffer orderAsBuffer = buildOrderBuffer(clientCompID,
                            clientCompID+System.nanoTime(),
                            symbol,
                            price,
                            qty,
                            ordType,
                            side);
                    socket.write(orderAsBuffer);


                    connectedClients.incrementAndGet();


                } else {
                    log.error("{} - Failed to connect, reason:{} ", _clientCompIDPrefix, res.cause().getMessage());
                    System.exit(-1);
                }
            });
        }

        while(true){
            if(connectedClients.get() < clientIDs.size()){
                System.out.println(_clientCompIDPrefix+ " wait all connections ready");
                TimeUnit.SECONDS.sleep(3);
            }else{
                break;
            }

        }

        log.info("{} all connections ready", _clientCompIDPrefix);


        Runnable command = ()-> {
                try {
                    int nextClientCompIDIndex = totalSent.get() % fixClientNum;
                    String clientCompID = clientIDs.get(nextClientCompIDIndex);
                    Buffer orderAsBuffer = buildOrderBuffer(clientCompID,clientCompID+System.nanoTime(),symbol,price,qty,ordType,side);
                    NetSocket socket = sockets.get(clientCompID);
                    socket.write(orderAsBuffer);
                    totalSent.incrementAndGet();

                } catch (Exception e) {
                    log.error("exception while sending",e);
                }

        };
        long initialDelay = 0;
        executor.scheduleAtFixedRate( command,  initialDelay, c._period,   c._unit);

        start = Instant.now();
        _vertx.setPeriodic(30 * 1000, (v)->{
            printCurrentStatistics();
        });

    }

    Buffer buildOrderBuffer(String clientCompID,
                            String clientOrdID,
                            String symbol,
                            String price,
                            String qty,
                            String ordType,
                            String side){
        Message order = FIXMessageUtil.buildNewOrderSingle(clientCompID,clientOrdID,symbol,price,qty,ordType,side);

        if(clientCompID.startsWith(MatchingEngineApp.LATENCY_ENTITY_PREFIX)){
            FIXMessageUtil.addLatencyText(order);
        }

        Buffer orderAsBuffer = Util.buildBuffer(order, MatchingEngineVertxWrapper.vertxTCPDelimiter);
        return orderAsBuffer;
    }

    void writeLatencyData(String erString, long erTimeNano){

        try {

            DataDictionary dd = null;

            dd = new DataDictionary("FIX50SP1.xml");

            boolean doValidation = false;
            Message er = new Message();
            er.fromString(erString,dd,doValidation);

            String clientCompID = er.getHeader().getString(56);
            if(clientCompID.startsWith(MatchingEngineApp.LATENCY_ENTITY_PREFIX)){
                FIXMessageUtil.recordLetencyTimeStamps(er, erTimeNano, output);
            }


        } catch (Exception e) {
            log.error("", e);
        }

    }


    public static void main(String[] args) throws Exception {

        TestToolArgs testToolArgsO = new TestToolArgs();
        JCommander.newBuilder()
                .addObject(testToolArgsO)
                .build()
                .parse(args);
        String symbol = testToolArgsO.symbol;
        String price = testToolArgsO.px;
        String qty = testToolArgsO.qty;
        String ordType = testToolArgsO.orderType; //Market or Limit
        String side = testToolArgsO.side;//Bid or Offer

        String clientCompIDPrefix = testToolArgsO.clientCompIDPrefix +"_"+System.currentTimeMillis() + "_";
        int clientNum = testToolArgsO.numOfClients;
        int ratePerMinute = testToolArgsO.ratePerMinute;

        log.info("testtool -{}, clientNum:{}, ratePerMin:{}, symbol:{}, px:{}, qty:{}, ordType:{},side:{}",
                clientCompIDPrefix,clientNum,ratePerMinute,symbol, price, qty, ordType, side);

        VertxClientRoundBatch vertxClientBatch = new VertxClientRoundBatch(clientCompIDPrefix, testToolArgsO.vertx_tcp_port);

        vertxClientBatch.execute( symbol,price,qty ,ordType, side   ,clientNum,ratePerMinute);



        TimeUnit.SECONDS.sleep(testToolArgsO.durationInSecond);
        output.flush();
        output.close();

        TimeUnit.SECONDS.sleep(2);
        log.info("exiting, since reached duration limit:"+ testToolArgsO.durationInSecond+" seconds");
        System.exit(0);
    }
}
