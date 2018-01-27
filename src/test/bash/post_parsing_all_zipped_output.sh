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
	unzip -p $zipfile $name/log/engine_statistics.json.txt  > $name.engine_statistics.json.txt
	unzip -p $zipfile $name/log/e2e_LxTxCx_FIX_RT*          > $name.e2e_LxTxCx_FIX_RT.csv.tmp
    unzip -p $zipfile $name.console.log                     > $name.testscript.console.log

	echo "-- unzip done"


    head -1 $name.e2e_LxTxCx_FIX_RT.csv.tmp                 > $name.e2e_LxTxCx_FIX_RT.csv

	local latency_records_num=$(wc -l $name.e2e_LxTxCx_FIX_RT.csv.tmp | cut -d ' ' -f1)
	local max_sort_can_handle=50000 #just a guess number
    #on git bash, sort silently quit on big file, e.g. 80MB
    #I expect Python will handle the sequence, since i have convert them to datetime. But looks like python does not handle that well.
    if (( $latency_records_num < $max_sort_can_handle )); then
        sort $name.e2e_LxTxCx_FIX_RT.csv.tmp | grep -v $(head -1 $name.e2e_LxTxCx_FIX_RT.csv.tmp)   > $name.e2e_LxTxCx_FIX_RT.csv.no_header
    else
        grep -v $(head -1 $name.e2e_LxTxCx_FIX_RT.csv.tmp) $name.e2e_LxTxCx_FIX_RT.csv.tmp > $name.e2e_LxTxCx_FIX_RT.csv.no_header
    fi
    rm $name.e2e_LxTxCx_FIX_RT.csv.tmp

    local max_python_handle_lines_num=$(( 100 * 1000 )) #just a guess number
    local awk_mod_for_latency_records=1
    if (( $latency_records_num > $max_python_handle_lines_num )); then
        awk_mod_for_latency_records=$(( ($latency_records_num * 2)/max_python_handle_lines_num ))
    else
        awk_mod_for_latency_records=1
    fi

    awk " NR % $awk_mod_for_latency_records == 0" $name.e2e_LxTxCx_FIX_RT.csv.no_header >> $name.e2e_LxTxCx_FIX_RT.csv
    rm $name.e2e_LxTxCx_FIX_RT.csv.no_header



    echo "match_ns"                    > $name.btrace.match_ns.txt
    grep "^match_ns" $name.btrace.csv | cut -d ',' -f2 >> $name.btrace.match_ns.txt

    echo "publish2bus_ns"                    > $name.btrace.publish2bus_ns.txt
    grep "^publish2bus_ns" $name.btrace.csv | cut -d ',' -f2 >> $name.btrace.publish2bus_ns.txt

    echo "match_publish2bus_ns"                    > $name.btrace.match_publish2bus_ns.txt
    grep "^match_publish2bus_ns" $name.btrace.csv | cut -d ',' -f2 >> $name.btrace.match_publish2bus_ns.txt

	python_script_file=$MYSCRIPTDIR/parseLatencyData.py
	#python_script_file=/c/baoying.wang/ws/gitnas/OrderBook/src/test/python/parseLatencyData.py
	python ${python_script_file} $name.e2e_LxTxCx_FIX_RT.csv $name.sysUsage.csv $name.sysInfo.txt ${vm_output_csv} $name.btrace $name.engine_statistics.json.txt $name

    echo "-- python process done"
}

function generate_all_cases_summary(){

    summary_result=all_cases_e2e_describe_all.csv
    summary_result_md_file=all_cases_e2e_describe.md.txt

    #populate the first column (min/max/percentiles)
	first_file=$(ls -l | grep "e2e_describe.csv$" |  awk ' { printf("%s\n", $NF)}' | head -1)
	if [[ ! -e $first_file ]]; then
	    echo "no e2e describe csv file"
	    return
	fi
    cut -d',' -f1 $first_file > $summary_result


	ls -lrt | grep "e2e_describe.csv$" |  awk ' { printf("%s\n", $NF)}' | while read e2e_desc_file
	do
		echo processing $e2e_desc_file
		local tmp_1st_col_file=tmp.$e2e_desc_file.1st.col.txt
		echo $e2e_desc_file |cut -d'_' -f1-3       > $tmp_1st_col_file
		cut -d, -f2 $e2e_desc_file | sed -n '1!p' >> $tmp_1st_col_file

		local local_tmp_result=tmp_local.txt.tmp
		paste -d , $summary_result tmp.$e2e_desc_file.1st.col.txt > $local_tmp_result
		mv $local_tmp_result $summary_result

		rm $tmp_1st_col_file
	done

	csv_to_markdown_table $summary_result $summary_result_md_file
}

function csv_to_markdown_table(){

	#summary_result_md_file=all_cases_e2e_describe.md.txt
	in_summary_result=$1
	out_summary_result_md_file=$2

    #header
	head -1 $in_summary_result | tr ',' '|' |  awk '{print "|"$1"|"}' > $out_summary_result_md_file
	column_num=$(head -1 $in_summary_result | awk -F, '{ printf("%s\n", NF)}')

    #splitter
	tmp_md_splitter=$(printf -v spaces '%*s' $column_num ''; printf '%s\n' ${spaces// /'|--'})
	md_splitter=$(echo $tmp_md_splitter'|')
	echo $md_splitter >> $out_summary_result_md_file

    #content
    #sed -n '1!p' //skip header of the csv
    #tr ',' '|'   //replace the comma with |
	sed -n '1!p' $in_summary_result | tr ',' '|' |  awk '{print "|"$1"|"}' >> $out_summary_result_md_file


}


#
#bash /c/baoying.wang/ws/gitnas/OrderBook/src/test/bash/post_parsing_all_zipped_output.sh \
# . vmstat_since20171231.log
#
SCRIPT_START_DIR=`pwd`
DIRNAMECMD="/usr/bin/dirname"
MYSCRIPTDIR=`(cd \`${DIRNAMECMD} ${0}\` ; echo \`pwd\`)`

zipfies_dir=${1:-${SCRIPT_START_DIR}}
vmstat_file=${2:-vmoutput.txt}

vm_output_csv=${vmstat_file}.csv
if [[ -e ${vmstat_file} ]]; then
    bash $MYSCRIPTDIR/vm.output.to.csv.sh $vmstat_file > ${vm_output_csv}
fi

ls -lrt ${zipfies_dir} | grep "zip_" | awk '{print $NF }' | while read zipfile_name
do

    echo "processing ${zipfile_name}"
    name=$(echo ${zipfile_name::(-4)} |  cut -c5-)

    parseZipFile ${zipfies_dir}/${zipfile_name} "$name" "$vm_output_csv"
done

generate_all_cases_summary
