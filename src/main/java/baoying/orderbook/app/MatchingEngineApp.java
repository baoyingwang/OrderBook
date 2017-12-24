package baoying.orderbook.app;

import baoying.orderbook.MatchingEngine;
import baoying.orderbook.OrderBook;
import baoying.orderbook.testtool.FirstQFJClientBatch;
import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;
import com.google.common.eventbus.AsyncEventBus;
import com.lmax.disruptor.BusySpinWaitStrategy;
import com.lmax.disruptor.SleepingWaitStrategy;
import com.lmax.disruptor.WaitStrategy;
import com.lmax.disruptor.YieldingWaitStrategy;
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

    private static String _queueType ;
    private static String _disruptorStrategy ;
    private static int _queueSize ;

    private final MatchingEngine _matchingEngine_USDJPY;
    private final MatchingEngine _matchingEngine_USDHKD;

    private final AsyncEventBus _marketDataBus;
    private final AsyncEventBus _executionReportsBus;

    private final SimpleOMSEngine         _simpleOMSEngine ;
    private final SimpleMarkderDataEngine _simpleMarkderDataEngine ;

    private final MatchingEngineWebWrapper _webWrapper;
    private final MatchingEngineFIXWrapper _fixWrapper;

    private final SysPerfDataCollectionEngine sysPerfEngine;

    //Why are the Bean declarations required?  MatchingEnginWebWrapper depends on Spring Rest feature. That needs Beans as constructor arguments.
    @Bean
    SimpleOMSEngine createSimpleOMSEngine(){
        return _simpleOMSEngine;
    }

    @Bean
    SimpleMarkderDataEngine createSimpleMarkderDataEngine(){
        return _simpleMarkderDataEngine;
    }

    @Bean
    Map<String,MatchingEngine> createEnginesBySymbol(){
        Map<String,MatchingEngine> result = new HashMap<>();
        result.put("USDJPY", _matchingEngine_USDJPY);
        result.put("USDHKD", _matchingEngine_USDHKD);
        return result;
    }

    public MatchingEngineApp() throws Exception{

        //TODO configurable. It should be be printed per minute, or per 2 minutes on production
        String startTimeAsFileName = Util.fileNameFormatter.format(Instant.now());
        Path usageFile = Paths.get("log/sysUsage_app.start"+ startTimeAsFileName+".csv");
        Path sysInfoFile = Paths.get("log/sysInfo_app.start"+ startTimeAsFileName+".txt");
        sysPerfEngine = SysPerfDataCollectionEngine.asEngine(5, TimeUnit.SECONDS, usageFile);
        Map<String, String> config = sysPerfEngine.config();
        config.forEach((k, v)->{
            try {
                Files.write(sysInfoFile, (k + ":" + v + "\n").getBytes(), APPEND, CREATE);
            }catch (Exception e){
            //TODO log exception
            e.printStackTrace();
            }
        });

        _executionReportsBus = new AsyncEventBus("async evt ER bus - for all engines", Executors.newSingleThreadExecutor(new ThreadFactory() {
                    @Override
                    public Thread newThread(Runnable r){ return new Thread(r, "Thread - async evt ER bus - for all engines"); }
                }
            )
        );
        _marketDataBus = new AsyncEventBus("async evt MD bus - for all engines", Executors.newSingleThreadExecutor(new ThreadFactory() {
                       @Override
                       public Thread newThread(Runnable r){ return new Thread(r, "Thread - async evt MD bus - for all engines"); }
                   }
            )
        );

        switch (_queueType){
            case "Disruptor" :
                final WaitStrategy waitStrategy;
                switch (_disruptorStrategy){
                    case "SleepingWaitStrategy": waitStrategy = new SleepingWaitStrategy(); break;
                    case "YieldingWaitStrategy": waitStrategy = new YieldingWaitStrategy(); break;
                    case "BusySpinWaitStrategy": waitStrategy = new BusySpinWaitStrategy(); break;
                    default: throw new RuntimeException("unknown disruptor strategy:" + _disruptorStrategy +". Only SleepingWaitStrategy(default), YieldingWaitStrategy, and BusySpinWaitStrategy  is supported.");

                }
                _matchingEngine_USDJPY = new MatchingEngine(new OrderBook("USDJPY"), _executionReportsBus, _marketDataBus, _queueSize, waitStrategy);
                _matchingEngine_USDHKD = new MatchingEngine(new OrderBook("USDHKD"), _executionReportsBus, _marketDataBus, _queueSize, waitStrategy);
                break;
            case "BlockingQueue" :
                _matchingEngine_USDJPY = new MatchingEngine(new OrderBook("USDJPY"), _executionReportsBus, _marketDataBus, _queueSize);
                _matchingEngine_USDHKD = new MatchingEngine(new OrderBook("USDHKD"), _executionReportsBus, _marketDataBus, _queueSize);
                break;
            default: throw new RuntimeException("unknown queue type:" + _queueType +". Only Disruptor and BlockingQueue is supported.");
        }

        _simpleOMSEngine          = new SimpleOMSEngine();
        _simpleMarkderDataEngine = new SimpleMarkderDataEngine(Arrays.asList(_matchingEngine_USDJPY, _matchingEngine_USDHKD));
        _executionReportsBus.register(_simpleOMSEngine);
        _marketDataBus.register(_simpleMarkderDataEngine);

        Map<String, MatchingEngine> enginesBySymbol = new HashMap<>();
        enginesBySymbol.put("USDJPY", _matchingEngine_USDJPY);
        enginesBySymbol.put("USDHKD", _matchingEngine_USDHKD);
        _webWrapper = new MatchingEngineWebWrapper(enginesBySymbol,
                _simpleOMSEngine,
                _simpleMarkderDataEngine);

        _fixWrapper = new MatchingEngineFIXWrapper(enginesBySymbol,
                _simpleOMSEngine,
                _simpleMarkderDataEngine,
                "DefaultDynamicSessionQFJServer.qfj.config.txt");
        //register FIX, for streaming output
        _executionReportsBus.register(_fixWrapper);
    }

    @PostConstruct
    public void start() throws Exception{

        log.info("start the MatchingEngineApp");
        _matchingEngine_USDJPY.start();
        _matchingEngine_USDHKD.start();
        _simpleMarkderDataEngine.start();

        _fixWrapper.start();
    }

    static class Args {
        @Parameter(names = "-q", description = "queue type: BlockingQueue(default), Disruptor")
        private String queueType = "BlockingQueue";

        @Parameter(names = "-s", description = "strategy for Disruptor : SleepingWaitStrategy(default), YieldingWaitStrategy, and BusySpinWaitStrategy")
        private String disruptorStrategy = "SleepingWaitStrategy";

        @Parameter(names = "-b", description = "queue size. default : 65536. 2^x is required for Disruptor Q type")
        private int queueSize = 65536;
    }

    public static void main(String[] args) {
        Args argsO = new Args();
        JCommander.newBuilder().addObject(argsO).build().parse(args);

        //TODO how to pass the argument in to MatchingEngineApp? right now, static fields are used
        _queueType = argsO.queueType;
        _disruptorStrategy = argsO.disruptorStrategy;
        _queueSize = argsO.queueSize;

        SpringApplication.run(MatchingEngineApp.class);
    }


}

