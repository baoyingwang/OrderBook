package baoying.orderbook.example;

import baoying.orderbook.MatchingEngine;
import baoying.orderbook.OrderBook;
import com.google.common.eventbus.AsyncEventBus;
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
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

import static java.nio.file.StandardOpenOption.APPEND;
import static java.nio.file.StandardOpenOption.CREATE;

@SpringBootApplication
public class MatchingEngineApp {

    private final static Logger log = LoggerFactory.getLogger(MatchingEngineApp.class);

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

        _matchingEngine_USDJPY = new MatchingEngine(new OrderBook("USDJPY"), _executionReportsBus, _marketDataBus);
        _matchingEngine_USDHKD = new MatchingEngine(new OrderBook("USDHKD"), _executionReportsBus, _marketDataBus);

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

    public static void main(String[] args) {
        SpringApplication.run(MatchingEngineApp.class);

    }
}

