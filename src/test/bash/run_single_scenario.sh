#!/bin/bash

#bash $0 bg3000perMin_$(date '+%Y%m%d_%H%M%S')   ${jarfile} $((60*100)) 600

SCRIPT_START_DIR=`pwd`
DIRNAMECMD="/usr/bin/dirname"
MYSCRIPTDIR=`(cd \`${DIRNAMECMD} ${0}\` ; echo \`pwd\`)`

source e2e.test.functions.sh

function startMachineEngine(){

    arguments="--symbols USDJPY --snapshot_interval_in_second 3600"
    #VisualVMOptions="-Dcom.sun.management.jmxremote.port=3333  -Dcom.sun.management.jmxremote.ssl=false  -Dcom.sun.management.jmxremote.authenticate=false"
    GCPrintOptions="-XX:+PrintGCDetails -XX:+PrintGCTimeStamps -XX:+PrintGCDateStamps -XX:+PrintGC -XX:+PrintGCApplicationStoppedTime -XX:+PrintGCDateStamps -XX:+PrintGCDetails -XX:+PrintGCTimeStamps -Xloggc:log/GC.txt"
    JVMOptions="${VisualVMOptions} -XX:+PrintSafepointStatistics -XX:-UseBiasedLocking -XX:+UnlockDiagnosticVMOptions -XX:GuaranteedSafepointInterval=300000 -XX:NewRatio=1 -XX:+UseParNewGC -XX:+UseConcMarkSweepGC -Xmx3072M -Xms3072M ${GCPrintOptions}"
    java $JVMOptions -jar  ${jarfile} ${arguments} | tee log/MatchingEngine.console.log &

    echo "$(date '+%Y%m%d_%H%M%S') sleep 10 seconds to wait matching up initialize done"
    sleep 10
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



test_name=$1
jarfile=$2
background_rate_per_min=$3
latency_rate_per_min=${4:-60}
duration_in_second=$5
testToolMainClass=${6:-"baoying.orderbook.testtool.vertx.VertxClientRoundBatch"}
bg_client_num=${7:-1}
lt_client_num=${8:-1}


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

mkdir -p ${test_name}/log
cd ${test_name}
APP_HOME=$(pwd)

start_vmstat
startMachineEngine


JVMOptionsTestClient="-Xmx256M -Dlog4j.configurationFile=log4j2_testtool.xml"


echo "$(date '+%Y%m%d_%H%M%S') populate OB begin"
populateOB10Levels ${jarfile} Bid     USDJPY 110 1000000000
#populateOB10Levels ${jarfile} Bid     USDJPY 111 1000000000
populateOB10Levels ${jarfile} Offer   USDJPY 120 1000000000
#populateOB10Levels ${jarfile} Offer   USDJPY 121 1000000000
echo "$(date '+%Y%m%d_%H%M%S') populate OB done"


tmp_warmup_testclient_arguments=" -clientNum 1 -ratePerMinute $((60 * 1000)) -client_prefix BACKGROUND_FIX_WMUP_${RANDOM} -symbol USDJPY -side Bid,Offer -qty 2 -ordType Market -prices 0 -d 20"
echo "$(date '+%Y%m%d_%H%M%S') warmup begin"
sendOrders ${jarfile} ${testToolMainClass} "$tmp_warmup_testclient_arguments" "$JVMOptionsTestClient"
echo "$(date '+%Y%m%d_%H%M%S') warmup done"


if (( $background_rate_per_min > $latency_rate_per_min )); then
    sampleMeanBaseRatePerMin=$background_rate_per_min
else
    sampleMeanBaseRatePerMin=$latency_rate_per_min
fi
startBTrace $(( $sampleMeanBaseRatePerMin / 600 )) #sample 10 values per second
sleep 5 #wait bind done, TODO: bind while jvm start? why and why not?


tmp_bg_testclient_arguments=" -clientNum ${bg_client_num} -ratePerMinute ${background_rate_per_min} -client_prefix BACKGROUND_FIX_${RANDOM} -symbol USDJPY -side Bid,Offer -qty 2 -ordType Market -prices 0 -d ${duration_in_second}"
echo "$(date '+%Y%m%d_%H%M%S') background orders begin"
sendOrders ${jarfile} ${testToolMainClass} "$tmp_bg_testclient_arguments" "$JVMOptionsTestClient" &


tmp_lt_testclient_arguments=" -clientNum ${lt_client_num} -ratePerMinute ${latency_rate_per_min} -client_prefix LxTxCx_FIX_RT_${RANDOM} -symbol USDJPY -side Bid,Offer -qty 2 -ordType Market -prices 0 -d ${duration_in_second}"
echo "$(date '+%Y%m%d_%H%M%S') latency orders begin"
sendOrders ${jarfile} ${testToolMainClass} "$tmp_lt_testclient_arguments" "$JVMOptionsTestClient" &



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


