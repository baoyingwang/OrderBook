#jarfile=../BaoyingOrderBookFat-2017-12-24_212235.242-all.jar
#bash latency.test.sh ${jarfile} Disruptor     $((60*100)) 600 BusySpinWaitStrategy
#bash latency.test.sh ${jarfile} Disruptor     $((60*100)) 600 SleepingWaitStrategy
#bash latency.test.sh ${jarfile} Disruptor     $((60*100)) 600 YieldingWaitStrategy
#bash latency.test.sh ${jarfile} BlockingQueue $((60*100)) 600 X

#vmstat 5 -t >> vmstat_begin.from$(date '+%Y%m%d_%H%M%S').log &
#http://localhost:18080/matching/reset_test_data
#http://localhost:18080/test_summary.html
#http://localhost:18080/main.html


echo "stop all java process, then start others for next test"
ps -eo pid,user,comm,pcpu | grep java | cut -d' ' -f2 | xargs kill -9

jarfile=$1
queue_type=$2
background_rate_per_min=$3
duration_in_second=$4
strategy=$5

test_name=${queue_type}_${strategy}_bg${background_rate_per_min}perMin_$(date '+%Y%m%d_%H%M%S')_duration${duration_in_second}Sec
echo $test_name


mkdir -p ${test_name}/log
cd ${test_name}
arguments="-q ${queue_type} -s ${strategy}"
mkdir -p log
JVMOptions="-XX:+UseParNewGC -XX:+UseConcMarkSweepGC -Xmx1024M -Xms1024M -XX:+PrintGCDetails -XX:+PrintGCTimeStamps -XX:+PrintGCDateStamps -XX:+PrintGC -XX:+PrintGCApplicationStoppedTime -XX:+PrintGCDateStamps -XX:+PrintGCDetails -XX:+PrintGCTimeStamps -Xloggc:log/GC.txt"
java $JVMOptions -jar  ${jarfile} ${arguments} &

echo "sleep 10 seconds to wait matching up initialize done"
sleep 10

echo "begin preparing big orders to book, for later background orders and latency orders"
for bidPriceForBookPrepare in {101..120}
do
	java -cp ${jarfile} baoying.orderbook.testtool.FirstQFJClientBatch -clientNum 1 -ratePerMinute 10 -client_prefix BACKGROUD_FIX_prepare -symbol USDJPY -side Bid -qty 50000000 -ordType Limit -px ${bidPriceForBookPrepare} -d 5 &
done

for offerPriceForBookPrepare in {121..140}
do
	java -cp ${jarfile} baoying.orderbook.testtool.FirstQFJClientBatch -clientNum 1 -ratePerMinute 10 -client_prefix BACKGROUD_FIX_prepare -symbol USDJPY -side Offer -qty 50000000 -ordType Limit -px ${offerPriceForBookPrepare} -d 5 &
done
echo "wait 30 seconds to populate book done"
sleep 30

echo "begin seding background orders"
background_rate_per_min_single_side=$((${background_rate_per_min}/2))
java -cp ${jarfile} baoying.orderbook.testtool.FirstQFJClientBatch -clientNum 1 -ratePerMinute ${background_rate_per_min_single_side} -client_prefix BACKGROUD_FIX -symbol USDJPY -side Bid   -qty 2 -ordType Market -d ${duration_in_second} &
java -cp ${jarfile} baoying.orderbook.testtool.FirstQFJClientBatch -clientNum 1 -ratePerMinute ${background_rate_per_min_single_side} -client_prefix BACKGROUD_FIX -symbol USDJPY -side Offer -qty 2 -ordType Market -d ${duration_in_second} &

echo "begin sending latency orders"
latency_rate_per_min_single_side=$((60*1))
java -cp ${jarfile} baoying.orderbook.testtool.FirstQFJClientBatch -clientNum 1 -ratePerMinute ${latency_rate_per_min_single_side} -client_prefix 'LTC$$_FIX_B' -symbol USDJPY -side Bid   -qty 2 -ordType Market -d ${duration_in_second} &
java -cp ${jarfile} baoying.orderbook.testtool.FirstQFJClientBatch -clientNum 1 -ratePerMinute ${latency_rate_per_min_single_side} -client_prefix 'LTC$$_FIX_O' -symbol USDJPY -side Offer -qty 2 -ordType Market -d ${duration_in_second} &

for i in {1..10}
do
	echo "sleep every $(($duration_in_second/10)) seconds, and trigger the dump latency data later"
	sleep $(($duration_in_second/10))
	curl http://localhost:8080/matching/get_test_summary | cut -c1-150
done