#!/usr/bin/env bash
#
#bash /c/baoying.wang/ws/gitnas/OrderBook/src/test/bash/post_parsing_all_zipped_output.sh \
# /c/Users/U0127650/Desktop/test_result_20171229/vmstat_since20171229.log \
# /c/Users/U0127650/Desktop/test_result_20171229
#
SCRIPT_START_DIR=`pwd`
DIRNAMECMD="/usr/bin/dirname"
MYSCRIPTDIR=`(cd \`${DIRNAMECMD} ${0}\` ; echo \`pwd\`)`

vmstat_file=$1
zipfies_dir=${2:-${SCRIPT_START_DIR}}


vm_output_csv=${vmstat_file}.csv
bash $MYSCRIPTDIR/vm.output.to.csv.sh $vmstat_file > ${vm_output_csv}

ls -l ${zipfies_dir} | grep zip | awk '{print $NF }' | while read zipfile
do

    echo "processing $zipfile"
	name=$(echo $zipfile | cut -d '.' -f1 | cut -c5-)
	
	unzip -p ${zipfies_dir}/$zipfile $name/log/LatencyData_test.start*     > $name.latency.data.csv
	unzip -p ${zipfies_dir}/$zipfile $name/log/LatencySummary_test.start*  > $name.latency.summary.json.txt
	unzip -p ${zipfies_dir}/$zipfile $name/log/sysUsage*                   > $name.sysUsage.csv
	unzip -p ${zipfies_dir}/$zipfile $name/log/sysInfo*                    > $name.sysInfo.txt
	unzip -p ${zipfies_dir}/$zipfile $name/log/GC.txt                      > $name.GC.txt
	unzip -p ${zipfies_dir}/$zipfile $name/log/GC.summary.csv              > $name.GC.summary.csv
	python /c/baoying.wang/ws/gitnas/OrderBook/src/test/python/parseLatencyData.py $name.latency.data.csv $name.sysUsage.csv ${vm_output_csv} $name.sysInfo.txt $name

done

