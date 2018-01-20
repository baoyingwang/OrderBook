#!/bin/bash


SCRIPT_START_DIR=`pwd`
DIRNAMECMD="/usr/bin/dirname"
MYSCRIPTDIR=`(cd \`${DIRNAMECMD} ${0}\` ; echo \`pwd\`)`

function run_single_case(){

    local tmp_jarfile_full_path=$1
    local tmp_bg_rate_per_second=$2
    local tmp_lt_rate_per_second=$3
    local tmp_duration_in_seconds=$4
    local tmp_interfaceType=${5:-TCP}

    local tmp_test_name=${tmp_interfaceType}_bg${tmp_bg_rate_per_second}.${bg_client_num}_lt${tmp_lt_rate_per_second}.${lt_client_num}_${tmp_duration_in_seconds}S_$(date '+%Y%m%d_%H%M%S')
    echo "$tmp_test_name on jar: $tmp_jarfile_full_path"

    local tmp_background_rate_per_min=$(($tmp_bg_rate_per_second*60))
    local tmp_latency_rate_per_min=$(($tmp_lt_rate_per_second*60))
    if [[ "$tmp_interfaceType" == "TCP" ]]; then
        testToolMainClass=baoying.orderbook.testtool.vertx.VertxClientRoundBatch
    else
        testToolMainClass=baoying.orderbook.testtool.qfj.FirstQFJClientBatch
    fi


    bash run_single_scenario.sh ${tmp_test_name} ${tmp_jarfile_full_path} ${tmp_background_rate_per_min} ${tmp_latency_rate_per_min} ${tmp_duration_in_seconds} ${testToolMainClass}

    cd ${MYSCRIPTDIR}
    zip -r zip_${tmp_test_name}.zip ${tmp_test_name}
    rm -rf ${tmp_test_name}

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

declare -a bg_client_num_array=(1 10 100 500)
declare -a lt_client_num_array=(1)
declare -a bg_rate_per_second_array=(50 500 1000 5000 50000)
declare -a lt_rate_per_second_array=(1)

for bg_client_num in "${bg_client_num_array[@]}"
do
	for lt_client_num in "${lt_client_num_array[@]}"
	do
		for bg_rate_per_second in "${bg_rate_per_second_array[@]}"
		do
			#for lt_rate_per_second in 1 2 5
			for lt_rate_per_second in "${lt_rate_per_second_array[@]}"
			do
				run_single_case $jarfile_full_path ${bg_rate_per_second} ${lt_rate_per_second} ${duration_in_seconds} $interfaceType ${bg_client_num} ${lt_client_num}
			done
		done

	done
done


bash post_parsing_all_zipped_output.sh