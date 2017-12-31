#!/bin/bash

#bash $0 Disruptor_BusySpinWaitStrategy_$(date '+%Y%m%d_%H%M%S') ${jarfile} Disruptor  BusySpinWaitStrategy   $((60*100)) 600 
#bash $0 Disruptor_SleepingWaitStrategy_$(date '+%Y%m%d_%H%M%S') ${jarfile} Disruptor  SleepingWaitStrategy   $((60*100)) 600 
#bash $0 Disruptor_YieldingWaitStrategy_$(date '+%Y%m%d_%H%M%S') ${jarfile} Disruptor  YieldingWaitStrategy   $((60*100)) 600 
#bash $0 BlockingQueue_X_bg3000perMin_$(date '+%Y%m%d_%H%M%S')   ${jarfile} BlockingQueue  X                  $((60*100)) 600 

#clean exist process
#trim the line is required(by sed), otherwise the -f1 maybe empty for align issue(e.g. pid 23 and 12345)
ps -eo pid,user,comm,pcpu | grep java | grep -v grep | sed 's/^ *//;s/ *$//'| cut -d' ' -f1 | xargs kill -9

vmstat_count=$(ps -ef |grep "vmstat 5 -t" | grep -v grep | wc -l)
if [[ $vmstat_count -lt 1 ]]; then
        echo "start vmstat, since not yet exist "
        vmstat 5 -t >> vmstat_since$(date '+%Y%m%d').log &
fi

test_name=$1
jarfile=$2
queue_type=$3
strategy=$4
background_rate_per_min=$5
duration_in_second=$6


mkdir -p ${test_name}/log
cd ${test_name}
arguments="--queue_type ${queue_type} --strategy ${strategy} --symbols USDJPY --queue_size 65536"
mkdir -p log
VisualVMOptions="-Dcom.sun.management.jmxremote.port=3333  -Dcom.sun.management.jmxremote.ssl=false  -Dcom.sun.management.jmxremote.authenticate=false"
GCPrintOptions="-XX:+PrintGCDetails -XX:+PrintGCTimeStamps -XX:+PrintGCDateStamps -XX:+PrintGC -XX:+PrintGCApplicationStoppedTime -XX:+PrintGCDateStamps -XX:+PrintGCDetails -XX:+PrintGCTimeStamps -Xloggc:log/GC.txt"
JVMOptions="${VisualVMOptions} -XX:NewRatio=1 -XX:+UseParNewGC -XX:+UseConcMarkSweepGC -Xmx3072M -Xms3072M ${GCPrintOptions}"
java $JVMOptions -jar  ${jarfile} ${arguments} | tee log/${test_name}.console.log &

echo "sleep 10 seconds to wait matching up initialize done"
sleep 10


echo "$(date '+%Y%m%d_%H%M%S') begin preparing big orders to book, for later background orders and latency orders"
JVMOptions_popOB="-Xmx64M -Dlog4j.configurationFile=log4j2_testtool.xml"
for delta in {1..2}
do
	USDJPY_base_px=110
	USDJPY_px_for_book=$(($USDJPY_base_px-$delta))
	for fraction_party in ".0" ".1" ".2" ".3" ".4" ".5" ".6" ".7" ".8" ".9"
	do
		px="$USDJPY_px_for_book$fraction_party"
		java $JVMOptions_popOB -cp ${jarfile} baoying.orderbook.testtool.FirstQFJClientBatch -clientNum 1 -ratePerMinute 10 -client_prefix BACKGROUND_FIX_prepare -symbol USDJPY -side Bid -qty 1000000000 -ordType Limit -px ${px} -d 3 &
	done
	sleep 20
done

echo "$(date '+%Y%m%d_%H%M%S') Bid side book done"

for delta in {1..2}
do
	USDJPY_base_px=110
	USDJPY_px_for_book=$(($USDJPY_base_px+$delta))
	for fraction_party in ".0" ".1" ".2" ".3" ".4" ".5" ".6" ".7" ".8" ".9"
	do
		px="$USDJPY_px_for_book$fraction_party"
		java $JVMOptions_popOB -cp ${jarfile} baoying.orderbook.testtool.FirstQFJClientBatch -clientNum 1 -ratePerMinute 10 -client_prefix BACKGROUND_FIX_prepare -symbol USDJPY -side Offer -qty 1000000000 -ordType Limit -px ${px} -d 3 &
	done
	
	sleep 20
done
echo "$(date '+%Y%m%d_%H%M%S') Offer side book done"


JVMOptions_sending="-Xmx128M -Dlog4j.configurationFile=log4j2_testtool.xml"
echo "begin seding background orders"
background_rate_per_min_single_side=$((${background_rate_per_min}/2))
java ${JVMOptions_sending} -cp ${jarfile} baoying.orderbook.testtool.FirstQFJClientBatch -clientNum 1 -ratePerMinute ${background_rate_per_min_single_side} -client_prefix BACKGROUND_FIX -symbol USDJPY -side Bid   -qty 2 -ordType Market -d ${duration_in_second} &
java ${JVMOptions_sending} -cp ${jarfile} baoying.orderbook.testtool.FirstQFJClientBatch -clientNum 1 -ratePerMinute ${background_rate_per_min_single_side} -client_prefix BACKGROUND_FIX -symbol USDJPY -side Offer -qty 2 -ordType Market -d ${duration_in_second} &

echo "begin sending latency orders"
latency_rate_per_min_single_side=$((60*1))
java ${JVMOptions_sending} -cp ${jarfile} baoying.orderbook.testtool.FirstQFJClientBatch -clientNum 1 -ratePerMinute ${latency_rate_per_min_single_side} -client_prefix 'LxTxCx_FIX_B' -symbol USDJPY -side Bid   -qty 2 -ordType Market -d ${duration_in_second} &
java ${JVMOptions_sending} -cp ${jarfile} baoying.orderbook.testtool.FirstQFJClientBatch -clientNum 1 -ratePerMinute ${latency_rate_per_min_single_side} -client_prefix 'LxTxCx_FIX_O' -symbol USDJPY -side Offer -qty 2 -ordType Market -d ${duration_in_second} &


echo "reset test data, after wait 5 seconds(the fix session setup will wait several seconds)"
sleep 5
curl http://localhost:8080/matching/reset_test_data | cut -c1-150

for i in {1..10}
do
	echo "$i/10 - ${test_name} sleep every $(($duration_in_second/10)) seconds, and trigger the dump latency data later"
	sleep $(($duration_in_second/10))
	curl http://localhost:8080/matching/get_test_summary | cut -c1-150
done


#trim the line is required(by sed), otherwise the -f1 maybe empty for align issue(e.g. pid 23 and 12345)
ps -eo pid,user,comm,pcpu | grep java | grep -v grep | sed 's/^ *//;s/ *$//'| cut -d' ' -f1 | xargs kill -9
