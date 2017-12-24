#bash test.sh ${jarfile} Disruptor     $((60*100)) 600 BusySpinWaitStrategy
#bash test.sh ${jarfile} Disruptor     $((60*100)) 600 SleepingWaitStrategy
#bash test.sh ${jarfile} Disruptor     $((60*100)) 600 YieldingWaitStrategy
#bash test.sh ${jarfile} BlockingQueue $((60*100)) 600 X

#vmstat 5 -t >> vmstat.$(date '+%Y%m%d').log &
#http://localhost:18080/matching/reset_test_data
#http://localhost:18080/test_summary.html
#http://localhost:18080/main.html


#==============
ps -eo pid,user,comm,pcpu | grep java | cut -d' ' -f2 | xargs kill -9

#queue_type=BlockingQueue
#queue_type=Disruptor
#background_rate_per_min=$((60*500))
#strategy=BusySpinWaitStrategy

#jarfile=../BaoyingOrderBookFat-2017-12-24_101139.396-all.jar

jarfile=$1
queue_type=$2
background_rate_per_min=$3
duration_in_second=$4
strategy=$5

test_name=${queue_type}_${strategy}_bg${background_rate_per_min}perMin_$(date '+%Y%m%d_%H%M%S')_duration${duration_in_second}Sec



mkdir -p ${test_name}/log
cd ${test_name}
arguments="-q ${queue_type} -s ${strategy}"
mkdir -p log
JVMOptions="-XX:+UseParNewGC -XX:+UseConcMarkSweepGC -XX:+PrintGCDetails -XX:+PrintGCTimeStamps -XX:+PrintGCDateStamps -XX:+PrintGC -XX:+PrintGCApplicationStoppedTime -XX:+PrintGCDateStamps -XX:+PrintGCDetails -XX:+PrintGCTimeStamps -Xloggc:log/GC.txt"
java $JVMOptions -jar  ${jarfile} ${arguments} &


#prepare order book
java -cp ${jarfile} baoying.orderbook.testtool.FirstQFJClientBatch -clientNum 1 -ratePerMinute 10 -client_prefix BACKGROUD_FIX_prepare -symbol USDJPY -side Bid -qty 50000000 -ordType Limit -px 103 -d 5
java -cp ${jarfile} baoying.orderbook.testtool.FirstQFJClientBatch -clientNum 1 -ratePerMinute 10 -client_prefix BACKGROUD_FIX_prepare -symbol USDJPY -side Bid -qty 50000000 -ordType Limit -px 104 -d 5
java -cp ${jarfile} baoying.orderbook.testtool.FirstQFJClientBatch -clientNum 1 -ratePerMinute 10 -client_prefix BACKGROUD_FIX_prepare -symbol USDJPY -side Bid -qty 50000000 -ordType Limit -px 105 -d 5
java -cp ${jarfile} baoying.orderbook.testtool.FirstQFJClientBatch -clientNum 1 -ratePerMinute 10 -client_prefix BACKGROUD_FIX_prepare -symbol USDJPY -side Bid -qty 50000000 -ordType Limit -px 106 -d 5
java -cp ${jarfile} baoying.orderbook.testtool.FirstQFJClientBatch -clientNum 1 -ratePerMinute 10 -client_prefix BACKGROUD_FIX_prepare -symbol USDJPY -side Bid -qty 50000000 -ordType Limit -px 107 -d 5
java -cp ${jarfile} baoying.orderbook.testtool.FirstQFJClientBatch -clientNum 1 -ratePerMinute 10 -client_prefix BACKGROUD_FIX_prepare -symbol USDJPY -side Bid -qty 50000000 -ordType Limit -px 108 -d 5
java -cp ${jarfile} baoying.orderbook.testtool.FirstQFJClientBatch -clientNum 1 -ratePerMinute 10 -client_prefix BACKGROUD_FIX_prepare -symbol USDJPY -side Bid -qty 50000000 -ordType Limit -px 109 -d 5
java -cp ${jarfile} baoying.orderbook.testtool.FirstQFJClientBatch -clientNum 1 -ratePerMinute 10 -client_prefix BACKGROUD_FIX_prepare -symbol USDJPY -side Bid -qty 50000000 -ordType Limit -px 110 -d 5
java -cp ${jarfile} baoying.orderbook.testtool.FirstQFJClientBatch -clientNum 1 -ratePerMinute 10 -client_prefix BACKGROUD_FIX_prepare -symbol USDJPY -side Bid -qty 50000000 -ordType Limit -px 111 -d 5
java -cp ${jarfile} baoying.orderbook.testtool.FirstQFJClientBatch -clientNum 1 -ratePerMinute 10 -client_prefix BACKGROUD_FIX_prepare -symbol USDJPY -side Bid -qty 50000000 -ordType Limit -px 112 -d 5
java -cp ${jarfile} baoying.orderbook.testtool.FirstQFJClientBatch -clientNum 1 -ratePerMinute 10 -client_prefix BACKGROUD_FIX_prepare -symbol USDJPY -side Bid -qty 50000000 -ordType Limit -px 113 -d 5
java -cp ${jarfile} baoying.orderbook.testtool.FirstQFJClientBatch -clientNum 1 -ratePerMinute 10 -client_prefix BACKGROUD_FIX_prepare -symbol USDJPY -side Bid -qty 50000000 -ordType Limit -px 114 -d 5
java -cp ${jarfile} baoying.orderbook.testtool.FirstQFJClientBatch -clientNum 1 -ratePerMinute 10 -client_prefix BACKGROUD_FIX_prepare -symbol USDJPY -side Bid -qty 50000000 -ordType Limit -px 115 -d 5
java -cp ${jarfile} baoying.orderbook.testtool.FirstQFJClientBatch -clientNum 1 -ratePerMinute 10 -client_prefix BACKGROUD_FIX_prepare -symbol USDJPY -side Bid -qty 50000000 -ordType Limit -px 116 -d 5


java -cp ${jarfile} baoying.orderbook.testtool.FirstQFJClientBatch -clientNum 1 -ratePerMinute 10 -client_prefix BACKGROUD_FIX_prepare -symbol USDJPY -side Offer -qty 50000000 -ordType Limit -px 122 -d 5
java -cp ${jarfile} baoying.orderbook.testtool.FirstQFJClientBatch -clientNum 1 -ratePerMinute 10 -client_prefix BACKGROUD_FIX_prepare -symbol USDJPY -side Offer -qty 50000000 -ordType Limit -px 123 -d 5
java -cp ${jarfile} baoying.orderbook.testtool.FirstQFJClientBatch -clientNum 1 -ratePerMinute 10 -client_prefix BACKGROUD_FIX_prepare -symbol USDJPY -side Offer -qty 50000000 -ordType Limit -px 124 -d 5
java -cp ${jarfile} baoying.orderbook.testtool.FirstQFJClientBatch -clientNum 1 -ratePerMinute 10 -client_prefix BACKGROUD_FIX_prepare -symbol USDJPY -side Offer -qty 50000000 -ordType Limit -px 125 -d 5
java -cp ${jarfile} baoying.orderbook.testtool.FirstQFJClientBatch -clientNum 1 -ratePerMinute 10 -client_prefix BACKGROUD_FIX_prepare -symbol USDJPY -side Offer -qty 50000000 -ordType Limit -px 126 -d 5
java -cp ${jarfile} baoying.orderbook.testtool.FirstQFJClientBatch -clientNum 1 -ratePerMinute 10 -client_prefix BACKGROUD_FIX_prepare -symbol USDJPY -side Offer -qty 50000000 -ordType Limit -px 127 -d 5
java -cp ${jarfile} baoying.orderbook.testtool.FirstQFJClientBatch -clientNum 1 -ratePerMinute 10 -client_prefix BACKGROUD_FIX_prepare -symbol USDJPY -side Offer -qty 50000000 -ordType Limit -px 128 -d 5
java -cp ${jarfile} baoying.orderbook.testtool.FirstQFJClientBatch -clientNum 1 -ratePerMinute 10 -client_prefix BACKGROUD_FIX_prepare -symbol USDJPY -side Offer -qty 50000000 -ordType Limit -px 129 -d 5
java -cp ${jarfile} baoying.orderbook.testtool.FirstQFJClientBatch -clientNum 1 -ratePerMinute 10 -client_prefix BACKGROUD_FIX_prepare -symbol USDJPY -side Offer -qty 50000000 -ordType Limit -px 130 -d 5
java -cp ${jarfile} baoying.orderbook.testtool.FirstQFJClientBatch -clientNum 1 -ratePerMinute 10 -client_prefix BACKGROUD_FIX_prepare -symbol USDJPY -side Offer -qty 50000000 -ordType Limit -px 131 -d 5
java -cp ${jarfile} baoying.orderbook.testtool.FirstQFJClientBatch -clientNum 1 -ratePerMinute 10 -client_prefix BACKGROUD_FIX_prepare -symbol USDJPY -side Offer -qty 50000000 -ordType Limit -px 132 -d 5
java -cp ${jarfile} baoying.orderbook.testtool.FirstQFJClientBatch -clientNum 1 -ratePerMinute 10 -client_prefix BACKGROUD_FIX_prepare -symbol USDJPY -side Offer -qty 50000000 -ordType Limit -px 133 -d 5
java -cp ${jarfile} baoying.orderbook.testtool.FirstQFJClientBatch -clientNum 1 -ratePerMinute 10 -client_prefix BACKGROUD_FIX_prepare -symbol USDJPY -side Offer -qty 50000000 -ordType Limit -px 134 -d 5
java -cp ${jarfile} baoying.orderbook.testtool.FirstQFJClientBatch -clientNum 1 -ratePerMinute 10 -client_prefix BACKGROUD_FIX_prepare -symbol USDJPY -side Offer -qty 50000000 -ordType Limit -px 135 -d 5


#background
background_rate_per_min_single_side=$((${background_rate_per_min}/2))
java -cp ${jarfile} baoying.orderbook.testtool.FirstQFJClientBatch -clientNum 1 -ratePerMinute ${background_rate_per_min_single_side} -client_prefix BACKGROUD_FIX -symbol USDJPY -side Bid   -qty 2 -ordType Market -d ${duration_in_second} &
java -cp ${jarfile} baoying.orderbook.testtool.FirstQFJClientBatch -clientNum 1 -ratePerMinute ${background_rate_per_min_single_side} -client_prefix BACKGROUD_FIX -symbol USDJPY -side Offer -qty 2 -ordType Market -d ${duration_in_second} &

#latency
latency_rate_per_min_single_side=$((60*1))
java -cp ${jarfile} baoying.orderbook.testtool.FirstQFJClientBatch -clientNum 1 -ratePerMinute ${latency_rate_per_min_single_side} -client_prefix 'LTC$$_FIX_B' -symbol USDJPY -side Bid   -qty 2 -ordType Market -d ${duration_in_second} &
java -cp ${jarfile} baoying.orderbook.testtool.FirstQFJClientBatch -clientNum 1 -ratePerMinute ${latency_rate_per_min_single_side} -client_prefix 'LTC$$_FIX_O' -symbol USDJPY -side Offer -qty 2 -ordType Market -d ${duration_in_second} &
