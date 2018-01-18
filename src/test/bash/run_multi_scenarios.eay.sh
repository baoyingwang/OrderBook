#!/bin/bash

jarfile=${1:-"../jars/BaoyingOrderBookFat.jar"}
if [[ ! -e $jarfile ]]; then
    echo "ERROR not found jar file:${jarfile}"
    echo "usage $0 jarfile"
    exit 1
fi

#testToolMainClass=${2:-"baoying.orderbook.testtool.vertx.VertxClientRoundBatch"}
#testToolMainClass=${2:-"baoying.orderbook.testtool.qfj.FirstQFJClientBatch"}
#TCP or  FIX
interfaceType=${2:-TCP}

duration_in_seconds=600
for bg_rate_per_second in 50 500 1000 5000 10000 20000 40000 50000 60000
#for bg_rate_per_second in 50 1000 5000
do
    #for lt_rate_per_min in 5 10 50 100
    for lt_rate_per_min in 60
    do
	    bash run_multi_scenarios.sh $jarfile "${bg_rate_per_second},${duration_in_seconds},${lt_rate_per_min}" $interfaceType
	done
done




bash post_parsing_all_zipped_output.sh