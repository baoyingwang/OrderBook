# Introduction
It is an OrderBook(Matching Engine). It supports
* LIMIT and MARKET order.
* generate execution reports and market data
* Web interface, FIX interface and TCP interface(for better performance)
* the highlight is performance 

v1.1 performance - 5000 order/second (bg50000.100_lt1.1 via TCP) - see https://github.com/baoyingwang/OrderBook/wiki/Track-Performance-Tuning-for-v1.1
* match:4.4us 99% (2.2us 95%)
* end to end(by native TCP): 1.5ms 99%  (1.1 ms 95%)
* restful throughput : 5000 by springboot(see https://github.com/baoyingwang/OrderBook/wiki/Restful-API(spring-boot)%E6%80%A7%E8%83%BD%E6%B5%8B%E8%AF%95%E4%B8%8E%E5%88%86%E6%9E%90-on-v1.1-20180408)

v1 performance - 5000 order/second(bg50000.10_lt1.1 via FIX - persist OFF)
* match:2.5us 95%
* end to end(by FIX): 20ms 95%  

note: matching priority - price then time.

# Architecture - v2.0 (on the plan) - more for production deployment, e.g. split OMS and MDS as separate service; fault tolerance; data persistence, etc

* OMS - order management service. Validate client connection/credit/etc; Record execution result and support query;
* MDS - market data service.

design consideration : VertX EventBus can be considered for the communication between Matching Engine and OMS/MDS.
note: why not consider ChronicleQ? Looks like only its enterprise version support tcp sync. Its communication version only support local file sync. Local file syncing does not makes sense for a distributed application.
![ME_2.0_architecture_20180407.png](https://raw.githubusercontent.com/wiki/baoyingwang/OrderBook/attachments/ME_2.0/ME_2.0_architecture_20180407.png)

TODO:
- replace gradle with maven - 1 day
- manage current OrderBook with docker - 2 days
- re-design the big picture with K8S and Cloud - 7 days
- move the Orderbook to K8S and/or Clound step by step - ???

# Architecture - v1.1(almost done) - add an interface with pure TCP without FIX store/IO overhead(ongoing) 

The route of an order to matching engine
* TCP(vertx): read bytes from socket -> Q -> vertx event loop thread for matching
* FIX : read bytes from socket -> Q -> QFJ FIX message dispatcher -> Q (disruptor ring buffer-v1.0 or blocking Q of vertx event loop v1.1) -> disruptor thread-v1.0 or vertx event loop-v1.1 for matching

see : https://github.com/baoyingwang/OrderBook/wiki/Track-Performance-Tuning-for-v1.1
It is a wrong direction to reduce the Q inside. Because
1.the goal should be reduce e2e. The e2e is about 1~4 ms. But the Q will only cause a few us(20~50). We should focus on the main part of the time cost.

![ME_1.1_architecture_20180118.png](https://raw.githubusercontent.com/wiki/baoyingwang/OrderBook/attachments/ME_1.1/ME_1.1_architecture_20180118.png)

One of the TCP end to end test result -  Diagram of performance summary of 60,000 order per second(TCP_bg60000.10_lt1.1_600S_20180120_215413_latency_overall.png).
![TCP_bg60000.10_lt1.1_600S_20180120_215413_latency_overall.png](https://raw.githubusercontent.com/wiki/baoyingwang/OrderBook/attachments/ME_1.1/images/TCP_bg60000.10_lt1.1_600S_20180120_215413_latency_overall.png)

# Architecture - v1.0 (released) 

![ME_1.0_architecture_20180107.png](https://raw.githubusercontent.com/wiki/baoyingwang/OrderBook/attachments/ME_1.0/ME_1.0_architecture_20180107.png)

Why not also use Disruptor on the output Q?
- is it required so fast for the output Q, since order has already executed?
- guava async event bus is rather easy to use. The code is clean.


# Current Performance - v1.0 - Jan 7, 2018
* note: rate per second during test 5000 orders per second
* note: java ArrayBlockingQueue is used as input Q. Another option is LMAX Disruptor, which is also supported.
* note: it is tested on my ubuntu(a VM of win7 64bit). 2 cpu are assigned to this vm.
![Disruptor_BusySpinWaitStrategy_bg5000perSec_lt60perMin_duration600sec_20180107_102049_latency_overall.png](https://raw.githubusercontent.com/wiki/baoyingwang/OrderBook/attachments/ME_1.0/images_ME_1.0_test.result_20180107.zips_linux/Disruptor_BusySpinWaitStrategy_bg5000perSec_lt60perMin_duration600sec_20180107_102049_latency_overall.png)

For more images and test data see https://github.com/baoyingwang/OrderBook/wiki/Track-Performance-Tuning - section: Jan 7, 2018 - add e2e measurement (release v1.0 is defined since today)
 

# How to use
after clone, run below to build a package. Find the package at  home/build/distributions, e.g. BaoyingOrderBookFat-v1.1_2018-01-20_204648.673.zip
```
gradle buildPackage
```

unzip the package, and start the it
```
cd scripts
bash bash startMatchingEngine.sh

e.g.
$ bash startMatchingEngine.sh
Matching Engine APP_HOME:/c/baoying.wang/ws/gitnas/OrderBook/build/distributions/BaoyingOrderBookFat-v1.1_2018-01-21_215934.902
starting matching engine for symbols:USDJPY
jar file /c/baoying.wang/ws/gitnas/OrderBook/build/distributions/BaoyingOrderBookFat-v1.1_2018-01-21_215934.902/jars/BaoyingOrderBookFat.jar

  .   ____          _            __ _ _
 /\\ / ___'_ __ _ _(_)_ __  __ _ \ \ \ \
( ( )\___ | '_ | '_| | '_ \/ _` | \ \ \ \
 \\/  ___)| |_)| | | | | || (_| |  ) ) ) )
  '  |____| .__|_| |_|_| |_\__, | / / / /
 =========|_|==============|___/=/_/_/_/
 :: Spring Boot ::  (vv1.1_2018-01-21_215934.902)

```

## Place order by Web
Go to below url. A simple order placing feature is provided. Both aggregated order book and detailed order book are supported.  
```
http://localhost:8080/main.html
```


## Place order by Restful API
```
e.g.
http://localhost:8080/matching/place_order?client_entity=BankB&qty=2000&side=Offer

symbol=USDJPY
client_entity=BankAny, default BankA. A bank cannot match with itself
side=Bid|Offer
price=any price, default 126.0
qty=any number, default 5000
```

## Check the order book(empty if you don't place order)
```
http://localhost:8080/main.html
```

## Check latency test summary (empty if you did not place order)
http://localhost:8080/test_summary.html

## Populate OrderBook by scripts
```
cd test_scripts
source e2e.test.functions.sh
populateOB ../jars/BaoyingOrderBookFat.jar Bid USDJPY 113 5500
populateOB ../jars/BaoyingOrderBookFat.jar Bid USDJPY 114 2500

populateOB ../jars/BaoyingOrderBookFat.jar Offer USDJPY 123 6500
populateOB ../jars/BaoyingOrderBookFat.jar Offer USDJPY 124 1500
```

## send a market order to update the order book by scripts
```
cd test_scripts
source e2e.test.functions.sh
sendOrders ../jars/BaoyingOrderBookFat.jar baoying.orderbook.testtool.vertx.VertxClientRoundBatch "-clientNum 1 -ratePerMinute 10 -client_prefix Bank_XYZ -symbol USDJPY -side Offer -qty 2500 -ordType Market -d 5"
```

## Load JMeter to send test deal, and check above links
see the JMeter file - OrderBook\src\test\resources\jmeter\OrderBook.jmx
