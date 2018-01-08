package baoying.orderbook.app;

import baoying.orderbook.CommonMessage;
import baoying.orderbook.MatchingEngine;
import baoying.orderbook.OrderBook;
import baoying.orderbook.TradeMessage;
import baoying.orderbook.testtool.LatencyMessageCallback;
import com.google.common.eventbus.Subscribe;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import quickfix.*;
import quickfix.mina.acceptor.AcceptorSessionProvider;
import quickfix.mina.acceptor.DynamicAcceptorSessionProvider;

import javax.annotation.PostConstruct;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;

//https://www.java2blog.com/spring-boot-web-application-example/
public class MatchingEngineFIXWrapper {

    //warn : should be exactly same with the value in the quickfix configuration file
    public static String serverCompID = "BaoyingMatchingCompID";

    private final static Logger log = LoggerFactory.getLogger(MatchingEngineFIXWrapper.class);

    private final MatchingEngine _engine;
    private final SimpleOMSEngine _simpleOMSEngine;
    private final SimpleMarkderDataEngine _simpleMarkderDataEngine ;

    private final DynamicSessionQFJAcceptor _dynamicSessionQFJAcceptor;
    private final String _appConfigInClasspath;

    private final Set<String> _triedLogonClientCompIDs = new HashSet<>();


    private final ExecutorService _fixERSendingExecutor = Executors.newSingleThreadExecutor(new ThreadFactory() {
        @Override
        public Thread newThread(Runnable r) {
            return new Thread(r, "Thread - FIXInterface Sending ER");
        }
    }
            );


    MatchingEngineFIXWrapper(MatchingEngine engine,
                             SimpleOMSEngine simpleOMSEngine,
                             SimpleMarkderDataEngine simpleMarkderDataEngine,
                             String appConfigInClasspath) throws Exception{

        _simpleOMSEngine=simpleOMSEngine;
        _simpleMarkderDataEngine=simpleMarkderDataEngine;
        _engine = engine;


        _appConfigInClasspath = appConfigInClasspath;
        _dynamicSessionQFJAcceptor = new DynamicSessionQFJAcceptor(_appConfigInClasspath, new InternalQFJApplicationCallback());

    }
    @PostConstruct
    public void start() throws Exception{

        log.info("start the MatchingEngineFIXWrapper");

        _dynamicSessionQFJAcceptor.start();

        log.info("start the MatchingEngineFIXWrapper - done");
    }


    public Message translate(TradeMessage.SingleSideExecutionReport singleSideExecutionReport) {

        final SessionID sessionID ;
        Message er = buildFIXExecutionReport(singleSideExecutionReport);
        //sendER(er, singleSideExecutionReport._originOrder._clientEntityID);
        return er;

    }


    public List<Util.Tuple<Message,TradeMessage.OriginalOrder>> translate(TradeMessage.MatchedExecutionReport matchedExecutionReport) {
        List<Util.Tuple<Message,TradeMessage.OriginalOrder>> fixExecutionReports = new ArrayList<>();

        fixExecutionReports.add(processOneSideOfMatchingReport(matchedExecutionReport, SimpleOMSEngine.MAKER_TAKER.MAKER));
        fixExecutionReports.add(processOneSideOfMatchingReport(matchedExecutionReport, SimpleOMSEngine.MAKER_TAKER.TAKER));

        return fixExecutionReports;
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

        Message er = buildExternalERfromInternalER(matchedExecutionReport, maker_taker);
        return new Util.Tuple<Message,TradeMessage.OriginalOrder>(er,originalOrder);

    }

    private void sendER(Message er, String clientEntityID){

        if(clientEntityID.startsWith(SimpleOMSEngine.IGNORE_ENTITY_PREFIX)){
            log.debug("ignore the ER sending since {} is with the prefix:{}", clientEntityID, SimpleOMSEngine.IGNORE_ENTITY_PREFIX);
            return;
        }
        SessionID sessionID =  new SessionID("FIXT.1.1", serverCompID,clientEntityID, "");
        sendER(er, sessionID);
    }

    private void sendER(Message er, SessionID sessionID){

        if(! Session.doesSessionExist(sessionID)){
            log.warn("ignore the realtime ER to client, since the session:{} is not online now", sessionID.toString());
            return;
        }

        _fixERSendingExecutor.submit(new Runnable() {
            @Override
            public void run() {
                try{
                    Session.sendToTarget(er, sessionID);
                }catch (Exception e){
                    log.error("failure", e);
                }
            }
        });
    }

    class InternalQFJApplicationCallback implements Application {

            @Override
            public void fromAdmin(Message message, SessionID sessionId) {
                log.debug("fromAdmin session:{}, received : {} ", sessionId, message);
            }

            @Override
            public void fromApp(final Message paramMessage, final SessionID paramSessionID)  {
                log.debug("fromApp session:{}, received : {} ", paramSessionID, paramMessage);

                try {

                    String msgType = paramMessage.getHeader().getString(35);
                    if(! "D".equals(msgType) ){
                        log.error("msg type:{} is not supported. msg:{}", msgType, paramMessage.toString());
                        return;
                    }

                    String orderID = UniqIDGenerator.next();
                    TradeMessage.OriginalOrder originalOrder = buildOriginalOrder(paramMessage, orderID);
                    recordPerformanceData(paramMessage, originalOrder);

                    List<OrderBook.MatchingEnginOutputMessageFlag> executionReports = _engine.addOrder(originalOrder);
                    for (OrderBook.MatchingEnginOutputMessageFlag executionReport : executionReports) {

                        if(executionReport instanceof TradeMessage.SingleSideExecutionReport ){

                            TradeMessage.SingleSideExecutionReport singleSideExecutionReport = (TradeMessage.SingleSideExecutionReport)executionReport;
                            Message fixER = translate(singleSideExecutionReport);
                            sendER(fixER, singleSideExecutionReport._originOrder._clientEntityID);

                        }else if(executionReport instanceof TradeMessage.MatchedExecutionReport){
                            TradeMessage.MatchedExecutionReport matchedExecutionReport = (TradeMessage.MatchedExecutionReport)executionReport;

                            if(matchedExecutionReport._takerOriginOrder._isLatencyTestOrder){
                                matchedExecutionReport._takerOriginOrder._matched_sysNano_test=System.nanoTime();
                            }
                            List<Util.Tuple<Message,TradeMessage.OriginalOrder>> fixERs = translate(matchedExecutionReport);

                            for(Util.Tuple<Message,TradeMessage.OriginalOrder> fixER : fixERs){

                                if(fixER._2._isLatencyTestOrder){
                                    long fixERTranslated_sysNano_test = System.nanoTime();
                                    String latencyTimes = originalOrder._latencyTimesFromClient
                                            +","+originalOrder._recvFromClient_sysNano_test
                                            +","+originalOrder._matched_sysNano_test
                                            +","+ fixERTranslated_sysNano_test ;
                                    fixER._1.setString(LatencyMessageCallback.latencyTimesField, latencyTimes);
                                }

                                sendER(fixER._1, fixER._2._clientEntityID);
                            }

                        }else{
                            log.error("unknown type: " + executionReport);
                        }
                    }


                }catch (Exception e){
                    log.error("failure", e);
                }




            }

        private void recordPerformanceData(Message paramMessage, TradeMessage.OriginalOrder originalOrder) throws FieldNotFound {
            if(originalOrder._clientEntityID.startsWith(MatchingEngine.LATENCY_ENTITY_PREFIX)){
                originalOrder._isLatencyTestOrder = true;
                originalOrder._recvFromClient_sysNano_test = System.nanoTime();

                if(paramMessage.isSetField(LatencyMessageCallback.latencyTimesField)){
                    originalOrder._latencyTimesFromClient = paramMessage.getString(LatencyMessageCallback.latencyTimesField);
                }
            }
            _simpleOMSEngine.perfTestDataForWeb.recordNewOrder(originalOrder);
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
                log.debug("toApp session:{}, send : {} ", paramSessionID, paramMessage);

            }

    }

    TradeMessage.OriginalOrder buildOriginalOrder(Message paramMessage, String orderID)throws FieldNotFound{

        String symbol = paramMessage.getString(55);
        String clientEntity = paramMessage.getHeader().getString(49);
        CommonMessage.Side orderSide = CommonMessage.Side.fixValueOf(paramMessage.getChar(54));  //1 buy, 2 sell
        String clientOrdID = paramMessage.getString(11);

        final double price; //Price
        if(paramMessage.isSetField(44)){
            price = paramMessage.getDouble(44); //Price
        }else{
            price = -1;
        }
        CommonMessage.OrderType ordType = CommonMessage.OrderType.fixValueOf(paramMessage.getChar(40)); //OrdType 1:Market, 2:Limit
        int qty = paramMessage.getInt(38);

        TradeMessage.OriginalOrder originalOrder  = new TradeMessage.OriginalOrder( System.currentTimeMillis(),symbol,orderSide ,ordType, price, qty, orderID, clientOrdID, clientEntity);

        return originalOrder;
    }

    Message buildFIXExecutionReport(TradeMessage.SingleSideExecutionReport singleSideER){

        Message executionReport = new Message();
        executionReport.getHeader().setString(35, "8"); // ExecutionReport
        executionReport.setString(37, singleSideER._originOrder._orderID); //OrderID
        executionReport.setString(17, String.valueOf(singleSideER._msgID)); //ExecID
        executionReport.setChar(150, singleSideER._type.getFIX150Type()); // ExecType http://www.onixs.biz/fix-dictionary/5.0.SP1/tagNum_150.html

        final TradeMessage.OrderStatus ordStatus;
        switch (singleSideER._type){
            case NEW : ordStatus = TradeMessage.OrderStatus.NEW; break;
            case CANCELLED: ordStatus = TradeMessage.OrderStatus.CANCELLED; break;
            case  REJECTED: ordStatus = TradeMessage.OrderStatus.REJECTED; break;
            default: throw new RuntimeException("Cannot translate execType:"+singleSideER._type.toString()+" to order status" );
        }
        executionReport.setChar(39, ordStatus.getFIX39OrdStatus()); // NEW //OrderStatus http://www.onixs.biz/fix-dictionary/5.0.SP1/tagNum_39.html
        executionReport.setString(55, singleSideER._originOrder._symbol); // non-repeating group
        executionReport.setChar(54, singleSideER._originOrder._side.getFIX54Side());// Side 54 - 1:buy, 2:sell
        executionReport.setInt(151, singleSideER._leavesQty);// LeavesQty
        executionReport.setInt(14, singleSideER._originOrder._qty - singleSideER._leavesQty);
        executionReport.setString(11, singleSideER._originOrder._clientOrdID);
        return executionReport;
    }

    private Message buildExternalERfromInternalER(TradeMessage.MatchedExecutionReport matchedExecutionReport, SimpleOMSEngine.MAKER_TAKER maker_taker ){

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

        return executionReport;
    }

    class DynamicSessionQFJAcceptor {

        private final String _appConfigInClasspath;
        private final SessionSettings _settings;
        private final MessageStoreFactory _storeFactory;
        private final LogFactory _logFactory;
        private final Application _msgCallback;

        private final SocketAcceptor _acceptor;

        public DynamicSessionQFJAcceptor(String appConfigInClasspath, Application msgCallback) throws Exception {

            _appConfigInClasspath = appConfigInClasspath;
            log.info("qfj server begin initializing, with app configuration file in classpath:{}", appConfigInClasspath);

            _msgCallback = msgCallback;

            _settings = new SessionSettings(appConfigInClasspath);
            for (final Iterator<SessionID> i = _settings.sectionIterator(); i.hasNext();) {
                final SessionID sessionID = i.next();
                log.info("session in the configuration :{} ", sessionID);
            }

            // It also supports other store factory, e.g. JDBC, memory. Maybe you
            // could use them in some advanced cases.
            _storeFactory = new FileStoreFactory(_settings);

            // It also supports other log factory, e.g. JDBC. But I think SL4J is
            // good enough.
            _logFactory = new SLF4JLogFactory(_settings);

            // This is single thread. For multi-thread, see
            // quickfix.ThreadedSocketInitiator, and QFJ Advanced.
            _acceptor = new SocketAcceptor(_msgCallback, _storeFactory, _settings, _logFactory,
                    new DefaultMessageFactory());

            setupDynamicSessionProvider(msgCallback , _acceptor);
            log.info("qfj server initialized, with app configuration file in classpath:{}", appConfigInClasspath);

        }

        // start is NOT put in constructor deliberately, to let it pair with
        // shutdown
        public void start() throws Exception {

            log.info("qfj server start, {}", _appConfigInClasspath);

            _acceptor.start();

            log.info("qfj server started, {}", _appConfigInClasspath);
        }

        public void stop() throws Exception {

            log.info("qfj server stop, {}", _appConfigInClasspath);

            _acceptor.stop();

            log.info("qfj server stopped, {}", _appConfigInClasspath);
        }

        private void setupDynamicSessionProvider(Application application, SocketAcceptor connectorAsAcc)
                throws ConfigError, FieldConvertError {
            for (final Iterator<SessionID> i = _settings.sectionIterator(); i.hasNext();) {
                final SessionID sessionID = i.next();

                boolean isAcceptorTemplateSet = _settings.isSetting(sessionID, "AcceptorTemplate");
                if (isAcceptorTemplateSet && _settings.getBool(sessionID, "AcceptorTemplate")) {

                    log.info("dynamic acceptor is configured on {}", sessionID);
                    AcceptorSessionProvider provider = new DynamicAcceptorSessionProvider(_settings, sessionID, application,
                            _storeFactory, _logFactory, new DefaultMessageFactory());
                    // SocketAcceptAddress
                    // SocketAcceptPort
                    SocketAddress address = new InetSocketAddress(_settings.getString(sessionID, "SocketAcceptAddress"),
                            (int) (_settings.getLong(sessionID, "SocketAcceptPort")));
                    connectorAsAcc.setSessionProvider(address, provider);

                    // we have to skip setup SessionStateListener,
                    // since the concrete session is not identified yet for
                    // dynamic acceptor.
                    // TODO try to figure out how to setup
                    // SessionStateListener
                    // when the concrete session is created.
                }
            }
        }

    }

}
