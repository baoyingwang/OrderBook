package baoying.orderbook.example;

import baoying.orderbook.MatchingEngine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.PostConstruct;
import java.util.Map;

//https://www.java2blog.com/spring-boot-web-application-example/
public class MatchingEngineFIXWrapper {

    private final static Logger log = LoggerFactory.getLogger(MatchingEngineFIXWrapper.class);

    private final Map<String,MatchingEngine> _enginesBySimbol;
    private final SimpleOMSEngine _simpleOMSEngine;
    private final SimpleMarkderDataEngine _simpleMarkderDataEngine ;


    MatchingEngineFIXWrapper(Map<String,MatchingEngine> engines,
                             SimpleOMSEngine simpleOMSEngine,
                             SimpleMarkderDataEngine simpleMarkderDataEngine){

        _simpleOMSEngine=simpleOMSEngine;
        _simpleMarkderDataEngine=simpleMarkderDataEngine;
        _enginesBySimbol = engines;

    }
    @PostConstruct
    public void start(){

        log.info("start the MatchingEngineFIXWrapper");

    }


}
