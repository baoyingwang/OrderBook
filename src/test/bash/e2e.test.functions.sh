#!/usr/bin/env bash


function sendOrders(){

    local tmp_cp=${1:-..}
    local tmp_main_class=${2:-"baoying.orderbook.testtool.vertx.VertxClientRoundBatch"}
    local tmp_testclient_arguments=$3
    local tmp_jvm_options=${4:-"-Xmx256M -Dlog4j.configurationFile=log4j2_testtool.xml"}

    echo  "$(date '+%Y%m%d_%H%M%S') begin sending orders - ${tmp_testclient_arguments}"
    java ${tmp_jvm_options} -cp ${tmp_cp} ${tmp_main_class} ${tmp_testclient_arguments}
}


function populateOB(){

    local tmp_cp=${1:-..}

    local tmp_side=${2:-Bid}
    local tmp_symbol=${3:-USDJPY}
    local tmp_prices=${4:-110}
    local tmp_qty=${5:-100000000}


    local tmp_ordType=Limit
    local tmp_clientNum=1
    local tmp_ratePerMinute=60
    local tmp_duration=5
    local tmp_client_prefix=BACKGROUND_FIX_OB_${tmp_side}_${RANDOM}

    local tmp_main_class=baoying.orderbook.testtool.vertx.VertxClientRoundBatch

    local tmp_testclient_arguments="-clientNum ${tmp_clientNum} -ratePerMinute ${tmp_ratePerMinute} -client_prefix ${tmp_client_prefix} -symbol ${tmp_symbol} -side ${tmp_side} -qty ${tmp_qty} -ordType ${tmp_ordType} -prices ${tmp_prices} -d ${tmp_duration}"

    local tmp_main_class="baoying.orderbook.testtool.vertx.VertxClientRoundBatch"
    sendOrders "$tmp_cp" $tmp_main_class "$tmp_testclient_arguments"

}


function populateOB10Levels(){

    local tmp_cp=${1:-..}

    local tmp_side=${2:-Bid}
    local tmp_symbol=${3:-USDJPY}
    local px_int_part=${4:-110}
    local tmp_qty=${5:-100000000}


    local tmp_prices="${px_int_part}.1,${px_int_part}.2,${px_int_part}.3,${px_int_part}.4,${px_int_part}.5,${px_int_part}.6,${px_int_part}.7,${px_int_part}.8,${px_int_part}.9"

    populateOB $tmp_cp $tmp_side $tmp_symbol $tmp_prices $tmp_qty

}

#TODO for some reason, this one doesn't work on gitbash.
function kill_all_java(){

    case $OSTYPE in
        linux*)
            #clean exist process
            #trim the line is required(by sed), otherwise the -f1 maybe empty for align issue(e.g. pid 23 and 12345)
            ps -eo pid,user,comm,pcpu | grep java | grep -v grep | sed 's/^ *//;s/ *$//'| cut -d' ' -f1 | xargs kill -9
            ;;
        msys*)
            cmd "/C TASKKILL /F /IM java.exe /T"
            ;;
    esac

}

function start_vmstat(){
    case $OSTYPE in
        linux*)
            vmstat_count=$(ps -ef |grep "vmstat 5 -t" | grep -v grep | wc -l)
            if [[ $vmstat_count -lt 1 ]]; then
                    echo "start vmstat, since not yet exist "
                    vmstat 5 -t >> vmstat_since$(date '+%Y%m%d').log &
            fi
            ;;
        msys*)
            echo "***remember start the performance monitor manually***, since no vmstat on windows"
            ;;
    esac
}