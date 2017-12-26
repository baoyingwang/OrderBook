#
#bash /c/baoying.wang/ws/gitnas/OrderBook/src/test/bash/post_parsing_all_zipped_output.sh /c/Users/U0127650/Desktop/test_result_20171225/tmp/
#
SCRIPT_START_DIR=`pwd`
DIRNAMECMD="/usr/bin/dirname"
MYSCRIPTDIR=`(cd \`${DIRNAMECMD} ${0}\` ; echo \`pwd\`)`

zipfies_dir=${1:-${SCRIPT_START_DIR}}
python_script_dir=${2:-"/c/baoying.wang/ws/gitnas/OrderBook/src/test/python/parseLatencyData.py"}

ls -l ${zipfies_dir} | grep zip | awk '{print $NF }' | while read zipfile
do

	echo "$(date '+%Y%m%d_%H%M%S') processing $zipfile"
	name=$(echo $zipfile | cut -d '.' -f1 | cut -c5-)
	
	
	unzip -p ${zipfies_dir}/$zipfile $name/log/LatencyData_test.start*  > $name.latency.data.csv
	unzip -p ${zipfies_dir}/$zipfile $name/log/sysUsage*  > $name.sysUsage.csv
	python /c/baoying.wang/ws/gitnas/OrderBook/src/test/python/parseLatencyData.py $name.latency.data.csv $name.sysUsage.csv $name
done

