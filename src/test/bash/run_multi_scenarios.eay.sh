#!/bin/bash

jarfile=${1:-"../jars/BaoyingOrderBookFat.jar"}
if [[ ! -e $jarfile ]]; then
    echo "ERROR not found jar file:${jarfile}"
    echo "usage $0 jarfile"
    exit 1
fi

duration_in_seconds=600
#for bg_rate_per_second in 50 100 200 500 1000 2000 3000 4000 5000
for bg_rate_per_second in 0
do

#	bash run_multi_scenarios.sh BaoyingOrderBookFat-2017-12-28_134458.706-all.jar  \
#	"Disruptor,BusySpinWaitStrategy,${bg_rate_per_second},${duration_in_seconds};Disruptor,SleepingWaitStrategy,${bg_rate_per_second},${duration_in_seconds};BlockingQueue,X,${bg_rate_per_second},${duration_in_seconds}"
    for lt_rate_per_min in 5 10 50 100
    do
	    bash run_multi_scenarios.sh $jarfile  \
	    "Disruptor,BusySpinWaitStrategy,${bg_rate_per_second},${duration_in_seconds},${lt_rate_per_min};BlockingQueue,X,${bg_rate_per_second},${duration_in_seconds},${lt_rate_per_min}"
	done
done
