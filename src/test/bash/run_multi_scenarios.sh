#!/bin/bash

SCRIPT_START_DIR=`pwd`
DIRNAMECMD="/usr/bin/dirname"
MYSCRIPTDIR=`(cd \`${DIRNAMECMD} ${0}\` ; echo \`pwd\`)`


jarfile=$SCRIPT_START_DIR/$1
duration_in_seconds=${2:-300}


for background_rate_per_second in 50 200 500
do
	#how to use bash array - http://www.yourownlinux.com/2013/10/working-with-arrays-in-bash-scripting.html
	#loop bash array https://www.cyberciti.biz/faq/bash-for-loop-array/
	#bg_rate_per_second,duration,queue_type,strategy
	declare -a test_cases=( 
							"Disruptor,BusySpinWaitStrategy,${background_rate_per_second},${duration_in_seconds}" \
							"Disruptor,YieldingWaitStrategy,${background_rate_per_second},${duration_in_seconds}" \
							"Disruptor,SleepingWaitStrategy,${background_rate_per_second},${duration_in_seconds}" \
							"BlockingQueue,X,${background_rate_per_second},${duration_in_seconds}")
	for test_case in "${test_cases[@]}"
	do
		cd ${MYSCRIPTDIR}
		
		echo $test_case
		
		queue_type=$(echo $test_case | cut -d',' -f1)
		strategy=$(echo $test_case | cut -d',' -f2)
		background_rate_per_second=$(echo $test_case | cut -d',' -f3)
		background_rate_per_min=$((60*$background_rate_per_second))
		duration_in_second=$(echo $test_case | cut -d',' -f4)
		test_name=${queue_type}_${strategy}_bg${background_rate_per_second}perSec_$(date '+%Y%m%d_%H%M%S')_duration${duration_in_second}Sec

		echo "stop all java process, then start others for next test"

		bash run_single_scenario.sh ${test_name} ${jarfile} ${queue_type} ${strategy} ${background_rate_per_min} ${duration_in_second} 
		
		cd ${MYSCRIPTDIR}
		zip -r zip_${test_name}.zip ${test_name}
		rm -rf ${test_name}

	done
done