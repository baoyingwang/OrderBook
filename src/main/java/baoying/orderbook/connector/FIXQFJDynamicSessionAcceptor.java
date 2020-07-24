package baoying.orderbook.connector;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import quickfix.*;
import quickfix.mina.acceptor.AcceptorSessionProvider;
import quickfix.mina.acceptor.DynamicAcceptorSessionProvider;

import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.Iterator;

public class FIXQFJDynamicSessionAcceptor {

    private final static Logger log = LoggerFactory.getLogger(FIXQFJDynamicSessionAcceptor.class);

    private final String _appConfigInClasspath;
    private final SessionSettings _settings;
    private final MessageStoreFactory _storeFactory;
    private final LogFactory _logFactory;
    private final Application _msgCallback;

    private final SocketAcceptor _acceptor;

    public FIXQFJDynamicSessionAcceptor(String appConfigInClasspath, Application msgCallback) throws Exception {

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
