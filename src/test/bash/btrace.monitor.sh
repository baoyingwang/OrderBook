#!/usr/bin/env bash

SCRIPT_START_DIR=`pwd`
DIRNAMECMD="/usr/bin/dirname"
MYSCRIPTDIR=`(cd \`${DIRNAMECMD} ${0}\` ; echo \`pwd\`)`


sampleMean=${1:-150}

if [[ -z "${BTRACE_HOME}" ]]; then
    echo "ERROR pls define BTRACE_HOME"
    exit -1
fi

if [[ -z "${JAVA_HOME}" ]]; then
    echo "ERROR pls define JAVA_HOME"
    exit -1
fi

if [[ ! -e ${MYSCRIPTDIR}/btrace/baoying/orderbook/BTracePerformance.java.template ]]; then
	mv ${MYSCRIPTDIR}/btrace/baoying/orderbook/BTracePerformance.java ${MYSCRIPTDIR}/btrace/baoying/orderbook/BTracePerformance.java.template
fi

for (( ; ; ))
do
	me_pid=$(jps | grep BaoyingOrderBookFat.jar | cut -d ' ' -f1)
	if [[ -z "$me_pid" ]]; then
		echo "not found related pid, sleep a while then next check"
		sleep 5
	else
		echo "found $me_pid and try to attach it (sampleMean: ${sampleMean}) "


	case $OSTYPE in
		linux*)
		${BTRACE_HOME}/bin/btrace -u $me_pid ${MYSCRIPTDIR}/btrace/baoying/orderbook/BTracePerformance.java

		exit
		;;
	msys*)

		sed "s/final static int sampleMean = 100;/final static int sampleMean = ${sampleMean};/" ${MYSCRIPTDIR}/btrace/baoying/orderbook/BTracePerformance.java.template > ${MYSCRIPTDIR}/btrace/baoying/orderbook/BTracePerformance.java
		${BTRACE_HOME}/bin/btrace.bat -u $me_pid ${MYSCRIPTDIR}/btrace/baoying/orderbook/BTracePerformance.java

		echo "INFO btrace done on $me_pid"
		exit
	;;
	*)
		echo "unknown OSTYPE:$OSTYPE"
	;;
	esac

	fi

done