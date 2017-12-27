#!/bin/bash

#bash /c/baoying.wang/ws/gitnas/OrderBook/src/test/bash/vm.output.to.csv.sh  ~/Desktop/test_result_20171226_v2/tmp/vmstat_begin.from20171226_060017.log > ~/Desktop/test_result_20171226_v2/tmp/vmstat_begin.from20171226_060017.csv
#output of vmstat 5 -t >> vmstat_begin.from$(date '+%Y%m%d').log &
#e.g.
#procs -----------memory---------- ---swap-- -----io---- -system-- ------cpu----- -----timestamp-----
# r  b   swpd   free   buff  cache   si   so    bi    bo   in   cs us sy id wa st                 GMT
# 0  0      0 5614196  34184 256724    0    0  1894    35  270  735  3  4 91  2  0 2017-12-26 06:00:17
# 0  0      0 5614072  34184 256752    0    0     0     0   60   70  0  0 100  0  0 2017-12-26 06:00:22

vmfile=$1
if [[ ! -e $vmfile ]]; then
	echo "ERROR - not found vmfile: $vmfile"
	exit 1
fi

echo "procs_r,procs_b,memory_swpd,memory_free,memory_buff,memory_cache,swap_si,swap_so,io_bi,io_bo,system_in,system_cs,cpu_us,cpu_sy,cpu_id,cpu_wa,cpu_st,timestamp_day,timestamp_time"
#trim from pipe: awk '{$1=$1;print}' https://unix.stackexchange.com/questions/102008/how-do-i-trim-leading-and-trailing-whitespace-from-each-line-of-some-output
grep -v memory $vmfile | grep -v free |awk '{$1=$1;print}' | sed 's/ \+/,/g'


#   Procs
#       r: The number of runnable processes (running or waiting for run time).
#       b: The number of processes in uninterruptible sleep.
#
#   Memory
#       swpd: the amount of virtual memory used.
#       free: the amount of idle memory.
#       buff: the amount of memory used as buffers.
#       cache: the amount of memory used as cache.
#       inact: the amount of inactive memory.  (-a option)
#       active: the amount of active memory.  (-a option)
#
#   Swap
#       si: Amount of memory swapped in from disk (/s).
#       so: Amount of memory swapped to disk (/s).
#
#   IO
#       bi: Blocks received from a block device (blocks/s).
#       bo: Blocks sent to a block device (blocks/s).
#   System
#       in: The number of interrupts per second, including the clock.
#       cs: The number of context switches per second.
#
#   CPU
#       These are percentages of total CPU time.
#       us: Time spent running non-kernel code.  (user time, including nice time)
#       sy: Time spent running kernel code.  (system time)
#       id: Time spent idle.  Prior to Linux 2.5.41, this includes IO-wait time.
#       wa: Time spent waiting for IO.  Prior to Linux 2.5.41, included in idle.
#       st: Time stolen from a virtual machine.  Prior to Linux 2.6.11, unknown.
