package baoying.orderbook;

import baoying.orderbook.monitor.JVMMonitorEngine;
import baoying.orderbook.util.*;
import baoying.orderbook.connector.FIXConnector;
import baoying.orderbook.connector.VertxConnector;
import baoying.orderbook.connector.WebConnector;
import baoying.orderbook.connector.SimpleOMSEngine;
import baoying.orderbook.core.MarketDataMessage;
import baoying.orderbook.core.MatchingEngine;
import baoying.orderbook.marketdata.SimpleMarkderDataEngine;
import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;
import com.google.common.eventbus.AsyncEventBus;
import io.vertx.core.Vertx;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

import javax.annotation.PostConstruct;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

import static java.nio.file.StandardOpenOption.APPEND;
import static java.nio.file.StandardOpenOption.CREATE;

@SpringBootApplication
public class MatchingEngineApp {

    private final static Logger log = LoggerFactory.getLogger(MatchingEngineApp.class);

    public static String LATENCY_ENTITY_PREFIX = "LxTxCx";

    private static List<String> _symbolList;
    private static int _snapshotRequestIntervalInSecond;
    private static int _vertx_tcp_port;

    private final InternalMatchingEngineApp _internalMatchingEngineApp;



    MatchingEngineApp() throws Exception{
        _internalMatchingEngineApp = new InternalMatchingEngineApp(_symbolList);
    }

    @PostConstruct
    public void start() throws Exception {
        _internalMatchingEngineApp.start();
    }

    //Why are the Bean declarations required?  MatchingEnginWebWrapper depends on Spring Rest feature.
    //That needs Beans as constructor arguments.
    @Bean
    SimpleOMSEngine createSimpleOMSEngine() {
        return _internalMatchingEngineApp._simpleOMSEngine;
    }

    @Bean
    SimpleMarkderDataEngine createSimpleMarkerDataEngine() {
        return _internalMatchingEngineApp._simpleMarkderDataEngine;
    }

    @Bean
    MatchingEngine engine() { return _internalMatchingEngineApp._engine;}

    @Bean
    Vertx vertx() { return _internalMatchingEngineApp._vertx;}

    class InternalMatchingEngineApp {

        private final MatchingEngine _engine;

        private final Vertx _vertx = Vertx.vertx();

        private final AsyncEventBus _marketDataBus;
        private final AsyncEventBus _executionReportsBus;

        private final SimpleOMSEngine _simpleOMSEngine;
        private final SimpleMarkderDataEngine _simpleMarkderDataEngine;

        private final WebConnector _webWrapper;
        private final FIXConnector _fixWrapper;
        private final VertxConnector _vertxWrapper;

        private final JVMMonitorEngine sysPerfEngine;

        public InternalMatchingEngineApp(List<String> symbols) throws Exception {

            //TODO configurable. It should be be printed per minute, or per 2 minutes on production
            String startTimeAsFileName = Util.fileNameFormatter.format(Instant.now());
            Path usageFile = Paths.get("log/sysUsage_app.start" + startTimeAsFileName + ".csv");
            Path sysInfoFile = Paths.get("log/sysInfo_app.start" + startTimeAsFileName + ".txt");
            sysPerfEngine = JVMMonitorEngine.asEngine(5, TimeUnit.SECONDS, usageFile);
            Map<String, String> config = sysPerfEngine.config();
            config.forEach((k, v) -> {
                try {
                    Files.write(sysInfoFile, (k + ":" + v + "\n").getBytes(), APPEND, CREATE);
                } catch (Exception e) {
                    //TODO log exception
                    e.printStackTrace();
                }
            });

            _executionReportsBus = new AsyncEventBus("async ER bus", Executors.newSingleThreadExecutor(new ThreadFactory() {
                                                                                     @Override
                                                                                     public Thread newThread(Runnable r) {
                                                                                         return new Thread(r, "Thread - async ER Bus");
                                                                                     }
}
            )
            );
            _marketDataBus = new AsyncEventBus("async MD bus", Executors.newSingleThreadExecutor(new ThreadFactory() {
                                                                                                                           @Override
                                                                                                                           public Thread newThread(Runnable r) {
                                                                                                                               return new Thread(r, "Thread - async MD bus");
                                                                                                                           }
                                                                                                                       }
            )
            );

            _engine = new MatchingEngine(_symbolList, _executionReportsBus, _marketDataBus);


            _simpleOMSEngine = new SimpleOMSEngine();
            _simpleMarkderDataEngine = new SimpleMarkderDataEngine();
            _executionReportsBus.register(_simpleOMSEngine);

            _marketDataBus.register(_simpleMarkderDataEngine);


            _webWrapper = new WebConnector(_engine,
                    _vertx,
                    _simpleOMSEngine,
                    _simpleMarkderDataEngine);

            _fixWrapper = new FIXConnector(_engine,
                    _vertx,
                    "DefaultDynamicSessionQFJServer.qfj.config.txt");

            _executionReportsBus.register(_fixWrapper);


            _vertxWrapper = new VertxConnector(_engine,_vertx, _vertx_tcp_port);
            _executionReportsBus.register(_vertxWrapper);

        }

        public void start() throws Exception {

            log.info("start the MatchingEngineApp");
            _simpleMarkderDataEngine.start();
            _fixWrapper.start();

            _vertx.setPeriodic(_snapshotRequestIntervalInSecond * 1000, id ->{
                for(String symbol: _engine._symbols){
                    _engine.addOrdBookRequest(new MarketDataMessage.AggregatedOrderBookRequest(String.valueOf(System.nanoTime()), symbol,5));
                    _engine.addOrdBookRequest(new MarketDataMessage.DetailOrderBookRequest(String.valueOf(System.nanoTime()), symbol,5));
                }
            });

            _vertxWrapper.start();
        }
    }


    static class Args {

        @Parameter(names = {"--vertx_tcp_port", "-v_p"})
        int vertx_tcp_port = 10005;

        @Parameter(names = {"--symbols", "-s"}, listConverter = Util.CSVListConverter.class)
        List<String> symbols = Arrays.asList(new String[]{"USDJPY","USDHKD"});

        @Parameter(names = {"--snapshot_interval_in_second"}, description = "the internal simple market data engine will request snaphost periodically . default : 1")
        private int snapshotRequestIntervalInSecond = 2;

    }

    public static void main(String[] args) {

        Args argsO = new Args();
        JCommander.newBuilder().addObject(argsO).build().parse(args);

        //TODO how to pass the argument in to MatchingEngineApp? right now, static fields are used
        _symbolList = argsO.symbols;
        _snapshotRequestIntervalInSecond = argsO.snapshotRequestIntervalInSecond;
        _vertx_tcp_port = argsO.vertx_tcp_port;


        SpringApplication.run(MatchingEngineApp.class);
    }


}

