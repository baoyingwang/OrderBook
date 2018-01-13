#!/usr/bin/env bash

function parseZipFile(){

    zipfile=$1
    name=$2
    vm_output_csv=$3

	unzip -p $zipfile $name/log/sysUsage*                   > $name.sysUsage.csv
	unzip -p $zipfile $name/log/sysInfo*                    > $name.sysInfo.txt
	unzip -p $zipfile $name/log/GC.txt                      > $name.GC.txt
	unzip -p $zipfile $name/log/btrace.csv                  > $name.btrace.csv
	unzip -p $zipfile $name/log/MatchingEngine.console.log  > $name.MatchingEngine.console.log

	unzip -p $zipfile $name/log/e2e_LxTxCx_FIX_RT*          > $name.e2e_LxTxCx_FIX_RT.csv.tmp
    head -1 $name.e2e_LxTxCx_FIX_RT.csv.tmp                 > $name.e2e_LxTxCx_FIX_RT.csv
    #Sort to avoid possible diagram problem. sendTime(YYY-MM-DD...) is the first column.
    sort $name.e2e_LxTxCx_FIX_RT.csv.tmp | grep -v $(head -1 $name.e2e_LxTxCx_FIX_RT.csv.tmp)  >> $name.e2e_LxTxCx_FIX_RT.csv
    rm $name.e2e_LxTxCx_FIX_RT.csv.tmp

    echo "match_ns"                    > $name.btrace.match_ns.txt
    grep "^match_ns" $name.btrace.csv | cut -d ',' -f2 >> $name.btrace.match_ns.txt

    echo "publish2bus_ns"                    > $name.btrace.publish2bus_ns.txt
    grep "^publish2bus_ns" $name.btrace.csv | cut -d ',' -f2 >> $name.btrace.publish2bus_ns.txt

    echo "match_publish2bus_ns"                    > $name.btrace.match_publish2bus_ns.txt
    grep "^match_publish2bus_ns" $name.btrace.csv | cut -d ',' -f2 >> $name.btrace.match_publish2bus_ns.txt

    echo "fix_processIncomingOrder_ns"                    > $name.btrace.fix_processIncomingOrder_ns.txt
    grep "^fix_processIncomingOrder_ns" $name.btrace.csv | cut -d ',' -f2 >> $name.btrace.fix_processIncomingOrder_ns.txt

	python_script_file=$MYSCRIPTDIR/parseLatencyData.py
	#python_script_file=/c/baoying.wang/ws/gitnas/OrderBook/src/test/python/parseLatencyData.py
	python ${python_script_file} $name.e2e_LxTxCx_FIX_RT.csv $name.sysUsage.csv $name.sysInfo.txt ${vm_output_csv} $name.btrace $name

}

#
#bash /c/baoying.wang/ws/gitnas/OrderBook/src/test/bash/post_parsing_all_zipped_output.sh \
# . vmstat_since20171231.log
#
SCRIPT_START_DIR=`pwd`
DIRNAMECMD="/usr/bin/dirname"
MYSCRIPTDIR=`(cd \`${DIRNAMECMD} ${0}\` ; echo \`pwd\`)`

zipfies_dir=${1:-${SCRIPT_START_DIR}}
vmstat_file=$2



vm_output_csv=${vmstat_file}.csv
if [[ -e ${vmstat_file} ]]; then
    bash $MYSCRIPTDIR/vm.output.to.csv.sh $vmstat_file > ${vm_output_csv}
fi

ls -l ${zipfies_dir} | grep "zip_" | awk '{print $NF }' | while read zipfile_name
do

    echo "processing ${zipfile_name}"
	name=$(echo ${zipfile_name} | cut -d '.' -f1 | cut -c5-)

	parseZipFile ${zipfies_dir}/${zipfile_name} $name $vm_output_csv

done

