package baoying.orderbook.example;

import baoying.orderbook.DisruptorInputAcceptor;
import baoying.orderbook.MatchingEngine;
import com.google.common.eventbus.AsyncEventBus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;

@SpringBootApplication
//@Configuration
//@ComponentScan
//@EnableAutoConfiguration
public class MatchingEngineApp {

    private final static Logger log = LoggerFactory.getLogger(MatchingEngineApp.class);

    private final DisruptorInputAcceptor _disruptorInputAcceptor_USDJPY;
    private final MatchingEngine _matchingEngine_USDJPY;

    private final DisruptorInputAcceptor _disruptorInputAcceptor_USDHKD;
    private final MatchingEngine _matchingEngine_USDHKD;

    private final AsyncEventBus _outputMarketDataBus;
    private final AsyncEventBus _outputExecutionReportsBus;


    private final SimpleOMSEngine _simpleOMSEngine ;
    @Bean
    SimpleOMSEngine createSimpleOMSEngine(){
        return _simpleOMSEngine;
    }

    private final SimpleMarkderDataEngine _simpleMarkderDataEngine ;
    @Bean
    SimpleMarkderDataEngine createSimpleMarkderDataEngine(){
        return _simpleMarkderDataEngine;
    }

    @Bean
    Map<String,DisruptorInputAcceptor> createDisruptorInputAcceptorsBySymbol(){
        Map<String,DisruptorInputAcceptor > result = new HashMap<>();
        result.put("USDJPY",_disruptorInputAcceptor_USDJPY);
        result.put("USDHKD",_disruptorInputAcceptor_USDHKD);
        return result;
    }

    public MatchingEngineApp(){

        _simpleOMSEngine = new SimpleOMSEngine();

        _outputExecutionReportsBus = new AsyncEventBus("async evt ER bus - for all engines", Executors.newSingleThreadExecutor());
        _outputMarketDataBus = new AsyncEventBus("async evt MD bus - for all engines", Executors.newSingleThreadExecutor());

        _matchingEngine_USDJPY = new MatchingEngine("USDJPY", _outputExecutionReportsBus, _outputMarketDataBus);
        _matchingEngine_USDHKD = new MatchingEngine("USDHKD", _outputExecutionReportsBus, _outputMarketDataBus);

        _disruptorInputAcceptor_USDJPY = new DisruptorInputAcceptor(_matchingEngine_USDJPY);
        _disruptorInputAcceptor_USDHKD = new DisruptorInputAcceptor(_matchingEngine_USDHKD);

        _simpleMarkderDataEngine = new SimpleMarkderDataEngine(
                Arrays.asList(_disruptorInputAcceptor_USDJPY,
                        _disruptorInputAcceptor_USDHKD));
        _outputExecutionReportsBus.register(_simpleOMSEngine);
        _outputMarketDataBus.register(_simpleMarkderDataEngine);
    }

    @PostConstruct
    public void start(){

        log.info("start the MatchingEngineApp");
        _disruptorInputAcceptor_USDJPY.start();
        _disruptorInputAcceptor_USDHKD.start();
        _simpleMarkderDataEngine.start();

        Map<String, DisruptorInputAcceptor> disruptorInputAcceptorsBySymbol = new HashMap<>();
        disruptorInputAcceptorsBySymbol.put("USDJPY",_disruptorInputAcceptor_USDJPY );
        disruptorInputAcceptorsBySymbol.put("USDHKD",_disruptorInputAcceptor_USDHKD );

        MatchingEngineWebWrapper webWrapper = new MatchingEngineWebWrapper(_simpleOMSEngine,
                _simpleMarkderDataEngine,
                disruptorInputAcceptorsBySymbol);

        MatchingEngineFIXWrapper fixWrapper = new MatchingEngineFIXWrapper();
    }

    public static void main(String[] args) {
        SpringApplication.run(MatchingEngineApp.class);

    }
}

