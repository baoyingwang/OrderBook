package baoying.orderbook.example;

import baoying.orderbook.MatchingEngine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import quickfix.*;
import quickfix.mina.acceptor.AcceptorSessionProvider;
import quickfix.mina.acceptor.DynamicAcceptorSessionProvider;

import javax.annotation.PostConstruct;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.Iterator;
import java.util.Map;

//https://www.java2blog.com/spring-boot-web-application-example/
public class MatchingEngineFIXWrapper {

    private final static Logger log = LoggerFactory.getLogger(MatchingEngineFIXWrapper.class);

    private final Map<String,MatchingEngine> _enginesBySimbol;
    private final SimpleOMSEngine _simpleOMSEngine;
    private final SimpleMarkderDataEngine _simpleMarkderDataEngine ;

    private final DynamicSessionQFJAcceptor _dynamicSessionQFJAcceptor;
    private final String _appConfigInClasspath;

    MatchingEngineFIXWrapper(Map<String,MatchingEngine> engines,
                             SimpleOMSEngine simpleOMSEngine,
                             SimpleMarkderDataEngine simpleMarkderDataEngine,
                             String appConfigInClasspath) throws Exception{

        _simpleOMSEngine=simpleOMSEngine;
        _simpleMarkderDataEngine=simpleMarkderDataEngine;
        _enginesBySimbol = engines;


        _appConfigInClasspath = appConfigInClasspath;
        _dynamicSessionQFJAcceptor = new DynamicSessionQFJAcceptor(_appConfigInClasspath, new InternalQFJApplicationCallback());

    }
    @PostConstruct
    public void start() throws Exception{

        log.info("start the MatchingEngineFIXWrapper");

        _dynamicSessionQFJAcceptor.start();

        log.info("start the MatchingEngineFIXWrapper - done");
    }

    class InternalQFJApplicationCallback implements Application {

            @Override
            public void fromAdmin(Message paramMessage, SessionID paramSessionID)
            throws FieldNotFound, IncorrectDataFormat, IncorrectTagValue, RejectLogon {
                log.debug("fromAdmin session:{}, received : {} ", paramSessionID.toString(), paramMessage.toString());

            }

            @Override
            public void fromApp(Message paramMessage, SessionID paramSessionID)
            throws FieldNotFound, IncorrectDataFormat, IncorrectTagValue, UnsupportedMessageType {
                log.debug("fromApp session:{}, received : {} ", paramSessionID.toString(), paramMessage.toString());

            }

            @Override
            public void onCreate(SessionID paramSessionID) {
                log.debug("onCreate session:{}", paramSessionID.toString());
            }

            @Override
            public void onLogon(SessionID paramSessionID) {
                log.debug("onLogon session:{}", paramSessionID.toString());

            }

            @Override
            public void onLogout(SessionID paramSessionID) {
                log.debug("onLogout session:{}", paramSessionID.toString());

            }

            @Override
            public void toAdmin(Message paramMessage, SessionID paramSessionID) {
                log.debug("toAdmin session:{}, send : {} ", paramSessionID.toString(), paramMessage.toString());

            }

            @Override
            public void toApp(Message paramMessage, SessionID paramSessionID) throws DoNotSend {
                log.debug("toApp session:{}, send : {} ", paramSessionID.toString(), paramMessage.toString());

            }

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
                log.info("session in the configuration : " + sessionID.toString());
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
