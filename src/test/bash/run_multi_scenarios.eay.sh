#!/bin/bash


SCRIPT_START_DIR=`pwd`
DIRNAMECMD="/usr/bin/dirname"
MYSCRIPTDIR=`(cd \`${DIRNAMECMD} ${0}\` ; echo \`pwd\`)`

function run_single_case(){

    jarfile_full_path=$1
    bg_rate_per_second=$2
    lt_rate_per_second=$3
    duration_in_seconds=$4
    interfaceType=${5:-TCP}

    test_name=${interfaceType}_bg${bg_rate_per_second}perS_lt${lt_rate_per_second}perS_${duration_in_seconds}S_$(date '+%Y%m%d_%H%M%S')
    echo "$test_name on jar: $jarfile_full_path"

    background_rate_per_min=$(($bg_rate_per_second*60))
    latency_rate_per_min=$(($lt_rate_per_second*60))
    if [[ "$interfaceType" == "TCP" ]]; then
        testToolMainClass=baoying.orderbook.testtool.vertx.VertxClientRoundBatch
    else
        testToolMainClass=baoying.orderbook.testtool.qfj.FirstQFJClientBatch
    fi


    bash run_single_scenario.sh ${test_name} ${jarfile_full_path} ${background_rate_per_min} ${latency_rate_per_min} ${duration_in_seconds} ${testToolMainClass}

    cd ${MYSCRIPTDIR}
    zip -r zip_${test_name}.zip ${test_name}
    rm -rf ${test_name}

}


jarfile=${1:-"../jars/BaoyingOrderBookFat.jar"}
if [[ ! -e $jarfile ]]; then
    echo "ERROR not found jar file:${jarfile}, pwd:$(pwd)"
    echo "usage $0 jarfile"
    exit 1
fi
jarfile_full_path=$(readlink -f $jarfile)
interfaceType=${2:-TCP}  #TCP or  FIX


cd ${MYSCRIPTDIR}
duration_in_seconds=600
for bg_rate_per_second in 50 500 1000 5000 10000 20000 40000 50000 60000
#for bg_rate_per_second in 50 1000 5000
do
    #for lt_rate_per_second in 1 2 5
    for lt_rate_per_second in 1
    do
	    run_single_case $jarfile_full_path ${bg_rate_per_second} ${lt_rate_per_second} ${duration_in_seconds} $interfaceType
	done
done


bash post_parsing_all_zipped_output.sh