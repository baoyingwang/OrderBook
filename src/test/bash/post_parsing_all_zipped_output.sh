#!/usr/bin/env bash

function parseZipFile(){

    zipfile=$1
    name=$2
    vm_output_csv=$3

	unzip -p $zipfile $name/log/LatencyData_test.start*     > $name.latency.data.csv
	unzip -p $zipfile $name/log/LatencySummary_test.start*  > $name.latency.summary.json.txt
	unzip -p $zipfile $name/log/sysUsage*                   > $name.sysUsage.csv
	unzip -p $zipfile $name/log/sysInfo*                    > $name.sysInfo.txt
	unzip -p $zipfile $name/log/GC.txt                      > $name.GC.txt
	unzip -p $zipfile $name/log/GC.summary.csv              > $name.GC.summary.csv
	unzip -p $zipfile $name/log/MatchingEngine.console.log  > $name.MatchingEngine.console.log

	unzip -p $zipfile $name/log/e2e_LxTxCx_FIX_RT*          > $name.e2e_LxTxCx_FIX_RT.csv.tmp
    head -1 $name.e2e_LxTxCx_FIX_RT.csv.tmp                 > $name.e2e_LxTxCx_FIX_RT.csv
    grep -v $(head -1 $name.e2e_LxTxCx_FIX_RT.csv.tmp) $name.e2e_LxTxCx_FIX_RT.csv.tmp >> $name.e2e_LxTxCx_FIX_RT.csv
    rm $name.e2e_LxTxCx_FIX_RT.csv.tmp

	#python_script_file=parseLatencyData.py
	python_script_file=/c/baoying.wang/ws/gitnas/OrderBook/src/test/python/parseLatencyData.py
	python ${python_script_file} $name.latency.data.csv $name.sysUsage.csv $name.sysInfo.txt ${vm_output_csv} $name

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

ls -l ${zipfies_dir} | grep zip | awk '{print $NF }' | while read zipfile_name
do

    echo "processing ${zipfile_name}"
	name=$(echo ${zipfile_name} | cut -d '.' -f1 | cut -c5-)

	parseZipFile ${zipfies_dir}/${zipfile_name} $name $vm_output_csv

done

