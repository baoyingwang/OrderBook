#!/bin/bash

jarfile=$1
if [[ ! -e $jarfile ]]; then
    echo "ERROR not found jar file:${jarfile}"
    echo "usage $0 jarfile"
    exit 1
fi

duration_in_seconds=600
for rate_per_second in 50 100 200 500 1000 2000 3000 4000 5000
#for rate_per_second in 5000
do
#	bash run_multi_scenarios.sh BaoyingOrderBookFat-2017-12-28_134458.706-all.jar  \
#	"Disruptor,BusySpinWaitStrategy,${rate_per_second},${duration_in_seconds};Disruptor,SleepingWaitStrategy,${rate_per_second},${duration_in_seconds};BlockingQueue,X,${rate_per_second},${duration_in_seconds}"
	bash run_multi_scenarios.sh $jarfile  \
	"Disruptor,BusySpinWaitStrategy,${rate_per_second},${duration_in_seconds};BlockingQueue,X,${rate_per_second},${duration_in_seconds}"
done
