#!/bin/bash

SCRIPT_START_DIR=`pwd`
DIRNAMECMD="/usr/bin/dirname"
MYSCRIPTDIR=`(cd \`${DIRNAMECMD} ${0}\` ; echo \`pwd\`)`


jarfile=$SCRIPT_START_DIR/$1
#background_rate_per_second,duration_in_second,latency_rate_per_min;another case;another case
cases_as_string=${2:-"50,600,60"}

interfaceType=${3:-TCP}
if [[ "$interfaceType" == "TCP" ]]; then
    testToolMainClass=baoying.orderbook.testtool.vertx.VertxClientRoundBatch
else
    testToolMainClass=baoying.orderbook.testtool.qfj.FirstQFJClientBatch
fi


IFS=';' read -r -a test_cases <<< "$cases_as_string"
for test_case in "${test_cases[@]}"
do
    cd ${MYSCRIPTDIR}

    echo "input : $test_case"
    echo "test class $testToolMainClass "

    background_rate_per_second=$(echo $test_case | cut -d',' -f1)
    background_rate_per_min=$((60*$background_rate_per_second))
    duration_in_second=$(echo $test_case | cut -d',' -f2)

    latency_rate_per_min=$(echo $test_case | cut -d',' -f3)
    latency_rate_per_min=${latency_rate_per_min:-60}
    test_name=${interfaceType}_bg${background_rate_per_second}pS_lt${latency_rate_per_min}pM_${duration_in_second}sec_$(date '+%Y%m%d_%H%M%S')

    echo "$test_name"

    bash run_single_scenario.sh ${test_name} ${jarfile} ${background_rate_per_min} ${duration_in_second} ${latency_rate_per_min} ${testToolMainClass}

    cd ${MYSCRIPTDIR}
    zip -r zip_${test_name}.zip ${test_name}
    rm -rf ${test_name}

done
