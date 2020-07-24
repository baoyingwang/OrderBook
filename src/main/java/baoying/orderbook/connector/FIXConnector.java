package baoying.orderbook.connector;

import baoying.orderbook.MatchingEngineApp;
import baoying.orderbook.util.UniqIDGenerator;
import baoying.orderbook.util.Util;
import baoying.orderbook.core.CommonMessage;
import baoying.orderbook.core.MatchingEngine;
import baoying.orderbook.core.OrderBook;
import baoying.orderbook.core.TradeMessage;
import baoying.orderbook.testtool.FIXMessageUtil;
import com.google.common.eventbus.Subscribe;
import io.vertx.core.Vertx;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import quickfix.*;

import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;

public class FIXConnector {


    //warn : should be exactly same with the value in the quickfix configuration file
    public static String serverCompID = "BaoyingMatchingCompID";

    private final static Logger log = LoggerFactory.getLogger(FIXConnector.class);

    private final MatchingEngine _engine;

    private final Vertx _vertx;

    private final FIXQFJDynamicSessionAcceptor _FIXQFJDynamicSessionAcceptor;
    private final String _appConfigInClasspath;

    private final Set<String> _triedLogonClientCompIDs = new HashSet<>();


    private final ExecutorService _fixProcessMatchResultExecutor = Executors.newSingleThreadExecutor(new ThreadFactory() {
        @Override
        public Thread newThread(Runnable r) {
            return new Thread(r, "Thread - FIXInterface processes match result");
        }
    });

    public FIXConnector(MatchingEngine engine,
                        Vertx vertx,
                        String appConfigInClasspath) throws Exception{

        _engine = engine;

        _vertx = vertx;

        _appConfigInClasspath = appConfigInClasspath;
        _FIXQFJDynamicSessionAcceptor = new FIXQFJDynamicSessionAcceptor(_appConfigInClasspath, new InternalQFJApplicationCallback());

    }

    public void start() throws Exception{

        log.info("start the MatchingEngineFIXWrapper");

        _FIXQFJDynamicSessionAcceptor.start();

        log.info("start the MatchingEngineFIXWrapper - done");
    }

    @Subscribe
    public void process(TradeMessage.SingleSideExecutionReport singleSideExecutionReport) {

        if(! (singleSideExecutionReport._originOrder._source == CommonMessage.ExternalSource.FIX)){
            return;
        }

        _fixProcessMatchResultExecutor.submit(()->{

            Message fixER = FIXHelper.translateSingeSideER(singleSideExecutionReport);
            try {
                sendER(fixER, singleSideExecutionReport._originOrder._clientEntityID);
            } catch (Exception e) {
                log.error("problem while processing:"+fixER.toString(),e);
            }
        });


    }

    @Subscribe
    public void process(TradeMessage.MatchedExecutionReport matchedExecutionReport){

        List<Util.Tuple<Message,TradeMessage.OriginalOrder>> fixERs
                = FIXHelper.translateMatchedER(
                CommonMessage.ExternalSource.FIX,
                matchedExecutionReport);

        for(Util.Tuple<Message,TradeMessage.OriginalOrder> fixER_ord : fixERs){

            if(fixER_ord._2._isLatencyTestOrder){
                long order_process_done_sysNano_test = System.nanoTime();
                String latencyTimes = fixER_ord._2._latencyTimesFromClient
                        +","+fixER_ord._2._recvFromClient_sysNano_test
                        +","+ order_process_done_sysNano_test ;
                fixER_ord._1.setString(FIXMessageUtil.latencyTimesField, latencyTimes);
            }

            _fixProcessMatchResultExecutor.submit(()->{

                try {
                    sendER(fixER_ord._1, fixER_ord._2._clientEntityID);
                } catch (Exception e) {
                    log.error("problem while processing:"+fixER_ord._1.toString(),e);
                }


            });
        }
    }


    public Util.Tuple<Message,TradeMessage.OriginalOrder> processOneSideOfMatchingReport(TradeMessage.MatchedExecutionReport matchedExecutionReport, SimpleOMSEngine.MAKER_TAKER maker_taker){

        final TradeMessage.OriginalOrder originalOrder;
        switch(maker_taker){
            case MAKER :
                originalOrder = matchedExecutionReport._makerOriginOrder;
                break;
            case TAKER :
                originalOrder = matchedExecutionReport._takerOriginOrder;
                break;
            default :
                throw new RuntimeException("unknown side : "+maker_taker);
        }


        final int leavesQty ;
        final String executionID ;
        final TradeMessage.OriginalOrder _originOrder ;
        switch(maker_taker){
            case MAKER : leavesQty = matchedExecutionReport._makerLeavesQty;
                executionID = matchedExecutionReport._matchID + "_M";
                _originOrder = matchedExecutionReport._makerOriginOrder;
                break;
            case TAKER : leavesQty = matchedExecutionReport._takerLeavesQty;
                _originOrder = matchedExecutionReport._takerOriginOrder;
                executionID = matchedExecutionReport._matchID + "_T";
                break;
            default :
                throw new RuntimeException("unknown side : "+maker_taker);
        }

        Message executionReport = new Message();
        executionReport.getHeader().setString(35, "8"); // ExecutionReport
        executionReport.setString(37, _originOrder._orderID); //OrderID
        executionReport.setString(17, executionID); //ExecID
        executionReport.setChar(150, TradeMessage.ExecutionType.TRADE.getFIX150Type()); // ExecType http://www.onixs.biz/fix-dictionary/5.0.SP1/tagNum_150.html

        executionReport.setString(31, String.valueOf(matchedExecutionReport._lastPrice)); //LastPx http://www.onixs.biz/fix-dictionary/5.0.SP1/tagNum_31.html
        executionReport.setString(17, String.valueOf(matchedExecutionReport._lastQty)); //lastQty http://www.onixs.biz/fix-dictionary/5.0.SP1/tagNum_32.html

        final TradeMessage.OrderStatus ordStatus = leavesQty==0?TradeMessage.OrderStatus.FILLED:TradeMessage.OrderStatus.PARTIALLY_FILLED;
        executionReport.setChar(39, ordStatus.getFIX39OrdStatus()); // NEW //OrderStatus http://www.onixs.biz/fix-dictionary/5.0.SP1/tagNum_39.html
        executionReport.setString(55, _originOrder._symbol); // non-repeating group
        executionReport.setChar(54, _originOrder._side.getFIX54Side());// Side 54 - 1:buy, 2:sell
        executionReport.setInt(151, leavesQty);// LeavesQty
        executionReport.setInt(14, _originOrder._qty - leavesQty); //CumQty http://www.onixs.biz/fix-dictionary/4.4/tagNum_14.html
        executionReport.setString(11, _originOrder._clientOrdID);

        return new Util.Tuple<Message,TradeMessage.OriginalOrder>(executionReport,originalOrder);

    }

    private void sendER(Message er, String clientEntityID) throws Exception{

        SessionID sessionID =  new SessionID("FIXT.1.1", serverCompID,clientEntityID, "");
        if(! Session.doesSessionExist(sessionID)){
            log.debug("FIX : not sending ER to {}, since it is off now", clientEntityID);
            return;
        }

        Session.sendToTarget(er, sessionID);
    }

    class InternalQFJApplicationCallback implements Application {

            @Override
            public void fromAdmin(Message message, SessionID sessionId) {
                log.debug("fromAdmin session:{}, received : {} ", sessionId, message);
            }

            @Override
            public void fromApp(final Message paramMessage, final SessionID paramSessionID)  {
                log.debug("fromApp session:{}, received:{} ", paramSessionID, paramMessage);

                try {

                    String msgType = paramMessage.getHeader().getString(35);
                    if(! "D".equals(msgType) ){
                        log.error("msg type:{} is not supported. msg:{}", msgType, paramMessage.toString());
                        return;
                    }

                    long zeroOLatencyOrdRrecvTimeNano = 0;

                    //here, the targetCompID of session is the client ID
                    boolean isLatencyClient = paramSessionID.getTargetCompID().startsWith(MatchingEngineApp.LATENCY_ENTITY_PREFIX);
                    if(isLatencyClient){
                        zeroOLatencyOrdRrecvTimeNano = System.nanoTime();
                    }
                    processIncomingOrder(paramMessage,paramSessionID ,zeroOLatencyOrdRrecvTimeNano);


                }catch (Exception e){
                    log.error("failure", e);
                }

            }

            @Override
            public void onCreate(SessionID paramSessionID) {
                log.debug("onCreate session:{}", paramSessionID);
            }

            @Override
            public void onLogon(SessionID paramSessionID) {
                log.debug("onLogon session:{}", paramSessionID);
                _triedLogonClientCompIDs.add(paramSessionID.getTargetCompID());

            }

            @Override
            public void onLogout(SessionID paramSessionID) {
                log.debug("onLogout session:{}", paramSessionID);

            }

            @Override
            public void toAdmin(Message paramMessage, SessionID paramSessionID) {
                log.debug("toAdmin session:{}, send : {} ", paramSessionID, paramMessage);

            }

            @Override
            public void toApp(Message paramMessage, SessionID paramSessionID) throws DoNotSend {
                log.debug("TX session:{}, send : {} ", paramSessionID, paramMessage);

            }

    }

    private void processIncomingOrder(final Message paramMessage, final SessionID paramSessionID, long zeroOLatencyOrdRrecvTimeNano) throws Exception{

        String orderID = UniqIDGenerator.next();
        _vertx.runOnContext((v)->{

            try {

                final TradeMessage.OriginalOrder originalOrder
                        = FIXHelper.buildOriginalOrder(
                        CommonMessage.ExternalSource.FIX,
                        paramMessage,
                        orderID,
                        zeroOLatencyOrdRrecvTimeNano);
                final List<OrderBook.MEExecutionReportMessageFlag> matchResult = _engine.matchOrder(originalOrder);
                //NOT process the match result here for FIX, because maybe the counterparty is on other interfaces(e.g. vertx).

            }catch (Exception e){
                log.error("fail to process FIX order:"+paramMessage.toString(), e);
            }
        });
    }


}
