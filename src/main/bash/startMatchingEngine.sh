#!/usr/bin/env bash

SCRIPT_START_DIR=`pwd`
DIRNAMECMD="/usr/bin/dirname"
MYSCRIPTDIR=`(cd \`${DIRNAMECMD} ${0}\` ; echo \`pwd\`)`
APP_HOME=$(cd $MYSCRIPTDIR/.. ; echo  $(pwd))

function startMachineEngine(){

    local tmp_symbols=$1
    local tmp_jarfile=$2

    if [[ -z "$tmp_symbols" ]]; then
        echo "ERROR - empty symbol list"
        exit -1
    fi

    if [[ ! -e $tmp_jarfile ]]; then
        echo "ERROR - not found jarfile:$tmp_jarfile"
        exit -1
    fi

    mkdir -p ${APP_HOME}/log
    arguments="--symbols ${tmp_symbols} --snapshot_interval_in_second 1"
    #VisualVMOptions="-Dcom.sun.management.jmxremote.port=3333  -Dcom.sun.management.jmxremote.ssl=false  -Dcom.sun.management.jmxremote.authenticate=false"
    GCPrintOptions="-XX:+PrintGCDetails -XX:+PrintGCTimeStamps -XX:+PrintGCDateStamps -XX:+PrintGC -XX:+PrintGCApplicationStoppedTime -XX:+PrintGCDateStamps -XX:+PrintGCDetails -XX:+PrintGCTimeStamps -Xloggc:${APP_HOME}/log/GC.txt"
    JVMOptions=" -Xmx3072M -Xms3072M ${VisualVMOptions} -XX:+PrintSafepointStatistics -XX:-UseBiasedLocking -XX:+UnlockDiagnosticVMOptions -XX:GuaranteedSafepointInterval=300000 -XX:NewRatio=1 -XX:+UseParNewGC -XX:+UseConcMarkSweepGC ${GCPrintOptions}"
    java $JVMOptions -jar  ${tmp_jarfile} ${arguments} | tee ${APP_HOME}/log/MatchingEngine.console.log

}




cd $APP_HOME
symbols=${1:-USDJPY} #e.g. USDJPY,USDHKD
jarfile=${APP_HOME}/jars/BaoyingOrderBookFat.jar

echo "Matching Engine APP_HOME:${APP_HOME}"
echo "starting matching engine for symbols:$symbols "
echo "jar file ${jarfile}"
startMachineEngine $symbols $jarfile