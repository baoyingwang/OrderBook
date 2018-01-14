package baoying.orderbook.app;

import baoying.orderbook.CommonMessage;
import baoying.orderbook.MatchingEngine;
import baoying.orderbook.OrderBook;
import baoying.orderbook.OrderBook.MEExecutionReportMessageFlag;
import baoying.orderbook.TradeMessage;
import baoying.orderbook.qfj.QFJDynamicSessionAcceptor;
import baoying.orderbook.testtool.FIXMessageUtil;
import baoying.orderbook.testtool.qfj.LatencyMessageCallback;
import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;
import com.google.common.eventbus.Subscribe;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.net.NetServer;
import io.vertx.core.net.NetServerOptions;
import io.vertx.core.net.NetSocket;
import io.vertx.core.parsetools.RecordParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import quickfix.*;

import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;

//https://www.java2blog.com/spring-boot-web-application-example/
public class MatchingEngineVertxWrapper {


    public static String vertxTCPDelimiter = "\nXYZ\n";

    private final static Logger log = LoggerFactory.getLogger(MatchingEngineVertxWrapper.class);

    private final MatchingEngine _engine;

    private final Vertx _vertx;
    //TODO - high - remove hardcode port
    public static final int port = 10005;

    private final BiMap<String, NetSocket> _triedLogonClientCompIDs = HashBiMap.create();
    private final BiMap<NetSocket, String> _triedLogonSockets = _triedLogonClientCompIDs.inverse();

    MatchingEngineVertxWrapper(MatchingEngine engine,
                               Vertx vertx) throws Exception{

        _engine = engine;

        _vertx = vertx;

    }

    public void start(){

        NetServerOptions options = new NetServerOptions().setTcpNoDelay(true);
        NetServer server = _vertx.createNetServer(options);
        server.connectHandler(socket -> {
            //http://vertx.io/docs/vertx-core/java/#_record_parser
            final RecordParser parser = RecordParser.newDelimited(vertxTCPDelimiter, buffer -> {
                handleMessage(buffer, socket);
            });

            socket.handler(buffer -> {
                parser.handle(buffer);

            });

            socket.closeHandler(v -> {
                _triedLogonSockets.remove(socket);
                System.out.println("The socket has been closed");
            });
        });


        server.listen(10005, "localhost", res -> {
            if (res.succeeded()) {
                log.info("Vertx TCP Server is now listening on :{}", port);
            } else {
                log.error("Failed to bind:{}!",port);
            }
        });
    }

    DataDictionary dd50sp1 = new DataDictionary("FIX50SP1.xml");
    boolean doValidation = true;

    private void handleMessage(Buffer buffer, NetSocket socket) {
        int msgSize = buffer.getInt(0);
        String msg = buffer.getString(4, msgSize);

        try {

            Message x = new Message();
            x.fromString(msg, dd50sp1, doValidation);

            String msgType = x.getHeader().getString(35);

            if (msgType.equals("A")) {
                processIncomingLogon(x, socket);
            }else if (msgType.equals("D")) {
                processIncomingOrder(x);
            } else {
                log.error("unknown message type:{}", msgType);
            }
        }
        catch(Exception e){
            log.error("problem while processing vertx:"+ msg , e);
        }

    }
    private void processIncomingLogon(final Message logon, NetSocket socket) throws Exception{

        String clientEntity = logon.getHeader().getString(49);
        _triedLogonClientCompIDs.put(clientEntity, socket);

    }

    private void processIncomingOrder(final Message newSingleOrder) throws Exception{

        String orderID = UniqIDGenerator.next();

        final TradeMessage.OriginalOrder originalOrder
                = MatchingEngineFIXHelper.buildOriginalOrder(
                    CommonMessage.ExternalSource.VertxTCP,
                    newSingleOrder,
                    orderID);

        _vertx.runOnContext((v)->{

            final List<OrderBook.MEExecutionReportMessageFlag> matchResult = _engine.matchOrder(originalOrder);
            //NOT process the match result here for vertx, because maybe the counterparty is on other interfaces(e.g. FIX).
        });

    }

    @Subscribe
    public void process(TradeMessage.SingleSideExecutionReport singleSideExecutionReport) {


        if(! (singleSideExecutionReport._originOrder._source == CommonMessage.ExternalSource.VertxTCP)){
            return;
        }

        _vertx.runOnContext((v)->{

            Message fixER = MatchingEngineFIXHelper.translateSingeSideER(singleSideExecutionReport);
            Buffer erBuffer = Util.buildBuffer(fixER, vertxTCPDelimiter);

            String clientEntityID = singleSideExecutionReport._originOrder._clientEntityID;
            sendERBuffer(erBuffer, clientEntityID);

        });
    }

    @Subscribe
    public void process(TradeMessage.MatchedExecutionReport matchedExecutionReport){



            List<Util.Tuple<Message,TradeMessage.OriginalOrder>> fixERs
                    = MatchingEngineFIXHelper.translateMatchedER(
                            CommonMessage.ExternalSource.VertxTCP,
                            matchedExecutionReport);

            for(Util.Tuple<Message,TradeMessage.OriginalOrder> fixER_ord : fixERs){

                if(fixER_ord._2._isLatencyTestOrder){
                    long order_process_done_sysNano_test = System.nanoTime();
                    String latencyTimes = fixER_ord._2._latencyTimesFromClient
                            +","+fixER_ord._2._recvFromClient_sysNano_test
                            +","+ order_process_done_sysNano_test ;
                    fixER_ord._1.setString(FIXMessageUtil.latencyTimesField, latencyTimes);
                }

                _vertx.runOnContext((v)->{

                    Message fixER = fixER_ord._1;
                    Buffer erBuffer = Util.buildBuffer(fixER, vertxTCPDelimiter);

                    String clientEntityID = fixER_ord._2._clientEntityID;
                    sendERBuffer(erBuffer, clientEntityID);
                });


            }

    }

    private void sendERBuffer(Buffer erBuffer, String clientEntityID){
        NetSocket socket = this._triedLogonClientCompIDs.get(clientEntityID);
        if(socket == null){
            log.error("cannot find the live vertx socket for result:"+clientEntityID);
            return;
        }

        socket.write(erBuffer);
    }

}
