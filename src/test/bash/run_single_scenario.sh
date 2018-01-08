#!/bin/bash

#bash $0 bg3000perMin_$(date '+%Y%m%d_%H%M%S')   ${jarfile} $((60*100)) 600

function startMachineEngine(){
    mkdir -p ${test_name}/log
    cd ${test_name}

    arguments="--symbols USDJPY --snapshot_interval_in_second 3600"
    #VisualVMOptions="-Dcom.sun.management.jmxremote.port=3333  -Dcom.sun.management.jmxremote.ssl=false  -Dcom.sun.management.jmxremote.authenticate=false"
    GCPrintOptions="-XX:+PrintGCDetails -XX:+PrintGCTimeStamps -XX:+PrintGCDateStamps -XX:+PrintGC -XX:+PrintGCApplicationStoppedTime -XX:+PrintGCDateStamps -XX:+PrintGCDetails -XX:+PrintGCTimeStamps -Xloggc:log/GC.txt"
    JVMOptions="${VisualVMOptions} -XX:+PrintSafepointStatistics -XX:-UseBiasedLocking -XX:+UnlockDiagnosticVMOptions -XX:GuaranteedSafepointInterval=300000 -XX:NewRatio=1 -XX:+UseParNewGC -XX:+UseConcMarkSweepGC -Xmx3072M -Xms3072M ${GCPrintOptions}"
    java $JVMOptions -jar  ${jarfile} ${arguments} | tee log/MatchingEngine.console.log &

    echo "sleep 10 seconds to wait matching up initialize done"
    sleep 10
}

function populateOB(){
    echo "$(date '+%Y%m%d_%H%M%S') begin preparing big orders to book, for later test orders"
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


}

#latency_rate_per_min is used in this function - to be refactored
function warmupOrder(){

    local tmp_warmup_rate_per_min=$1
    if [[ tmp_warmup_rate_per_min -eq 0 ]]; then
        echo "not start warmup order sending, since rate is 0"
        return
    fi

    local tmp_warmup_duration_in_seconds=${2:-20}

    echo "begin sending warmup orders - ${tmp_warmup_duration_in_seconds} seconds - warmup bg rate:$tmp_warmup_rate_per_min per min, warmup latency rate:$latency_rate_per_min per min"
    local warmup_rate_per_min_single_side=$((${tmp_warmup_duration_in_seconds}/2))
    java ${JVMOptions_sending} -cp ${jarfile} baoying.orderbook.testtool.FirstQFJClientBatch -clientNum 1 -ratePerMinute ${warmup_rate_per_min_single_side} -client_prefix BACKGROUND_FIX -symbol USDJPY -side Bid   -qty 2 -ordType Market -d ${tmp_warmup_duration_in_seconds} &
    java ${JVMOptions_sending} -cp ${jarfile} baoying.orderbook.testtool.FirstQFJClientBatch -clientNum 1 -ratePerMinute ${warmup_rate_per_min_single_side} -client_prefix BACKGROUND_FIX -symbol USDJPY -side Offer -qty 2 -ordType Market -d ${tmp_warmup_duration_in_seconds} &

    java ${JVMOptions_sending} -cp ${jarfile} baoying.orderbook.testtool.FirstQFJClientBatch -clientNum 1 -ratePerMinute ${latency_rate_per_min} -client_prefix LxTxCx_FIX_WMUPB -symbol USDJPY -side Bid   -qty 2 -ordType Market -d ${tmp_warmup_duration_in_seconds} &
    java ${JVMOptions_sending} -cp ${jarfile} baoying.orderbook.testtool.FirstQFJClientBatch -clientNum 1 -ratePerMinute ${latency_rate_per_min} -client_prefix LxTxCx_FIX_WMUPO -symbol USDJPY -side Offer -qty 2 -ordType Market -d ${tmp_warmup_duration_in_seconds} &

    #addtional 20 seconds to 1) make sure FIX setup and exit 2) make sure all warmup messages are consumed
    sleep $(($tmp_warmup_duration_in_seconds + 20))
}

function startBackgroundOrder(){

    local tmp_background_rate_per_min=$1
    if [[ $tmp_background_rate_per_min -eq 0 ]]; then
        echo "not start background order sending, since rate is 0"
        return
    fi

    echo "begin sending background orders"
    local background_rate_per_min_single_side=$((${tmp_background_rate_per_min}/2))
    java ${JVMOptions_sending} -cp ${jarfile} baoying.orderbook.testtool.FirstQFJClientBatch -clientNum 1 -ratePerMinute ${background_rate_per_min_single_side} -client_prefix BACKGROUND_FIX -symbol USDJPY -side Bid   -qty 2 -ordType Market -d ${duration_in_second} &
    java ${JVMOptions_sending} -cp ${jarfile} baoying.orderbook.testtool.FirstQFJClientBatch -clientNum 1 -ratePerMinute ${background_rate_per_min_single_side} -client_prefix BACKGROUND_FIX -symbol USDJPY -side Offer -qty 2 -ordType Market -d ${duration_in_second} &
}

function startLatencyOrder(){

    local tmp_latency_rate_per_min=${1:-60}
    local tmp_latency_rate_per_min_single_side=$((${tmp_latency_rate_per_min}/2))
    echo "begin sending latency orders - tmp_latency_rate_per_min_single_side:${tmp_latency_rate_per_min_single_side}"

    java ${JVMOptions_sending} -cp ${jarfile} baoying.orderbook.testtool.FirstQFJClientBatch -clientNum 1 -ratePerMinute ${tmp_latency_rate_per_min_single_side} -client_prefix 'LxTxCx_FIX_RT_B' -symbol USDJPY -side Bid   -qty 2 -ordType Market -d ${duration_in_second} &
    java ${JVMOptions_sending} -cp ${jarfile} baoying.orderbook.testtool.FirstQFJClientBatch -clientNum 1 -ratePerMinute ${tmp_latency_rate_per_min_single_side} -client_prefix 'LxTxCx_FIX_RT_O' -symbol USDJPY -side Offer -qty 2 -ordType Market -d ${duration_in_second} &
}


case $OSTYPE in
	linux*) 
		#clean exist process
		#trim the line is required(by sed), otherwise the -f1 maybe empty for align issue(e.g. pid 23 and 12345)
		ps -eo pid,user,comm,pcpu | grep java | grep -v grep | sed 's/^ *//;s/ *$//'| cut -d' ' -f1 | xargs kill -9
		;;
	msis*)
	
		#tasklist  | grep java |  sed 's/^ *//;s/ *$//;s/\ \+/ /g' |cut -d' ' -f2 | xargs kill -9
		#taskkill /PID 10208 /F  the /PID is identified as :ERROR: Invalid argument/option - 'C:/baoying.wang/program/Git/PID'.
		#https://stackoverflow.com/questions/11865085/out-of-a-git-console-how-do-i-execute-a-batch-file-and-then-return-to-git-conso
		cmd "/C TASKKILL /F /IM java.exe /T"
		;;
esac		

case $OSTYPE in
	linux*) 
		vmstat_count=$(ps -ef |grep "vmstat 5 -t" | grep -v grep | wc -l)
		if [[ $vmstat_count -lt 1 ]]; then
				echo "start vmstat, since not yet exist "
				vmstat 5 -t >> vmstat_since$(date '+%Y%m%d').log &
		fi
		;;
	msys*)
		echo "***remember start the performance monitor manually***, since no vmstat on windows"
		;;
esac
	

test_name=$1
jarfile=$2
background_rate_per_min=$3
duration_in_second=$4
latency_rate_per_min=${5:-60}

startMachineEngine
populateOB

JVMOptions_sending="-Xmx128M -Dlog4j.configurationFile=log4j2_testtool.xml"

tmp_warmup_rate_per_min=$((60*3000))
tmp_warmup_duration_in_seconds=30
warmupOrder $tmp_warmup_rate_per_min $tmp_warmup_duration_in_seconds

startBackgroundOrder $background_rate_per_min
startLatencyOrder $latency_rate_per_min

echo "reset test data, after wait 5 seconds(the fix session setup will wait several seconds)"
sleep 5
curl http://localhost:8080/matching/reset_test_data | cut -c1-150

for i in {1..10}
do
	echo "$(date '+%Y%m%d_%H%M%S') $i/10 - ${test_name} sleep every $(($duration_in_second/10)) seconds before exit"
	sleep $(($duration_in_second/10))

done


case $OSTYPE in
	linux*) 
		#clean exist process
		#trim the line is required(by sed), otherwise the -f1 maybe empty for align issue(e.g. pid 23 and 12345)
		ps -eo pid,user,comm,pcpu | grep java | grep -v grep | sed 's/^ *//;s/ *$//'| cut -d' ' -f1 | xargs kill -9
		;;
	msys*)
		cmd "/C TASKKILL /F /IM java.exe /T"
		;;
esac	
