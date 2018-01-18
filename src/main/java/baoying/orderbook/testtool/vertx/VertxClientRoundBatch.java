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
import quickfix.Message;

import java.io.BufferedOutputStream;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;

//This is a simplified version.   
//For production code, please read common.DefaultQFJSingleSessionInitiator.
public class VertxClientRoundBatch {

    private final static Logger log = LoggerFactory.getLogger(VertxClientRoundBatch.class);

    private final Vertx _vertx = Vertx.vertx();

    final TestToolArgs _testToolArgs;

    private final AtomicInteger totalSent = new AtomicInteger(0);
    private final AtomicInteger totalRecv = new AtomicInteger(0);

    private Instant start = null;

    private final ExecutorService _LatencyERResultExecutor = Executors.newFixedThreadPool(4,new ThreadFactory() {
        @Override
        public Thread newThread(Runnable r) {
            return new Thread(r, "Thread - testtool processes Latency ER result");
        }
    });

    private final String _latencyDataFile ;
    private final int bufferSize = 10*1024*1024;
    private final BufferedOutputStream output;




    VertxClientRoundBatch(TestToolArgs testToolArgs) throws Exception{

        _testToolArgs = testToolArgs;
        _latencyDataFile = "log/e2e_"+testToolArgs.clientCompIDPrefix+".csv";
        output = TestToolUtil.setupOutputLatencyFile(_latencyDataFile, bufferSize);

    }



    public void execute() throws  Exception{

        ScheduleBatchConfig sender = new ScheduleBatchConfig();
        ScheduleBatchConfig.Config c = sender.getBatchConfig(_testToolArgs.ratePerMinute);
        log.info("testool - {} - schedule config:{}", _testToolArgs.clientCompIDPrefix, c);

        List<String> clientIDs = new ArrayList<>();
        {
            IntStream.range(0, _testToolArgs.numOfClients).forEach(it ->{
                        String clientEntityID = _testToolArgs.clientCompIDPrefix +"_"+UniqIDGenerator.next();
                        clientIDs.add(clientEntityID);
                    });
        }


        AtomicInteger completedInCurrentPeriodCounter = new AtomicInteger(0);
        final AtomicInteger connectedClients = new AtomicInteger(0);


        final Map<String, NetSocket> liveSockets = new HashMap<>();
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

                        int nextClientCompIDIndex = totalSent.get() % clientIDs.size();

                        String nextClientCompID = clientIDs.get(nextClientCompIDIndex);
                        NetSocket nextSocket = liveSockets.get(nextClientCompID);
                        if(nextSocket == null){
                            log.error("cannot find the live vertx socket for result live socket list, live sockets:{}, nextClientCompID:{}",liveSockets, nextClientCompID);
                            return;
                        }

                        String clientOrdID = nextClientCompID+ UniqIDGenerator.next();
                        Message newOrder   = FIXMessageUtil.buildNewOrderSingle(nextClientCompID,clientOrdID,_testToolArgs.symbol,_testToolArgs.px,_testToolArgs.qty,_testToolArgs.orderType,_testToolArgs.side);
                        if(nextClientCompID.startsWith(MatchingEngineApp.LATENCY_ENTITY_PREFIX)){
                            FIXMessageUtil.addLatencyText(newOrder);
                        }

                        Buffer orderAsBuffer = Util.buildBuffer(newOrder, MatchingEngineVertxWrapper.vertxTCPDelimiter);
                        nextSocket.write(orderAsBuffer);
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
                int nextClientCompIDIndex = totalSent.get() % _testToolArgs.numOfClients;
                String clientCompID = clientIDs.get(nextClientCompIDIndex);
                NetSocket socket = liveSockets.get(clientCompID);

                String clientOrdID = clientCompID+ UniqIDGenerator.next();
                Message newOrder   = FIXMessageUtil.buildNewOrderSingle(clientCompID,clientOrdID,_testToolArgs.symbol,_testToolArgs.px,_testToolArgs.qty,_testToolArgs.orderType,_testToolArgs.side);
                if(clientCompID.startsWith(MatchingEngineApp.LATENCY_ENTITY_PREFIX)){
                    FIXMessageUtil.addLatencyText(newOrder);
                }

                Buffer orderAsBuffer = Util.buildBuffer(newOrder, MatchingEngineVertxWrapper.vertxTCPDelimiter);
                socket.write(orderAsBuffer);
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

    void handleER(String fixER, long recvTimeNano){
        //TODO add SOH in the index
        if(fixER.indexOf("56="+MatchingEngineApp.LATENCY_ENTITY_PREFIX) > 0){
            _LatencyERResultExecutor.submit(()->{
                try {
                    TestToolUtil.writeLatencyData(fixER, recvTimeNano, output);
                } catch (Exception e) {
                    log.error("",e);
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
