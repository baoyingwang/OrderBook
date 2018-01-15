package baoying.orderbook.testtool.vertx;

import baoying.orderbook.app.*;
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
import quickfix.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;

//This is a simplified version.   
//For production code, please read common.DefaultQFJSingleSessionInitiator.
public class VertxClientBatch {

    private final static Logger log = LoggerFactory.getLogger(VertxClientBatch.class);

    private final Vertx _vertx = Vertx.vertx();

    private final Map<String, NetSocket> sockets = new HashMap<>();

    private final ExecutorService _fixERResult = Executors.newSingleThreadExecutor(new ThreadFactory() {
        @Override
        public Thread newThread(Runnable r) {
            return new Thread(r, "Thread - testtool processes ER result");
        }
    });

    private  final int _vertx_tcp_port;
    VertxClientBatch(int vertx_tcp_port){
        _vertx_tcp_port = vertx_tcp_port;
    }

    public void execute( String symbol,
                            String price,
                            String qty ,
                            String ordType,
                            String side,

                            String clientCompIDPrefix,
                            int fixClientNum,
                            int ratePerMinute  ) throws  Exception{


        List<String> clientIDs = new ArrayList<>();
        {
            IntStream.range(0, fixClientNum).forEach(it -> clientIDs.add(clientCompIDPrefix + String.valueOf(it)));
        }

        NetClientOptions options = new NetClientOptions()
                .setConnectTimeout(10000)
                .setTcpNoDelay(true);

        for(String clientID: clientIDs){

            NetClient client = _vertx.createNetClient(options);
            client.connect(_vertx_tcp_port, "localhost", res -> {
                if (res.succeeded()) {
                    log.info("vertx client csonnected on port:{}", _vertx_tcp_port);
                    NetSocket socket = res.result();

                    sockets.put(clientID, socket);

                    Message logon = FIXMessageUtil.buildLogon(clientID);
                    Buffer logonAsBuffer = Util.buildBuffer(logon, MatchingEngineVertxWrapper.vertxTCPDelimiter);
                    socket.write(logonAsBuffer);

                    //http://vertx.io/docs/vertx-core/java/#_record_parser
                    final RecordParser parser = RecordParser.newDelimited(MatchingEngineVertxWrapper.vertxTCPDelimiter, buffer -> {

                        long erTimeNano = System.nanoTime();
                        _fixERResult.submit(()->{
                            handleMessage(buffer, erTimeNano);
                        });
                    });

                    socket.handler(buffer -> {
                        parser.handle(buffer);
                    });

                    //Buffer b = VertxUtil.getB(System.nanoTime(), _msgSize, SystemUtils.delimiter());
                    //socket.write(b);

                } else {
                    log.error("Failed to connect, reason:{} ", res.cause().getMessage());
                    System.exit(-1);
                }
            });
        }


        // after start, you have to wait several seconds before sending
        // messages.
        // in production code, you should check the response Logon message.
        // Refer: DefaultQFJSingSessionInitiator.java
        TimeUnit.SECONDS.sleep(3);

        AtomicInteger totalSent = new AtomicInteger(0);
        ScheduleSender s = new ScheduleSender();
        s.execut(ratePerMinute, ()-> {
                int nextClientCompIDIndex = totalSent.get() % fixClientNum;
                String clientCompID = clientIDs.get(nextClientCompIDIndex);

                NetSocket socket = sockets.get(clientCompID);

                String clientOrdID = clientCompID+ UniqIDGenerator.next();
                try {
                    Message order = FIXMessageUtil.buildNewOrderSingle(clientCompID,clientOrdID,symbol,price,qty,ordType,side);

                    if(clientCompID.startsWith(MatchingEngineApp.LATENCY_ENTITY_PREFIX)){
                        FIXMessageUtil.addLatencyText(order);
                    }

                    Buffer orderAsBuffer = Util.buildBuffer(order, MatchingEngineVertxWrapper.vertxTCPDelimiter);
                    socket.write(orderAsBuffer);

                } catch (Exception e) {
                    log.error("exception while sending",e);
                }

                int sent = totalSent.incrementAndGet();
                if (sent >= Integer.MAX_VALUE / 2) {
                    totalSent.getAndSet(0);
                }
        });



    }

    void handleMessage(Buffer messageAsBuffer, long erTimeNano){


        try {
            int length = messageAsBuffer.getInt(0);
            String erString = messageAsBuffer.getString(4, length+4);
            log.debug("test tool received fix:{}",erString);

            DataDictionary dd = null;

            dd = new DataDictionary("FIX50SP1.xml");

            boolean doValidation = false;
            Message er = new Message();
            er.fromString(erString,dd,doValidation);

            String clientCompID = er.getHeader().getString(56);
            if(clientCompID.startsWith(MatchingEngineApp.LATENCY_ENTITY_PREFIX)){
                FIXMessageUtil.recordLetencyTimeStamps(er, erTimeNano);
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

        String clientCompIDPrefix = testToolArgsO.clientCompIDPrefix + System.currentTimeMillis() + "_";
        int clientNum = testToolArgsO.numOfClients;
        int ratePerMinute = testToolArgsO.ratePerMinute;

        VertxClientBatch vertxClientBatch = new VertxClientBatch(testToolArgsO.vertx_tcp_port);

        vertxClientBatch.execute( symbol,price,qty ,ordType, side   ,clientCompIDPrefix  ,clientNum,ratePerMinute);

        TimeUnit.SECONDS.sleep(testToolArgsO.durationInSecond);
        log.info("exiting, since time out:"+ testToolArgsO.durationInSecond+" seconds");
        System.exit(0);
    }
}
