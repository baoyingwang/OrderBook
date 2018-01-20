#!/bin/bash

#bash $0 bg3000perMin_$(date '+%Y%m%d_%H%M%S')   ${jarfile} $((60*100)) 600

SCRIPT_START_DIR=`pwd`
DIRNAMECMD="/usr/bin/dirname"
MYSCRIPTDIR=`(cd \`${DIRNAMECMD} ${0}\` ; echo \`pwd\`)`

function startMachineEngine(){
    mkdir -p ${test_name}/log
    cd ${test_name}

    arguments="--symbols USDJPY --snapshot_interval_in_second 3600"
    #VisualVMOptions="-Dcom.sun.management.jmxremote.port=3333  -Dcom.sun.management.jmxremote.ssl=false  -Dcom.sun.management.jmxremote.authenticate=false"
    GCPrintOptions="-XX:+PrintGCDetails -XX:+PrintGCTimeStamps -XX:+PrintGCDateStamps -XX:+PrintGC -XX:+PrintGCApplicationStoppedTime -XX:+PrintGCDateStamps -XX:+PrintGCDetails -XX:+PrintGCTimeStamps -Xloggc:log/GC.txt"
    JVMOptions="${VisualVMOptions} -XX:+PrintSafepointStatistics -XX:-UseBiasedLocking -XX:+UnlockDiagnosticVMOptions -XX:GuaranteedSafepointInterval=300000 -XX:NewRatio=1 -XX:+UseParNewGC -XX:+UseConcMarkSweepGC -Xmx3072M -Xms3072M ${GCPrintOptions}"
    java $JVMOptions -jar  ${jarfile} ${arguments} | tee log/MatchingEngine.console.log &

    echo "$(date '+%Y%m%d_%H%M%S') sleep 10 seconds to wait matching up initialize done"
    sleep 10
}

function populateOB(){

    local side=${1:-Bid}
    local symbol=${2:-USDJPY}
    local base_px=${3:-110}

    local price_delta_sign
    if [[ $side == Bid ]]; then
        price_delta_sign="-"
    else
        price_delta_sign="+"
    fi

    echo "$(date '+%Y%m%d_%H%M%S') begin preparing big orders to book, for later test orders,side: ${side}, symbol:${symbol}, base_px:${base_px}, px_sign:${price_delta_sign}"

    local tmp_duration=5
    for delta in {1..2}
    do
        USDJPY_base_px=110
        USDJPY_px_for_book=$(($base_px $price_delta_sign $delta))

        local prices="${USDJPY_px_for_book}.1,${USDJPY_px_for_book}.2,${USDJPY_px_for_book}.3,${USDJPY_px_for_book}.4,${USDJPY_px_for_book}.5"

        java $JVMOptionsTestClient -cp ${jarfile} ${testToolMainClass} -clientNum 1 -ratePerMinute 60 -client_prefix BACKGROUND_FIX_OB_${side}_$px -symbol $symbol -side $side -qty 1000000000 -ordType Limit -prices ${prices} -d ${tmp_duration} &
    done

    sleep $(($tmp_duration + 3))
}


function startSendingMarketOrder(){

    local tmp_clientPrefix=$1 #local tmp_clientPrefix="BACKGROUND_FIX_B"
    local tmp_client_num=$2
    local tmp_rate_per_min=$3
    local tmp_duration_in_second=$4
    local tmp_sides=$5
    local tmp_symbol=$6
    local tmp_qty=$7
    local tmp_ordType=$8
    if [[ ${tmp_rate_per_min} -eq 0 || ${tmp_client_num} -eq 0 ]]; then
        echo "$(date '+%Y%m%d_%H%M%S') not start order sending, since rate:${tmp_rate_per_min}, or clientNum:${tmp_client_num} is 0"
        return
    fi

    echo            "$(date '+%Y%m%d_%H%M%S') begin sending orders - -clientNum ${tmp_client_num} -ratePerMinute ${tmp_rate_per_min} -client_prefix ${tmp_clientPrefix} -symbol ${tmp_symbol} -sides ${tmp_sides}   -qty ${tmp_qty} -ordType ${tmp_ordType} -d ${tmp_duration_in_second}"
    java ${JVMOptionsTestClient} -cp ${jarfile} ${testToolMainClass} -clientNum ${tmp_client_num} -ratePerMinute ${tmp_rate_per_min} -client_prefix ${tmp_clientPrefix} -symbol ${tmp_symbol} -sides ${tmp_sides}   -qty ${tmp_qty} -ordType ${tmp_ordType} -d ${tmp_duration_in_second} &
}


function startBTrace(){

    sampleMean=$1

    case $OSTYPE in
        linux*)
            ps -ef | grep btrace.monitor.sh | grep -v grep | sed 's/^ *//;s/ *$//'| cut -d' ' -f1 | xargs kill -9
            ;;
        msys*)
            #TODO: not sure how to kill btrace.monitor.sh on windows
            #cmd "/C TASKKILL /F /IM java.exe /T"
            ;;
    esac
    bash $MYSCRIPTDIR/btrace.monitor.sh $sampleMean &

}

case $OSTYPE in
	linux*) 
		#clean exist process
		#trim the line is required(by sed), otherwise the -f1 maybe empty for align issue(e.g. pid 23 and 12345)
		ps -eo pid,user,comm,pcpu | grep java | grep -v grep | sed 's/^ *//;s/ *$//'| cut -d' ' -f1 | xargs kill -9
		;;
	msys*)
	
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
latency_rate_per_min=${4:-60}
duration_in_second=$5
testToolMainClass=${6:-"baoying.orderbook.testtool.vertx.VertxClientRoundBatch"}
bg_client_num=${7:-1}
lt_client_num=${8:-1}

startMachineEngine

JVMOptionsTestClient="-Xmx256M -Dlog4j.configurationFile=log4j2_testtool.xml"
populateOB Bid   USDJPY 110
populateOB Offer USDJPY 110

warmup_druation_in_second=5
startSendingMarketOrder BACKGROUND_FIX_WMUP 5 $background_rate_per_min ${warmup_druation_in_second} "Bid,Offer"  USDJPY 2 Market
sleep $(($warmup_druation_in_second + 2))

if (( $background_rate_per_min > $latency_rate_per_min )); then
    sampleMeanBaseRatePerMin=$background_rate_per_min
else
    sampleMeanBaseRatePerMin=$latency_rate_per_min
fi
startBTrace $(( $sampleMeanBaseRatePerMin / 600 )) #sample 10 values per second
sleep 5 #wait bind done, TODO: bind while jvm start? why and why not?

startSendingMarketOrder BACKGROUND_FIX ${bg_client_num} $background_rate_per_min $duration_in_second "Bid,Offer"  USDJPY 2 Market
startSendingMarketOrder LxTxCx_FIX_RT_ ${lt_client_num} $latency_rate_per_min    $duration_in_second "Bid,Offer"  USDJPY 2 Market

echo "$(date '+%Y%m%d_%H%M%S') reset test data, after wait several seconds - the fix session setup will wait several seconds"
sleep 3
curl http://localhost:8080/matching/reset_test_data | cut -c1-150

for i in {1..10}
do
	echo "$(date '+%Y%m%d_%H%M%S') $i/10 - ${test_name} sleep every $(($duration_in_second/10)) seconds before exit"
	sleep $(($duration_in_second/10))

done


curl http://localhost:8080/matching/get_test_summary > log/engine_statistics.json.txt

#sleep a while to make sure the latency test tool writes the data to disk in time
sleep 3


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

