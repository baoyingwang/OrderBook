package baoying.orderbook.app;

import baoying.orderbook.MarketDataMessage;
import baoying.orderbook.MatchingEngine;
import baoying.orderbook.OrderBook;
import baoying.orderbook.TradeMessage;
import baoying.orderbook.testtool.FirstQFJClientBatch;
import com.beust.jcommander.IStringConverter;
import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;
import com.google.common.eventbus.AsyncEventBus;
import com.google.gson.Gson;
import com.lmax.disruptor.BusySpinWaitStrategy;
import com.lmax.disruptor.SleepingWaitStrategy;
import com.lmax.disruptor.WaitStrategy;
import com.lmax.disruptor.YieldingWaitStrategy;
import net.openhft.affinity.AffinityLock;
import net.openhft.chronicle.queue.ChronicleQueue;
import net.openhft.chronicle.queue.ExcerptTailer;
import net.openhft.chronicle.queue.impl.single.SingleChronicleQueueBuilder;
import net.openhft.chronicle.threads.LongPauser;
import net.openhft.chronicle.threads.Pauser;
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
    private static List<String> _symbolList;
    private static String _queueType;
    private static String _disruptorStrategy;
    private static int _queueSize;

    private final InternalMatchingEngineApp _internalMatchingEngineApp;

    MatchingEngineApp() throws Exception{
        _internalMatchingEngineApp = new InternalMatchingEngineApp(_symbolList);
    }

    @PostConstruct
    public void start() throws Exception {
        _internalMatchingEngineApp.start();
    }

    //Why are the Bean declarations required?  MatchingEnginWebWrapper depends on Spring Rest feature. That needs Beans as constructor arguments.
    @Bean
    SimpleOMSEngine createSimpleOMSEngine() {
        return _internalMatchingEngineApp._simpleOMSEngine;
    }

    @Bean
    SimpleMarkderDataEngine createSimpleMarkderDataEngine() {
        return _internalMatchingEngineApp._simpleMarkderDataEngine;
    }

    @Bean
    Map<String, MatchingEngine> createEnginesBySymbol() { return _internalMatchingEngineApp._enginesBySymbol;}

    class InternalMatchingEngineApp {


        private final List<MatchingEngine> _engines = new ArrayList<>();
        private final Map<String, MatchingEngine> _enginesBySymbol = new HashMap<>();

        private final AsyncEventBus _marketDataBus;
        private final AsyncEventBus _executionReportsBus;

        private final SimpleOMSEngine _simpleOMSEngine;
        private final SimpleMarkderDataEngine _simpleMarkderDataEngine;

        private final MatchingEngineWebWrapper _webWrapper;
        private final MatchingEngineFIXWrapper _fixWrapper;

        private final SysPerfDataCollectionEngine sysPerfEngine;

        public final String _chronicleERFile="log/chronicleQ/ExecutionReports";
        public final String _chronicleMDFile="log/chronicleQ/MarketData";

        public InternalMatchingEngineApp(List<String> symbols) throws Exception {

            //TODO configurable. It should be be printed per minute, or per 2 minutes on production
            String startTimeAsFileName = Util.fileNameFormatter.format(Instant.now());
            Path usageFile = Paths.get("log/sysUsage_app.start" + startTimeAsFileName + ".csv");
            Path sysInfoFile = Paths.get("log/sysInfo_app.start" + startTimeAsFileName + ".txt");
            sysPerfEngine = SysPerfDataCollectionEngine.asEngine(5, TimeUnit.SECONDS, usageFile);
            Map<String, String> config = sysPerfEngine.config();
            config.forEach((k, v) -> {
                try {
                    Files.write(sysInfoFile, (k + ":" + v + "\n").getBytes(), APPEND, CREATE);
                } catch (Exception e) {
                    //TODO log exception
                    e.printStackTrace();
                }
            });

            _executionReportsBus = new AsyncEventBus("async evt ER bus - for all engines", Executors.newSingleThreadExecutor(new ThreadFactory() {
                                                                                                                                 @Override
                                                                                                                                 public Thread newThread(Runnable r) {
                                                                                                                                     return new Thread(r, "Thread - async evt ER bus - for all engines");
                                                                                                                                 }
                                                                                                                             }
            )
            );
            _marketDataBus = new AsyncEventBus("async evt MD bus - for all engines", Executors.newSingleThreadExecutor(new ThreadFactory() {
                                                                                                                           @Override
                                                                                                                           public Thread newThread(Runnable r) {
                                                                                                                               return new Thread(r, "Thread - async evt MD bus - for all engines");
                                                                                                                           }
                                                                                                                       }
            )
            );

            final ChronicleQueue chronicleQueueER = SingleChronicleQueueBuilder.binary(_chronicleERFile).build();
            final ChronicleQueue chronicleQueueMD = SingleChronicleQueueBuilder.binary(_chronicleMDFile).build();


            switch (_queueType) {
                case "Disruptor":
                    final WaitStrategy waitStrategy;
                    switch (_disruptorStrategy) {
                        case "SleepingWaitStrategy":
                            waitStrategy = new SleepingWaitStrategy();
                            break;
                        case "YieldingWaitStrategy":
                            waitStrategy = new YieldingWaitStrategy();
                            break;
                        case "BusySpinWaitStrategy":
                            waitStrategy = new BusySpinWaitStrategy();
                            break;
                        default:
                            throw new RuntimeException("unknown disruptor strategy:" + _disruptorStrategy + ". Only SleepingWaitStrategy(default), YieldingWaitStrategy, and BusySpinWaitStrategy  is supported.");

                    }

                    symbols.forEach(symbol -> {
                        MatchingEngine engine = new MatchingEngine(new OrderBook(symbol), _executionReportsBus, _marketDataBus, _queueSize, waitStrategy, chronicleQueueER, chronicleQueueMD);
                        _engines.add(engine);
                        _enginesBySymbol.put(symbol, engine);
                    });
                    break;
                case "BlockingQueue":
                    symbols.forEach(symbol -> {
                        MatchingEngine engine = new MatchingEngine(new OrderBook(symbol), _executionReportsBus, _marketDataBus, _queueSize, chronicleQueueER, chronicleQueueMD);
                        _engines.add(engine);
                        _enginesBySymbol.put(symbol, engine);
                    });
                    break;
                default:
                    throw new RuntimeException("unknown queue type:" + _queueType + ". Only Disruptor and BlockingQueue is supported.");
            }

            _simpleOMSEngine = new SimpleOMSEngine();
            _simpleMarkderDataEngine = new SimpleMarkderDataEngine(_engines);
            _executionReportsBus.register(_simpleOMSEngine);
            _marketDataBus.register(_simpleMarkderDataEngine);


            _webWrapper = new MatchingEngineWebWrapper(_enginesBySymbol,
                    _simpleOMSEngine,
                    _simpleMarkderDataEngine);

            _fixWrapper = new MatchingEngineFIXWrapper(_enginesBySymbol,
                    _simpleOMSEngine,
                    _simpleMarkderDataEngine,
                    "DefaultDynamicSessionQFJServer.qfj.config.txt");
            //register FIX, for streaming output
            _executionReportsBus.register(_fixWrapper);


            final ExcerptTailer erTailer = chronicleQueueER.createTailer();
            final ExcerptTailer mdTailer = chronicleQueueMD.createTailer();
            Thread erTrailerThread = new Thread(new Runnable(){

                AffinityLock lock = AffinityLock.acquireLock();
                private Gson _gson = new Gson();
                final Pauser pauser = new LongPauser(1, 100, 500, 10_000, TimeUnit.MICROSECONDS);
                public void run(){

                    while(!Thread.currentThread().isInterrupted()) {
                        String msg = erTailer.readText();
                        if (msg != null) {
                            pauser.reset();
                            //https://www.mkyong.com/java/how-do-convert-java-object-to-from-json-format-gson-api/
                            if(msg.indexOf("MatchedExecutionReport") > 0){

                                TradeMessage.MatchedExecutionReport matchedER = _gson.fromJson(msg, TradeMessage.MatchedExecutionReport.class);
                                _simpleOMSEngine.process(matchedER);
                                _fixWrapper.process(matchedER);
                            }else if(msg.indexOf("SingleSideExecutionReport") > 0){
                                TradeMessage.SingleSideExecutionReport singleSideER = _gson.fromJson(msg, TradeMessage.SingleSideExecutionReport.class);
                                _simpleOMSEngine.process(singleSideER);
                                _fixWrapper.process(singleSideER);
                            }else{
                                log.error("unknown msg for ER:{}", msg);
                            }
                        }else{
                            pauser.pause();
                        }

                    }
                }
            }, "erTrailerThread");
            Thread mdTrailerThread = new Thread(new Runnable(){

                AffinityLock lock = AffinityLock.acquireLock();
                private Gson _gson = new Gson();
                final Pauser pauser = new LongPauser(1, 100, 500, 10_000, TimeUnit.MICROSECONDS);
                public void run(){

                    while(!Thread.currentThread().isInterrupted()) {
                        String msg = mdTailer.readText();
                        if (msg != null) {
                            pauser.reset();
                            //https://www.mkyong.com/java/how-do-convert-java-object-to-from-json-format-gson-api/
                            if(msg.indexOf("OrderBookDelta") > 0){

                                MarketDataMessage.OrderBookDelta delta = _gson.fromJson(msg, MarketDataMessage.OrderBookDelta.class);
                                _simpleMarkderDataEngine.process(delta);
                            }else if(msg.indexOf("AggregatedOrderBook") > 0){
                                MarketDataMessage.AggregatedOrderBook ob = _gson.fromJson(msg, MarketDataMessage.AggregatedOrderBook.class);
                                _simpleMarkderDataEngine.process(ob);
                            }else{
                                log.error("unknown msg for MD:{}", msg);
                            }
                        }else{
                            pauser.pause();
                        }

                    }
                }
            }, "mdTrailerThread");
            erTrailerThread.start();
            mdTrailerThread.start();

        }

        public void start() throws Exception {

            log.info("start the MatchingEngineApp");
            _engines.forEach(engine -> engine.start());
            _simpleMarkderDataEngine.start();

            _fixWrapper.start();
        }
    }
    class CSVListConverter implements IStringConverter<List<String>> {

        @Override
        public List<String> convert(String csvValues) {
            String [] values = csvValues.split(",");
            return Arrays.asList(values);
        }
    }
    static class Args {
        @Parameter(names = {"--symbols", "-s"}, listConverter = CSVListConverter.class)
        List<String> symbols = Arrays.asList(new String[]{"USDJPY"});

        @Parameter(names = {"--queue_type", "-qt"}, description = "queue type: BlockingQueue(default), Disruptor")
        private String queueType = "BlockingQueue";

        @Parameter(names = {"--strategy", "-sn"}, description = "strategy for Disruptor : SleepingWaitStrategy(default), YieldingWaitStrategy, and BusySpinWaitStrategy")
        private String disruptorStrategy = "SleepingWaitStrategy";

        @Parameter(names = {"--queue_size", "-qs"}, description = "queue size. default : 65536. 2^x is required for Disruptor Q type")
        private int queueSize = 65536;
    }

    public static void main(String[] args) {
        Args argsO = new Args();
        JCommander.newBuilder().addObject(argsO).build().parse(args);

        //TODO how to pass the argument in to MatchingEngineApp? right now, static fields are used
        _queueType = argsO.queueType;
        _disruptorStrategy = argsO.disruptorStrategy;
        _queueSize = argsO.queueSize;
        _symbolList = argsO.symbols;

        SpringApplication.run(MatchingEngineApp.class);
    }


}

