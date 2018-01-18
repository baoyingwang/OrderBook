package baoying.orderbook.app;

import baoying.orderbook.CommonMessage;
import baoying.orderbook.MatchingEngine;
import baoying.orderbook.OrderBook;
import baoying.orderbook.TradeMessage;
import baoying.orderbook.testtool.FIXMessageUtil;
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

//https://www.java2blog.com/spring-boot-web-application-example/
public class MatchingEngineVertxWrapper {


    public static String vertxTCPDelimiter = "\nXYZ\n";

    private final static Logger log = LoggerFactory.getLogger(MatchingEngineVertxWrapper.class);

    private final MatchingEngine _engine;

    private final Vertx _vertx;
    private final int _vertx_tcp_port;

    private final BiMap<String, NetSocket> _liveClientCompIDs = HashBiMap.create();
    //private final BiMap<NetSocket, String> _liveSockets = _liveClientCompIDs.inverse();

    MatchingEngineVertxWrapper(MatchingEngine engine,
                               Vertx vertx, int vertx_tcp_port) throws Exception{

        _engine = engine;

        _vertx = vertx;
        _vertx_tcp_port = vertx_tcp_port;

    }

    public void start(){

        NetServerOptions options = new NetServerOptions()
                .setTcpNoDelay(true);
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
                String clientCompID = _liveClientCompIDs.inverse().remove(socket);
                log.info("The socket has been closed for:{}", clientCompID);
            });
        });


        server.listen(_vertx_tcp_port, "localhost", res -> {
            if (res.succeeded()) {
                log.info("Vertx TCP Server is now listening on :{}", _vertx_tcp_port);
            } else {
                log.error("Failed to bind:{}!",_vertx_tcp_port);
            }
        });
    }

    DataDictionary dd50sp1 = new DataDictionary("FIX50SP1.xml");
    boolean fixMsgDoValidation = false;

    private void handleMessage(Buffer buffer, NetSocket socket) {

        long zeroOLatencyOrdRrecvTimeNano = 0;
        final int     msgSize   = buffer.getInt(0);
        final String  msgString = buffer.getString(4, msgSize+4);
        log.debug("vertx received:{}", msgString);


        boolean isLatencyClient;
        if(msgString.indexOf("\u000149="+MatchingEngineApp.LATENCY_ENTITY_PREFIX)>0){
            isLatencyClient=true;
            zeroOLatencyOrdRrecvTimeNano = System.nanoTime();
        }else{
            isLatencyClient=false;
        }

        try {

            Message msg = new Message();
            msg.fromString(msgString, dd50sp1, fixMsgDoValidation);

            String msgType = msg.getHeader().getString(35);
            switch (msgType){
                case "D" :
                    processIncomingOrder(msg, zeroOLatencyOrdRrecvTimeNano);
                    break;
                case "A" :
                    log.info("vertx - received logon:{}", msg);
                    processIncomingLogon(msg, socket);
                    break;
                default :
                    log.error("unknown message type:{}", msgType);
            }

        }
        catch(Exception e){
            log.error("problem while processing vertx:"+ msgString , e);
        }

    }
    private void processIncomingLogon(final Message logon, NetSocket socket) throws Exception{

        String clientEntity = logon.getHeader().getString(49);
        _liveClientCompIDs.put(clientEntity, socket);

        Message ackLogon = FIXMessageUtil.buildLogon(MatchingEngineFIXWrapper.serverCompID, clientEntity);
        Buffer buffer = Util.buildBuffer(ackLogon, vertxTCPDelimiter);
        socket.write(buffer);
    }

    private void processIncomingOrder(final Message newSingleOrder, final long zeroOLatencyOrdRrecvTimeNano) throws Exception{

        String orderID = UniqIDGenerator.next();

        final TradeMessage.OriginalOrder originalOrder
                = MatchingEngineFIXHelper.buildOriginalOrder(
                    CommonMessage.ExternalSource.VertxTCP,
                    newSingleOrder,
                    orderID,
                    zeroOLatencyOrdRrecvTimeNano);

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

        String clientEntityID = singleSideExecutionReport._originOrder._clientEntityID;
        NetSocket socket = this._liveClientCompIDs.get(clientEntityID);
        if(socket == null){
            log.debug("cannot find the live vertx socket for result client:{}, on clientOrdID:{}",clientEntityID,singleSideExecutionReport._originOrder._clientOrdID);
            return;
        }

        _vertx.runOnContext((v)->{

            Message fixER = MatchingEngineFIXHelper.translateSingeSideER(singleSideExecutionReport);

            log.debug("TX:{}", fixER);

            Buffer erBuffer = Util.buildBuffer(fixER, vertxTCPDelimiter);
            socket.write(erBuffer);

        });
    }

    @Subscribe
    public void process(TradeMessage.MatchedExecutionReport matchedExecutionReport){

        List<Util.Tuple<Message,TradeMessage.OriginalOrder>> fixERs
                = MatchingEngineFIXHelper.translateMatchedER(
                        CommonMessage.ExternalSource.VertxTCP,
                        matchedExecutionReport);

        for(Util.Tuple<Message,TradeMessage.OriginalOrder> fixER_ord : fixERs){

            String clientEntityID = fixER_ord._2._clientEntityID;
            NetSocket socket = this._liveClientCompIDs.get(clientEntityID);
            if(socket == null){
                log.debug("cannot find the live vertx socket for result client:{}, on clientOrdID:{}",clientEntityID,fixER_ord._2._clientOrdID);
                continue;
            }


            _vertx.runOnContext((v)->{

                if(fixER_ord._2._isLatencyTestOrder){
                    long order_process_done_sysNano_test = System.nanoTime();
                    String latencyTimes = fixER_ord._2._latencyTimesFromClient
                            +","+fixER_ord._2._recvFromClient_sysNano_test
                            +","+ order_process_done_sysNano_test ;
                    fixER_ord._1.setString(FIXMessageUtil.latencyTimesField, latencyTimes);
                }

                Message fixER = fixER_ord._1;
                Buffer erBuffer = Util.buildBuffer(fixER, vertxTCPDelimiter);
                socket.write(erBuffer);

            });

        }

    }

}
