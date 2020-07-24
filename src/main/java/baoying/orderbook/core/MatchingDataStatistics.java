package baoying.orderbook.core;

import com.google.common.collect.EvictingQueue;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalUnit;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;


public class MatchingDataStatistics {

    ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();

    private CurrentPeriod _currentPeriod;
    final CurrentPeriod _theWholeLifeCycle;


    int _period = 60;
    final TimeUnit _unit = TimeUnit.SECONDS;
    final TemporalUnit _addUnit = ChronoUnit.SECONDS; //this two should be same

    private final int _maxQueuedPeriods ;
    private final EvictingQueue _periods ;

    Lock queueLock = new ReentrantLock();
    MatchingDataStatistics(){

        _maxQueuedPeriods = 60;
        _periods = EvictingQueue.create(_maxQueuedPeriods);


        int initialDelay = _period; //delay, since i have prepared a _currentPeriod in advance.

        Runnable command = ()->{
            Instant start = Instant.now();
            Instant end = start.plus(_period, _addUnit);
            _currentPeriod = new CurrentPeriod(start, Optional.of(end));
            addPeriod(_currentPeriod);
        };

        Instant start = Instant.now();
        Instant end = start.plus(_period, _addUnit);
        _currentPeriod = new CurrentPeriod(start, Optional.of(end));
        addPeriod(_currentPeriod);

        _theWholeLifeCycle = new CurrentPeriod(Instant.now(), Optional.empty());
        executor.scheduleAtFixedRate( command,  initialDelay, _period,   _unit);

    }

    private void addPeriod(CurrentPeriod period){
        queueLock.lock();
        try {
            _periods.add(period);
        }finally {
            queueLock.unlock();
        }
    }

    public List<CurrentPeriod> dataList(){


        List<CurrentPeriod> result ;
        queueLock.lock();
        try {
            result = new ArrayList(_periods);
        }finally {
            queueLock.unlock();
        }

        return result;
    }


    void increaseRecvOrder(){

        _theWholeLifeCycle._recvOrderCounter.incrementAndGet();
        _currentPeriod._recvOrderCounter.incrementAndGet();
    }

    void increaseER(){

        _theWholeLifeCycle._ERCounter.incrementAndGet();
        _currentPeriod._ERCounter.incrementAndGet();
    }

    void increaseMD(){
        _theWholeLifeCycle._MDCounter.incrementAndGet();
        _currentPeriod._MDCounter.incrementAndGet();
    }

    public void reset(){
        _theWholeLifeCycle.reset();
        _currentPeriod.reset();
    }

    public Map<String, String> overallSummary(){

        Map<String, String> result = this._theWholeLifeCycle.summaryAsHash();
        result.put("each period",this._period+" "+this._unit.toString());

        return result;
    }

    public class CurrentPeriod{


        CurrentPeriod(Instant startTime, Optional<Instant> endTime){
            _startTime = startTime;
            _endTime = endTime;
        }

        private AtomicLong _recvOrderCounter = new AtomicLong(0);
        private AtomicLong _ERCounter = new AtomicLong(0);
        private AtomicLong _MDCounter = new AtomicLong(0);

        private Instant _startTime; //not final to support reset;
        private final Optional<Instant> _endTime;


        public long orderCount(){
            return _recvOrderCounter.get();
        }

        public long erCount(){
            return _ERCounter.get();
        }

        public long mdCount(){
            return _MDCounter.get();
        }

        public Instant startTime(){
            return _startTime;
        }

        public void reset(){

            _startTime = Instant.now();

            _recvOrderCounter = new AtomicLong(0);
            _ERCounter = new AtomicLong(0);
            _MDCounter = new AtomicLong(0);
        }

        public  double rateOfOrdPerSec(){
            return rate(_recvOrderCounter);
        }

        public double rateOfERPerSec(){
            return rate(_ERCounter);
        }

        public double rateOfMDPerSec(){
            return rate(_MDCounter);
        }

        private double rate(AtomicLong counter){

            Instant endTime = endTimeForCalculation();

            long duration = endTime.getEpochSecond() - _startTime.getEpochSecond();
            if(duration == 0){
                return 0;
            }

            return (counter.get()*1.0)/duration;
        }

        private Instant endTimeForCalculation(){
            final Instant now = Instant.now();
            final Instant endTime;
            if(_endTime.isPresent()){
                if(_endTime.get().isAfter(now)){
                    //not yet ended period
                    endTime = now;
                }else{
                    endTime = _endTime.get();
                }

            }else{
                endTime = Instant.now();
            }

            return endTime;
        }



        public Map<String, String> summaryAsHash(){

            Map<String, String> data = new HashMap<>();

            data.put("order_count", String.valueOf(orderCount()));
            data.put("er_count", String.valueOf(erCount()));
            data.put("md_count", String.valueOf(mdCount()));

            Instant endTime = endTimeForCalculation();

            data.put("start_time", startTime().toString());
            data.put("end_time", endTime.toString());
            data.put("duration_in_second", String.valueOf((endTime.getEpochSecond() - _startTime.getEpochSecond())));

            data.put("ord_rate_per_second", String.format("%.2f", rateOfOrdPerSec()));
            data.put("er_rate_per_second", String.format("%.2f", rateOfERPerSec()));
            data.put("md_rate_per_second", String.format("%.2f", rateOfMDPerSec()));

            return data;
        }
    }


}
