package baoying.orderbook.testtool.vertx;

import baoying.orderbook.app.*;
import baoying.orderbook.testtool.*;
import com.beust.jcommander.JCommander;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.net.NetClient;
import io.vertx.core.net.NetClientOptions;
import io.vertx.core.net.NetSocket;
import io.vertx.core.parsetools.RecordParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import quickfix.ConfigError;
import quickfix.DataDictionary;
import quickfix.Message;

import java.io.BufferedOutputStream;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

//This is a simplified version.   
//For production code, please read common.DefaultQFJSingleSessionInitiator.
public class VertxClientRoundBatch {

    private final static Logger log = LoggerFactory.getLogger(VertxClientRoundBatch.class);

    private final Vertx _vertx = Vertx.vertx();

    final TestToolArgs _testToolArgs;
    final List<String> clientIDs;
    final List<TestToolUtil.OrderBrief> _orderInfoList;


    final Map<String, NetSocket> liveSockets = new HashMap<>();


    private final AtomicInteger totalSent = new AtomicInteger(0);
    private final AtomicInteger totalRecv = new AtomicInteger(0);

    private Instant start = null;

    //single thread, since only single output stream.
    private final ExecutorService _latencyWritingExecutor = Executors.newFixedThreadPool(1,new ThreadFactory() {
        @Override
        public Thread newThread(Runnable r) {
            return new Thread(r, "Thread - testtool processes Latency ER result");
        }
    });

    private final String _latencyDataFile ;
    private final int bufferSize = 16*1024*1024;
    private final BufferedOutputStream output;

    VertxClientRoundBatch(TestToolArgs testToolArgs) throws Exception{

        _testToolArgs = testToolArgs;

        clientIDs = TestToolUtil.generateClientList(_testToolArgs);
        _orderInfoList = TestToolUtil.getOrderBriefList(_testToolArgs, clientIDs);


        _latencyDataFile = "log/e2e_"+testToolArgs.clientCompIDPrefix+".csv";
        output = TestToolUtil.setupOutputLatencyFile(_latencyDataFile, bufferSize);
    }

    public void execute() throws  Exception{

        ScheduleBatchConfig sender = new ScheduleBatchConfig();
        ScheduleBatchConfig.Config c = sender.getBatchConfig(_testToolArgs.ratePerMinute);
        log.info("testool - {} - schedule config:{}", _testToolArgs.clientCompIDPrefix, c);



        AtomicInteger completedInCurrentPeriodCounter = new AtomicInteger(0);
        final AtomicInteger connectedClients = new AtomicInteger(0);

        NetClientOptions options = new NetClientOptions().setConnectTimeout(10000).setTcpNoDelay(true);
        clientIDs.forEach( clientID ->{
            NetClient client = _vertx.createNetClient(options);
            client.connect(_testToolArgs.vertx_tcp_port, "localhost", res -> {
                if (res.succeeded()) {

                    log.info("{} - vertx client connected on port:{}", _testToolArgs.clientCompIDPrefix, _testToolArgs.vertx_tcp_port);

                    NetSocket socket = res.result();
                    liveSockets.put(clientID, socket);

                    connectedClients.incrementAndGet();

                } else {
                    log.error("{} - Failed to connect, reason:{} ", _testToolArgs.clientCompIDPrefix, res.cause().getMessage());
                    System.exit(-1);
                }
            });
        });

        while(true){
            if(connectedClients.get() < clientIDs.size()){
                log.info("testtool - {} - wait all connections ready, expect:{}, now:{}", _testToolArgs.clientCompIDPrefix, clientIDs.size(), connectedClients.get());
                TimeUnit.SECONDS.sleep(3);
            }else{
                break;
            }
        }


        AtomicInteger logonAcked = new AtomicInteger(0);
        liveSockets.forEach( (clientID, socket)->{

            //http://vertx.io/docs/vertx-core/java/#_record_parser
            final RecordParser parser = RecordParser.newDelimited(MatchingEngineVertxWrapper.vertxTCPDelimiter, buffer -> {

                long recvTimeNano = System.nanoTime();

                totalRecv.incrementAndGet();
                int length = buffer.getInt(0);
                String fixString = buffer.getString(4, length+4);
                log.debug("test tool received fix:{}",fixString);

                if(fixString.indexOf("\u000135=A\u0001")>0){
                    logonAcked.incrementAndGet();
                }else if(fixString.indexOf("\u000135=8\u0001")>0){

                    int completedInCurrentPeriod = completedInCurrentPeriodCounter.incrementAndGet();

                    handleER(fixString, recvTimeNano);

                    if(completedInCurrentPeriod < c._msgNumPerPeriod) {

                        sendNextOrder(totalSent.get());

                        totalSent.incrementAndGet();

                    }else{
                        completedInCurrentPeriodCounter.set(0);
                    }
                }


            });

            socket.handler(buffer -> {
                parser.handle(buffer);
            });

        });

        liveSockets.forEach( (clientID, socket)-> {
            Message logon = FIXMessageUtil.buildLogon(clientID, MatchingEngineFIXWrapper.serverCompID);
            Buffer logonAsBuffer = Util.buildBuffer(logon, MatchingEngineVertxWrapper.vertxTCPDelimiter);
            socket.write(logonAsBuffer);
        });

        while(true){
            if(logonAcked.get() < clientIDs.size()){
                log.info("testtool - {} - wait all connections Logon, expect:{}, now:{}", _testToolArgs.clientCompIDPrefix, clientIDs.size(), logonAcked.get());
                TimeUnit.SECONDS.sleep(3);
            }else{
                break;
            }
        }


        log.info("{} all connections ready", _testToolArgs.clientCompIDPrefix);


        //Trigger the first order for each period.
        //The related callback will control how many orders to be sent in current period
        Runnable command = ()-> {
            try {

                sendNextOrder(totalSent.get());

                totalSent.incrementAndGet();

            } catch (Exception e) {
                log.error("exception while sending",e);
            }

        };
        long initialDelay = 0;
        ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();
        executor.scheduleAtFixedRate( command,  initialDelay, c._period,   c._unit);

        start = Instant.now();
        _vertx.setPeriodic(30 * 1000, (v)->{
            double sendRateInSecond = TestToolUtil.getCurrentRateInSecond(start, totalSent);
            log.warn("{} - totalSent:{}, rotalRecv:{}, sendRateInSecond:{}", _testToolArgs.clientCompIDPrefix, totalSent, totalRecv, String.format("%.2f", sendRateInSecond));
        });

    }

    void sendNextOrder(int currentTotalSent){
        TestToolUtil.OrderBrief nextOrdBrief = nextOrderInfo(_orderInfoList, currentTotalSent);

        NetSocket nextSocket = liveSockets.get(nextOrdBrief._clientEntity);
        if(nextSocket == null){
            log.error("cannot find the live vertx socket for result live socket list, live sockets:{}, nextClientCompID:{}",liveSockets, nextOrdBrief._clientEntity);
            return;
        }

        Buffer orderAsBuffer = buildOrderBuffer(nextOrdBrief);
        nextSocket.write(orderAsBuffer);
    }

    TestToolUtil.OrderBrief nextOrderInfo(List<TestToolUtil.OrderBrief> orderInfoList, int curentTotalSent){

        int nextOrdBriefIndex = curentTotalSent % orderInfoList.size();
        TestToolUtil.OrderBrief nextOrdBrief = orderInfoList.get(nextOrdBriefIndex);
        return  nextOrdBrief;
    }
    Buffer buildOrderBuffer(TestToolUtil.OrderBrief nextOrdBrief){


        String clientOrdID = nextOrdBrief._clientEntity + UniqIDGenerator.next();
        Message newOrder   = FIXMessageUtil.buildNewOrderSingle(nextOrdBrief._clientEntity,clientOrdID,nextOrdBrief._symbol,nextOrdBrief._px,nextOrdBrief._qty,nextOrdBrief._ordType,nextOrdBrief._side);
        if(nextOrdBrief._clientEntity.startsWith(MatchingEngineApp.LATENCY_ENTITY_PREFIX)){
            FIXMessageUtil.addLatencyText(newOrder);
        }

        Buffer orderAsBuffer = Util.buildBuffer(newOrder, MatchingEngineVertxWrapper.vertxTCPDelimiter);

        return orderAsBuffer;
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
    void handleER(String fixER, long recvTimeNano){

        if(fixER.indexOf("\u000156="+MatchingEngineApp.LATENCY_ENTITY_PREFIX) > 0){


            String latencyRecord= TestToolUtil.getLantecyRecord(fixER,recvTimeNano);
            if(latencyRecord.length() < 1){
                log.error("fail to get latency record from:{}", fixER);
                return;
            }

            _latencyWritingExecutor.submit(()->{
                try {
                    output.write(latencyRecord.getBytes());
                }catch (Exception e){
                    log.error("fail to write", e);
                }
            });
        }
    }

    void stop()throws Exception{
        output.flush();
        output.close();
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

        int clientNum = testToolArgsO.numOfClients;
        int ratePerMinute = testToolArgsO.ratePerMinute;

        log.info("testtool -{}, clientNum:{}, ratePerMin:{}, symbol:{}, px:{}, qty:{}, ordType:{},side:{}",
                testToolArgsO.clientCompIDPrefix,clientNum,ratePerMinute,symbol, price, qty, ordType, side);

        VertxClientRoundBatch clientBatch = new VertxClientRoundBatch(testToolArgsO);

        clientBatch.execute();

        TimeUnit.SECONDS.sleep(testToolArgsO.durationInSecond);
        clientBatch.stop();
        log.info("exiting, since reached duration limit:"+ testToolArgsO.durationInSecond+" seconds");
        System.exit(0);
    }
}
