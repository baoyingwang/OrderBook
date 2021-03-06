import sys
from by_common_import import *
import os
import re #regex
import json
from datetime import datetime



#===input===
#clientOrdID
#sendTime
#sendTimeNano
#recvFromClient_sysNano
#matched_sysNano
#matchER
#
#===output===
#
def getE2E(inputE2EFile):

    print("load e2e csv file" + inputE2EFile +" to dataframe")

    df=pd.read_csv(inputE2EFile)

    df.describe()

    df["e2e_match_us" ] = (df["clientRecvER"  ] - df["clientSendNano" ]            )/1000

    df["input_us"  ] = (df["svrRecvOrdNano"  ] - df["clientSendNano" ]   )/1000 #client order -->mar-->bytes -->network-->unmar-->svr order
    df["process_us"] = (df["svrMatchedNano"  ] - df["svrRecvOrdNano"]    )/1000
    df["output_us" ] = (df["clientRecvER"    ] - df["svrMatchedNano"]    )/1000 #server ER -->mar-->bytes -->network-->unmar--> client ER

    df['sendTime_datetime' ] = df['sendTime'].map(lambda x: datetime.strptime(x,"%Y-%m-%dT%H:%M:%S.%fZ"))

    result_df = df[['sendTime_datetime',
                    'e2e_match_us',
                    'input_us', 'process_us', 'output_us']]

    return result_df

def genPlotE2E( plt, shape, row_start_index, df_e2e, output_file_prefix):

    #https://stackoverflow.com/questions/31247198/python-pandas-write-content-of-dataframe-into-text-file
    describeResult = df_e2e.describe(percentiles=[.25,.5,.75,.9, .95, .99 ])
    describeResult.to_csv(output_file_prefix+"_e2e_describe.csv")

    plt.subplot2grid(shape,(row_start_index,0), colspan=2)
    plt.text(0, 0 ,describeResult.to_string())
    #plt.title(" e2e summary" ) #remove title because of overlap on diagram


    genSimplePlot(plt, shape,df_e2e ,(row_start_index  ,2),'sendTime_datetime',"e2e_match_us"  ,"e2e_match_us" , "us")

    genSimplePlot(plt, shape,df_e2e ,(row_start_index+1,0),'sendTime_datetime',"input_us"  ,"#1 clnt ord obj->net bytes->svr ord obj" , "us")
    genSimplePlot(plt, shape,df_e2e ,(row_start_index+1,1),'sendTime_datetime',"process_us","#2 svr proc obj" , "us")
    genSimplePlot(plt, shape,df_e2e ,(row_start_index+1,2),'sendTime_datetime',"output_us" ,"#3 svr ER obj->net bytes->clnt ER obj" , "us")


    return (row_start_index + 1) + 1

def genSimplePlot( plt, shape, df, loc, x_col, y_col, title, y_label):
    plt.subplot2grid(shape,loc)
    plt.plot(df[x_col], df[y_col]   , label=y_col   , marker='h' )
    #https://plot.ly/matplotlib/axes/
    plt.title(title)
    plt.ylabel(y_label)
    plt.xticks(rotation=90)#fig.autofmt_xdate() does not work for me. why? https://stackoverflow.com/questions/10998621/rotate-axis-text-in-python-matplotlib



def genPlotBTrace( plt, shape, row_start_index, df_match_us):

    plt.subplot2grid(shape,(row_start_index,0))
    plt.text(0, 0 ,df_match_us["match_us"].describe(percentiles=[.25,.75,.95, .99 ]).to_string())
    plt.ylabel("match_us")

    genSimplePlot(plt, shape,df_match_us            ,(row_start_index,1),'manual_index',"match_us"           ,"match_us"             , "us")
    #genSimplePlot(plt, shape,df_publish2bus_us      ,(row_start_index,1),'manual_index',"publish2bus_us"      ,"publish2bus_us"      , "us")
    #genSimplePlot(plt, shape,df_match_publish2bus_us,(row_start_index,2),'manual_index',"match_publish2bus_us","match_publish2bus_us", "us")


    #min_size=min([df_match_us["match_us"].size,
    #              df_publish2bus_us["publish2bus_us"].size,
    #              df_match_publish2bus_us["match_publish2bus_us"].size])
    ##https://stackoverflow.com/questions/23668427/pandas-joining-multiple-dataframes-on-columns
    #agg_df_tmp=df_match_us[:min_size].merge(df_publish2bus_us[:min_size],on='manual_index').merge(df_match_publish2bus_us[0:min_size],on='manual_index')
    #agg_df    =agg_df_tmp[['match_us','publish2bus_us','match_publish2bus_us']]

    return row_start_index + 1


#time_datetime
#cpu_ProcessCpuLoad
#cpu_SystemLoadAverage
#gc_ConcurrentMarkSweep_CollectionCount
#gc_ConcurrentMarkSweep_CollectionTime
#gc_ParNew_CollectionCount
#gc_ParNew_CollectionTime
#heapMemory_Committed
#heapMemory_Init
#heapMemory_Max
#heapMemory_Used
#non-heapMemory_Committed
#non-heapMemory_Init
#non-heapMemory_Max
#non-heapMemory_Used
#thread_ThreadCount
def getSysUsage(sysUsageFile):

    print("load sys usage csv file " + sysUsageFile +" to dataframe")

    #time,cpu ProcessCpuLoad,cpu SystemLoadAverage,gc ConcurrentMarkSweep CollectionCount,gc ConcurrentMarkSweep CollectionTime,gc ParNew CollectionCount,gc ParNew CollectionTime,heapMemory Committed,heapMemory Init,heapMemory Max,heapMemory Used,non-heapMemory Committed,non-heapMemory Init,non-heapMemory Max,non-heapMemory Used,thread ThreadCount
    #2017-12-26T06:23:57.323Z,60.00,1.35,0,14,1,26,1056309248,1073741824,1056309248,64060352,42807296,2555904,-1,41957960,9
    #2017-12-26T06:24:02.320Z,65.70,1.33,1,68,1,26,1056309248,1073741824,1056309248,135747440,49889280,2555904,-1,48575024,30
    df=pd.read_csv(sysUsageFile)

    #replace the space with understand in column name - https://github.com/pandas-dev/pandas/issues/6508
    cols = df.columns
    cols = cols.map(lambda x: x.replace(' ', '_') )
    df.columns = cols

    df['time_datetime'] = df['time'].map(lambda x: datetime.strptime(x,"%Y-%m-%dT%H:%M:%S.%fZ"))
    return df


def getVmstat(inputVmstatFile):

    print("load sys vmstat csv file " + inputVmstatFile +" to dataframe")

    df=pd.read_csv(inputVmstatFile)

    #procs_r,procs_b,memory_swpd,memory_free,memory_buff,memory_cache,swap_si,swap_so,io_bi,io_bo,system_in,system_cs,system_us,system_sy,system_id,system_wa,system_st,timestamp_day,timestamp_time
    #0,0,0,5605964,35796,257216,0,0,1492,85,225,853,3,4,92,1,0,2017-12-26,05:50:22
    df['time']=df['timestamp_day']+'T'+df['timestamp_time']+'Z'
    df['time_datetime'] = df['time'].map(lambda x: datetime.strptime(x,"%Y-%m-%dT%H:%M:%SZ"))
    return df

def getBTrace(btraceFilePrefix):

    df_match                   =pd.read_csv(btraceFilePrefix+".match_ns.txt")
    #df_publish2bus             =pd.read_csv(btraceFilePrefix+".publish2bus_ns.txt")
    #df_match_publish2bus       =pd.read_csv(btraceFilePrefix+".match_publish2bus_ns.txt")

    df_match['match_us']     =       df_match["match_ns"]/1000
    df_match['manual_index'] = range(df_match.index.size) #Not df.size, because it is m*n.

    #df_publish2bus['publish2bus_us']     =       df_publish2bus["publish2bus_ns"]/1000
    #df_publish2bus['manual_index']       = range(df_publish2bus.index.size) #Not df.size, because it is m*n.

    #df_match_publish2bus['match_publish2bus_us']  =       df_match_publish2bus["match_publish2bus_ns"]/1000
    #df_match_publish2bus['manual_index']          = range(df_match_publish2bus.index.size) #Not df.size, because it is m*n.

    #return df_match[['manual_index','match_us']], df_publish2bus[['manual_index','publish2bus_us']],df_match_publish2bus[['manual_index','match_publish2bus_us']]
    return df_match[['manual_index','match_us']]


def genPlotVMStat(plt, shape, vmstat_row_start_index, df_vmstat):

    if df_vmstat.size<1:
        return vmstat_row_start_index

    #procs_r,procs_b,memory_swpd,memory_free,memory_buff,memory_cache,swap_si,swap_so,io_bi,io_bo,system_in,system_cs,cpu_us,cpu_sy,cpu_id,cpu_wa,cpu_st,timestamp_day,timestamp_time
    #0,0,0,5605964,35796,257216,0,0,1492,85,225,853,3,4,92,1,0,2017-12-26,05:50:22
    plt.subplot2grid(shape,(vmstat_row_start_index,0))
    plt.plot(df_vmstat["time_datetime"], df_vmstat["procs_r"] , label="procs_r",  marker='h')
    plt.plot(df_vmstat["time_datetime"], df_vmstat["procs_b"] , label="procs_b",  marker='h')
    plt.title(u"vmstat procs")
    plt.legend((u'procs_r', u'procs_b'),loc='best')
    plt.xticks(rotation=90)#fig.autofmt_xdate() does not work for me. why? https://stackoverflow.com/questions/10998621/rotate-axis-text-in-python-matplotlib

    plt.subplot2grid(shape,(vmstat_row_start_index,1))
    plt.plot(df_vmstat["time_datetime"], df_vmstat["memory_swpd"] , label="memory_swpd",  marker='h')
    plt.plot(df_vmstat["time_datetime"], df_vmstat["memory_free"] , label="memory_free",  marker='h')
    plt.plot(df_vmstat["time_datetime"], df_vmstat["memory_buff"] , label="memory_buff",  marker='h')
    plt.plot(df_vmstat["time_datetime"], df_vmstat["memory_cache"] , label="memory_cache",  marker='h')
    plt.title(u"vmstat memory")
    plt.legend((u'memory_swpd',u'memory_free', u'memory_buff',  u'memory_cache'),loc='best')
    plt.xticks(rotation=90)#fig.autofmt_xdate() does not work for me. why? https://stackoverflow.com/questions/10998621/rotate-axis-text-in-python-matplotlib

    plt.subplot2grid(shape,(vmstat_row_start_index,2))
    plt.plot(df_vmstat["time_datetime"], df_vmstat["swap_si"] , label="swap_si",  marker='h')
    plt.plot(df_vmstat["time_datetime"], df_vmstat["swap_so"] , label="swap_so",  marker='h')
    plt.title(u"vmstat swap")
    plt.legend((u'swap_si', u'swap_so'),loc='best')
    plt.xticks(rotation=90)#fig.autofmt_xdate() does not work for me. why? https://stackoverflow.com/questions/10998621/rotate-axis-text-in-python-matplotlib

    plt.subplot2grid(shape,(vmstat_row_start_index+1,0))
    plt.plot(df_vmstat["time_datetime"], df_vmstat["io_bi"] , label="io_bi",  marker='h')
    plt.plot(df_vmstat["time_datetime"], df_vmstat["io_bo"] , label="io_bo",  marker='h')
    plt.title(u"vmstat io")
    plt.legend((u'io_bi', u'io_bo'),loc='best')
    plt.xticks(rotation=90)#fig.autofmt_xdate() does not work for me. why? https://stackoverflow.com/questions/10998621/rotate-axis-text-in-python-matplotlib

    plt.subplot2grid(shape,(vmstat_row_start_index+1,1))
    plt.plot(df_vmstat["time_datetime"], df_vmstat["system_in"] , label="system_in",  marker='h')
    plt.plot(df_vmstat["time_datetime"], df_vmstat["system_cs"] , label="system_cs",  marker='h')
    plt.title(u"vmstat System")
    plt.legend((u'system_in', u'system_cs'),loc='best')
    plt.xticks(rotation=90)#fig.autofmt_xdate() does not work for me. why? https://stackoverflow.com/questions/10998621/rotate-axis-text-in-python-matplotlib

    #cpu_us,cpu_sy,cpu_id,cpu_wa,cpu_st
    plt.subplot2grid(shape,(vmstat_row_start_index+1,2))
    plt.plot(df_vmstat["time_datetime"], df_vmstat["cpu_us"] , label="cpu_us",  marker='h')
    plt.plot(df_vmstat["time_datetime"], df_vmstat["cpu_sy"] , label="cpu_sy",  marker='h')
    plt.plot(df_vmstat["time_datetime"], df_vmstat["cpu_id"] , label="cpu_id",  marker='h')
    plt.plot(df_vmstat["time_datetime"], df_vmstat["cpu_wa"] , label="cpu_wa",  marker='h')
    plt.plot(df_vmstat["time_datetime"], df_vmstat["cpu_st"] , label="cpu_st",  marker='h')
    plt.title(u"vmstat cpu")
    plt.legend((u'cpu_us',u'cpu_sy', u'cpu_id',  u'cpu_wa', u'cpu_st'),loc='best')
    plt.xticks(rotation=90)#fig.autofmt_xdate() does not work for me. why? https://stackoverflow.com/questions/10998621/rotate-axis-text-in-python-matplotlib

    next_row_index_for_plot=vmstat_row_start_index + 1 +1
    return next_row_index_for_plot

def genPlotSysUsage(plt, shape, row_start_index, df_sysUsage):


    genSimplePlot(plt, shape,df_sysUsage ,(row_start_index  ,0),'time_datetime',"cpu_ProcessCpuLoad"     ,"cpu_ProcessCpuLoad"    ,"%")
    genSimplePlot(plt, shape,df_sysUsage ,(row_start_index  ,1),'time_datetime',"cpu_SystemLoadAverage"  ,"cpu_SystemLoadAverage" , "")
    genSimplePlot(plt, shape,df_sysUsage ,(row_start_index  ,2),'time_datetime',"thread_ThreadCount"     ,"thread_ThreadCount" , "")


    plt.subplot2grid(shape, (row_start_index + 1,0))
    plt.plot(df_sysUsage["time_datetime"], df_sysUsage["heapMemory_Used"]/(1024*1024)        , label="heapMemory_Used" ,  marker='h' )
    plt.plot(df_sysUsage["time_datetime"], df_sysUsage["heapMemory_Committed"]/(1024*1024)   , label="heapMemory_Committed" ,  marker='h' )
    plt.title(u"heapMemory (MB)")
    plt.legend((u'Used', u'Committed'),loc='best')
    plt.xticks(rotation=90)#fig.autofmt_xdate() does not work for me. why? https://stackoverflow.com/questions/10998621/rotate-axis-text-in-python-matplotlib

    plt.subplot2grid(shape,(row_start_index + 2,0))
    plt.plot(df_sysUsage["time_datetime"], df_sysUsage["non-heapMemory_Used"]/(1024*1024)       , label="non-heapMemory_Used" ,  marker='h' )
    plt.plot(df_sysUsage["time_datetime"], df_sysUsage["non-heapMemory_Committed"]/(1024*1024)  , label="non-heapMemory_Committed" ,  marker='h' )
    plt.title(u"non-heapMemory (MB)")
    plt.legend((u'Used', u'Committed'),loc='best')
    plt.xticks(rotation=90)#fig.autofmt_xdate() does not work for me. why? https://stackoverflow.com/questions/10998621/rotate-axis-text-in-python-matplotlib

    plt.subplot2grid(shape,(row_start_index + 1,1))
    plt.plot(df_sysUsage["time_datetime"], df_sysUsage["gc_ConcurrentMarkSweep_CollectionCount"] , label="gc_ConcurrentMarkSweep_CollectionCount",  marker='h')
    plt.plot(df_sysUsage["time_datetime"], df_sysUsage["gc_ParNew_CollectionCount"]              , label="gc_ParNew_CollectionCount"             ,  marker='h')
    plt.title(u"GC Collection Count")
    plt.legend((u'ConcurrentMarkSweep', u'ParNew'),loc='best')
    plt.xticks(rotation=90)#fig.autofmt_xdate() does not work for me. why? https://stackoverflow.com/questions/10998621/rotate-axis-text-in-python-matplotlib

    plt.subplot2grid(shape,(row_start_index + 2,1))
    plt.plot(df_sysUsage["time_datetime"], df_sysUsage["gc_ConcurrentMarkSweep_CollectionTime"] , label="gc_ConcurrentMarkSweep_CollectionTime",  marker='h')
    plt.plot(df_sysUsage["time_datetime"], df_sysUsage["gc_ParNew_CollectionTime"]              , label="gc_ParNew_CollectionTime"             ,  marker='h')
    plt.title(u"GC Collection Accumulated Time-MS?")
    plt.legend((u'ConcurrentMarkSweep', u'ParNew'),loc='best')
    plt.xticks(rotation=90)#fig.autofmt_xdate() does not work for me. why? https://stackoverflow.com/questions/10998621/rotate-axis-text-in-python-matplotlib

    next_row_index_for_plot=row_start_index + 2 +1
    return next_row_index_for_plot



def genPlotSysInfo(plt, shape, row_start_index, sysInfoContentList, meStatsJsonOverall):

    #https://stackoverflow.com/questions/3013449/list-filtering-list-comprehension-vs-lambda-filter
    #https://stackoverflow.com/questions/16522111/python-syntax-for-if-a-or-b-or-c-but-not-all-of-them
    osLineList      = [x for x in sysInfoContentList if x.startswith("os ")]
    gcLineList      = [x for x in sysInfoContentList if x.startswith("gc ")]
    runtimeSpecLineList    = [x for x in sysInfoContentList if x.startswith("runtime Spec")]
    runtimeVMLineList      = [x for x in sysInfoContentList if x.startswith("runtime Vm")]

    stats = "ord_rate_per_second:"+meStatsJsonOverall["ord_rate_per_second"]+"\n" \
          + "duration_in_second:" +meStatsJsonOverall["duration_in_second" ]+"\n" \
          + "order_count:"        +meStatsJsonOverall["order_count"]+"\n"         \
          + "start_time:"         +meStatsJsonOverall["start_time"]+"\n"          \
          + "end_time:"           +meStatsJsonOverall["end_time"]+"\n\n"

    overall_text = stats \
                   + "".join(osLineList) \
                   + "".join(gcLineList) \
                   + "".join(runtimeSpecLineList) \
                   + "".join(runtimeVMLineList)
    plt.subplot2grid(shape,(row_start_index,0), rowspan=2)
    plt.text(0, 0 , overall_text[ :-1])#string[:-1] remove the last carriage return character
    plt.title(u"Overall Information")

    runtime_prefix_list = [
        "runtime Name"           ,
        "runtime ClassPath"      ,
        "runtime InputArguments" ,
        "runtime BootClassPath"]
    #https://stackoverflow.com/questions/30919275/inserting-period-after-every-3-chars-in-a-string
    runtimeLineList = ['\n          '.join(x[i:i+80] for i in range(0, len(x), 80)) for x in sysInfoContentList if x.startswith("runtime ") and x.split(":")[0] in runtime_prefix_list]

    #https://stackoverflow.com/questions/30919275/inserting-period-after-every-3-chars-in-a-string
    #the ? is for non-greedy - https://docs.python.org/3/library/re.html
    java_command = [re.findall('sun.java.command=.+?,', x)[0] for x in sysInfoContentList if x.startswith("runtime SystemProperties")]
    java_command_string = '\n          '.join(java_command[0][i:i+80] for i in range(0, len(java_command[0]), 80))

    text = java_command_string + '\n' + ( "".join(runtimeLineList))
    plt.subplot2grid(shape,(row_start_index,1), colspan=2, rowspan=2)
    plt.text(0, 0 ,  text[:-1] ) #text[:-1] remove the last carriage return character
    plt.title(u"Runtime Arguments")

    return row_start_index+2


def genPlot(plotTitle,df_e2e,df_sysUsage, df_vmstat, sysInfoContentList, df_match_us, meStatsJson, output_file_prefix):

    #https://matplotlib.org/api/pyplot_api.html
    #http://blog.csdn.net/han_xiaoyang/article/details/49797143
    #https://matplotlib.org/api/_as_gen/matplotlib.pyplot.subplot.html#matplotlib.pyplot.subplot
    plt.figure(1)
    fig=plt.gcf()
    #fig.suptitle(plotTitle) overlap with heading row, after applying tight layout #https://stackoverflow.com/questions/39370584/how-do-you-add-an-overall-title-to-a-figure-with-subplots-in-matplotlib
    fig.set_tight_layout(True) #https://matplotlib.org/tutorials/intermediate/tight_layout_guide.html#sphx-glr-tutorials-intermediate-tight-layout-guide-py , https://matplotlib.org/users/tight_layout_guide.html
    fig.set_size_inches(16, 24)


    #http://blog.csdn.net/han_xiaoyang/article/details/49797143
    #https://matplotlib.org/users/gridspec.html
    #================================df_latency==============================
    shape=(10,4)
    next_row_index = 0
    next_row_index = genPlotSysInfo(    plt, shape, next_row_index, sysInfoContentList, meStatsJson["overall"])
    next_row_index = genPlotE2E(        plt, shape, next_row_index, df_e2e, output_file_prefix)
    next_row_index = genPlotBTrace(     plt, shape, next_row_index, df_match_us)
    next_row_index = genPlotSysUsage(   plt, shape, next_row_index, df_sysUsage)
    next_row_index = genPlotVMStat(     plt, shape, next_row_index, df_vmstat  )

    fig=plt.gcf()
    fig.savefig(output_file_prefix+'_latency_overall.png', dpi=100)
    plt.clf()


#python /c/baoying.wang/ws/gitnas/OrderBook/src/test/python/parseLatencyData.py \
#    BlockingQueue_X_bg50perSec_lt60perMin_duration600sec_20180107_115910.e2e_LxTxCx_FIX_RT.csv \
#    BlockingQueue_X_bg50perSec_lt60perMin_duration600sec_20180107_115910.sysUsage.csv \
#    BlockingQueue_X_bg50perSec_lt60perMin_duration600sec_20180107_115910.sysInfo.txt \
#    vmstat_since20171229.log.csv \
#    output_file_prefix

#http://www.diveintopython.net/scripts_and_streams/command_line_arguments.html
inputE2EFile         = sys.argv[1]
inputSysUsageFile    = sys.argv[2]
inputSysInfoFile     = sys.argv[3]
inputVmstatFile      = sys.argv[4]
inputBTraceFilePrefix= sys.argv[5]
inputMEStatsJsonFile = sys.argv[6]
output_file_prefix   = sys.argv[7]

df_sysUsage = getSysUsage(inputSysUsageFile)
df_e2e      = getE2E(inputE2EFile)

if os.path.isfile(inputVmstatFile):
    df_vmstat=getVmstat(inputVmstatFile)
    df_vmstat.drop( df_vmstat[df_vmstat["time_datetime"] < df_sysUsage["time_datetime"].min()].index, inplace=True) #remove those before engine up
    df_vmstat.drop( df_vmstat[df_vmstat["time_datetime"] > df_sysUsage["time_datetime"].max()].index, inplace=True) #remove those after  engine down
else:
    df_vmstat = pd.DataFrame()

df_match_us=getBTrace(inputBTraceFilePrefix)

#https://stackoverflow.com/questions/3277503/how-do-i-read-a-file-line-by-line-into-a-list
with open(inputSysInfoFile) as f:
    sysInfoContentList = f.readlines()
# you may also want to remove whitespace characters like `\n` at the end of each line
#sysInfoContentList = [x.strip() for x in sysInfoContentList]


with open(inputMEStatsJsonFile) as json_data:
    meStatsJson = json.load(json_data)


plotTitle=output_file_prefix
#drop heading 5%, since they are during system warmup. df_e2e[30:] means remove the deading 0~29
genPlot(plotTitle,df_e2e[int(df_e2e.size*0.05):],df_sysUsage,df_vmstat,sysInfoContentList,df_match_us[int(df_match_us.size*0.05):],meStatsJson,output_file_prefix)


