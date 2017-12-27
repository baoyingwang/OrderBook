import sys
sys.path.append('C:\\baoying.wang\\ws\\github\\ML_Kaggle')
from by_common_import import *
import os
from datetime import datetime


#recvTime_datetime    
#put2InputQ_us
#pickFromInputQ_us
#match_us
#pickFromOutputQ_us
def getLatency(inputLatencyFile):

    #os.chdir("C:\\baoying.wang\\ws\\gitnas\\OrderBook\\build\\libs\\log")
    df_latency=pd.read_csv(inputLatencyFile)
    
    #the heading lines are always bad performance(why?warm up?), below can be used to remove them(heading 500)
    #df_latency=df_latency[51:df_latency.size]
    
    df_latency["put2InputQ_us"]=df_latency["put2InputQ"]/1000
    df_latency["pickFromInputQ_us"]=df_latency["pickFromInputQ"]/1000
    df_latency["match_us"]=df_latency["match"]/1000
    df_latency["pickFromOutputQ_us"]=df_latency["pickFromOutputQ"]/1000
    df_latency.drop(['put2InputQ', 'pickFromInputQ', 'match', 'pickFromOutputQ'], axis=1, inplace=True)
 
    
    #https://stackoverflow.com/questions/19079143/how-to-plot-time-series-in-python
    df_latency['recvTime_datetime'] = df_latency['recvTime'].map(lambda x: datetime.strptime(x,"%Y-%m-%dT%H:%M:%S.%fZ"))
    
    return df_latency


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

    #os.chdir("C:\\baoying.wang\\ws\\gitnas\\OrderBook\\build\\libs\\log")
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
    
    #os.chdir("C:\\baoying.wang\\ws\\gitnas\\OrderBook\\build\\libs\\log")
    df=pd.read_csv(inputVmstatFile)
    
    #procs_r,procs_b,memory_swpd,memory_free,memory_buff,memory_cache,swap_si,swap_so,io_bi,io_bo,system_in,system_cs,system_us,system_sy,system_id,system_wa,system_st,timestamp_day,timestamp_time
    #0,0,0,5605964,35796,257216,0,0,1492,85,225,853,3,4,92,1,0,2017-12-26,05:50:22
    df['time']=df['timestamp_day']+'T'+df['timestamp_time']+'Z'
    df['time_datetime'] = df['time'].map(lambda x: datetime.strptime(x,"%Y-%m-%dT%H:%M:%SZ"))
    return df
    
    
def genPlot(plotTitle,df_latency,df_sysUsage, df_vmstat, outputPicPrefix):

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
    #plt.subplot2grid((7,3),(0,0))  #5 rows, 3 columns. The index start from 0.
    
    
    #================================df_latency==============================
    plt.subplot2grid((7,3),(0,0), colspan=2) 
    #https://stackoverflow.com/questions/31247198/python-pandas-write-content-of-dataframe-into-text-file
    describeResult = df_latency.describe(percentiles=[.25,.5,.75,.9, .95, .99 ])
    plt.text(0, 0.1 ,describeResult.to_string())
    plt.title(plotTitle + " latency summary" )

    plt.subplot2grid((7,3),(0,2))
    ax=plt.plot(df_latency["recvTime_datetime"], df_latency["pickFromInputQ_us"]  , label="pickFromInputQ_us" , marker='h' )
    #https://plot.ly/matplotlib/axes/
    plt.ylabel(u"us")
    plt.title(u"pickFromInputQ_us")
    plt.xticks(rotation=90)#fig.autofmt_xdate() does not work for me. why? https://stackoverflow.com/questions/10998621/rotate-axis-text-in-python-matplotlib

    plt.subplot2grid((7,3),(1,0))
    plt.plot(df_latency["recvTime_datetime"], df_latency["put2InputQ_us"]  , label="put2InputQ_us" ,  marker='h' )
    plt.ylabel(u"us")
    plt.title(u"put2InputQ_us")
    plt.xticks(rotation=90)#fig.autofmt_xdate() does not work for me. why? https://stackoverflow.com/questions/10998621/rotate-axis-text-in-python-matplotlib

    plt.subplot2grid((7,3),(1,1)) 
    plt.plot(df_latency["recvTime_datetime"], df_latency["match_us"]  , label="match_us" ,  marker='h' )
    plt.ylabel(u"us")
    plt.title(u"match_us")    
    plt.xticks(rotation=90)#fig.autofmt_xdate() does not work for me. why? https://stackoverflow.com/questions/10998621/rotate-axis-text-in-python-matplotlib

    plt.subplot2grid((7,3),(1,2)) 
    plt.plot(df_latency["recvTime_datetime"], df_latency["pickFromOutputQ_us"] , label="pickFromOutputQ_us",  marker='h')
    plt.ylabel(u"us")
    plt.title(u"pickFromOutputQ_us")
    plt.xticks(rotation=90)#fig.autofmt_xdate() does not work for me. why? https://stackoverflow.com/questions/10998621/rotate-axis-text-in-python-matplotlib

    
    #================================df_sysUsage==== 

    plt.subplot2grid((7,3),(2,0))
    plt.plot(df_sysUsage["time_datetime"], df_sysUsage["cpu_ProcessCpuLoad"]  , label="cpu_ProcessCpuLoad" ,  marker='h' )
    plt.ylabel(u"%")
    plt.title(u"cpu_ProcessCpuLoad")
    plt.xticks(rotation=90)#fig.autofmt_xdate() does not work for me. why? https://stackoverflow.com/questions/10998621/rotate-axis-text-in-python-matplotlib

    plt.subplot2grid((7,3),(2,1))
    plt.plot(df_sysUsage["time_datetime"], df_sysUsage["cpu_SystemLoadAverage"]  , label="cpu_SystemLoadAverage" ,  marker='h' )
    plt.title(u"cpu_SystemLoadAverage")
    plt.xticks(rotation=90)#fig.autofmt_xdate() does not work for me. why? https://stackoverflow.com/questions/10998621/rotate-axis-text-in-python-matplotlib

    plt.subplot2grid((7,3),(2,2))
    plt.plot(df_sysUsage["time_datetime"], df_sysUsage["thread_ThreadCount"]  , label="thread_ThreadCount" ,  marker='h' )
    plt.title(u"thread_ThreadCount")
    plt.xticks(rotation=90)#fig.autofmt_xdate() does not work for me. why? https://stackoverflow.com/questions/10998621/rotate-axis-text-in-python-matplotlib

    plt.subplot2grid((7,3),(3,0))
    plt.plot(df_sysUsage["time_datetime"], df_sysUsage["heapMemory_Used"]/(1024*1024)        , label="heapMemory_Used" ,  marker='h' )
    plt.plot(df_sysUsage["time_datetime"], df_sysUsage["heapMemory_Committed"]/(1024*1024)   , label="heapMemory_Committed" ,  marker='h' )
    plt.title(u"heapMemory (MB)")    
    plt.legend((u'Used', u'Committed'),loc='best') 
    plt.xticks(rotation=90)#fig.autofmt_xdate() does not work for me. why? https://stackoverflow.com/questions/10998621/rotate-axis-text-in-python-matplotlib

    plt.subplot2grid((7,3),(4,0))
    plt.plot(df_sysUsage["time_datetime"], df_sysUsage["non-heapMemory_Used"]/(1024*1024)       , label="non-heapMemory_Used" ,  marker='h' )
    plt.plot(df_sysUsage["time_datetime"], df_sysUsage["non-heapMemory_Committed"]/(1024*1024)  , label="non-heapMemory_Committed" ,  marker='h' )
    plt.title(u"non-heapMemory (MB)")    
    plt.legend((u'Used', u'Committed'),loc='best') 
    plt.xticks(rotation=90)#fig.autofmt_xdate() does not work for me. why? https://stackoverflow.com/questions/10998621/rotate-axis-text-in-python-matplotlib

    plt.subplot2grid((7,3),(3,1))
    plt.plot(df_sysUsage["time_datetime"], df_sysUsage["gc_ConcurrentMarkSweep_CollectionCount"] , label="gc_ConcurrentMarkSweep_CollectionCount",  marker='h')
    plt.plot(df_sysUsage["time_datetime"], df_sysUsage["gc_ParNew_CollectionCount"]              , label="gc_ParNew_CollectionCount"             ,  marker='h')
    plt.title(u"GC Collection Count")    
    plt.legend((u'ConcurrentMarkSweep', u'ParNew'),loc='best') 
    plt.xticks(rotation=90)#fig.autofmt_xdate() does not work for me. why? https://stackoverflow.com/questions/10998621/rotate-axis-text-in-python-matplotlib

    plt.subplot2grid((7,3),(4,1))
    plt.plot(df_sysUsage["time_datetime"], df_sysUsage["gc_ConcurrentMarkSweep_CollectionTime"] , label="gc_ConcurrentMarkSweep_CollectionTime",  marker='h')
    plt.plot(df_sysUsage["time_datetime"], df_sysUsage["gc_ParNew_CollectionTime"]              , label="gc_ParNew_CollectionTime"             ,  marker='h')
    plt.title(u"GC Collection Accumulated Time-MS? to be confirmed")    
    plt.legend((u'ConcurrentMarkSweep', u'ParNew'),loc='best') 
    plt.xticks(rotation=90)#fig.autofmt_xdate() does not work for me. why? https://stackoverflow.com/questions/10998621/rotate-axis-text-in-python-matplotlib

    #==========vmstat==========
    #procs_r,procs_b,memory_swpd,memory_free,memory_buff,memory_cache,swap_si,swap_so,io_bi,io_bo,system_in,system_cs,cpu_us,cpu_sy,cpu_id,cpu_wa,cpu_st,timestamp_day,timestamp_time
    #0,0,0,5605964,35796,257216,0,0,1492,85,225,853,3,4,92,1,0,2017-12-26,05:50:22
    plt.subplot2grid((7,3),(5,0))
    plt.plot(df_vmstat["time_datetime"], df_vmstat["procs_r"] , label="procs_r",  marker='h')
    plt.plot(df_vmstat["time_datetime"], df_vmstat["procs_b"] , label="procs_b",  marker='h')
    plt.title(u"vmstat procs")    
    plt.legend((u'procs_r', u'procs_b'),loc='best') 
    plt.xticks(rotation=90)#fig.autofmt_xdate() does not work for me. why? https://stackoverflow.com/questions/10998621/rotate-axis-text-in-python-matplotlib
    
    plt.subplot2grid((7,3),(5,1))
    plt.plot(df_vmstat["time_datetime"], df_vmstat["memory_swpd"] , label="memory_swpd",  marker='h')
    plt.plot(df_vmstat["time_datetime"], df_vmstat["memory_free"] , label="memory_free",  marker='h')
    plt.plot(df_vmstat["time_datetime"], df_vmstat["memory_buff"] , label="memory_buff",  marker='h')
    plt.plot(df_vmstat["time_datetime"], df_vmstat["memory_cache"] , label="memory_cache",  marker='h')    
    plt.title(u"vmstat memory")    
    plt.legend((u'memory_swpd',u'memory_free', u'memory_buff',  u'memory_cache'),loc='best')
    plt.xticks(rotation=90)#fig.autofmt_xdate() does not work for me. why? https://stackoverflow.com/questions/10998621/rotate-axis-text-in-python-matplotlib

    plt.subplot2grid((7,3),(5,2))
    plt.plot(df_vmstat["time_datetime"], df_vmstat["swap_si"] , label="swap_si",  marker='h')
    plt.plot(df_vmstat["time_datetime"], df_vmstat["swap_so"] , label="swap_so",  marker='h')
    plt.title(u"vmstat swap")    
    plt.legend((u'swap_si', u'swap_so'),loc='best') 
    plt.xticks(rotation=90)#fig.autofmt_xdate() does not work for me. why? https://stackoverflow.com/questions/10998621/rotate-axis-text-in-python-matplotlib

    plt.subplot2grid((7,3),(6,0))
    plt.plot(df_vmstat["time_datetime"], df_vmstat["io_bi"] , label="io_bi",  marker='h')
    plt.plot(df_vmstat["time_datetime"], df_vmstat["io_bo"] , label="io_bo",  marker='h')
    plt.title(u"vmstat io")    
    plt.legend((u'io_bi', u'io_bo'),loc='best') 
    plt.xticks(rotation=90)#fig.autofmt_xdate() does not work for me. why? https://stackoverflow.com/questions/10998621/rotate-axis-text-in-python-matplotlib

    plt.subplot2grid((7,3),(6,1))
    plt.plot(df_vmstat["time_datetime"], df_vmstat["system_in"] , label="system_in",  marker='h')
    plt.plot(df_vmstat["time_datetime"], df_vmstat["system_cs"] , label="system_cs",  marker='h')
    plt.title(u"vmstat System")    
    plt.legend((u'system_in', u'system_cs'),loc='best') 
    plt.xticks(rotation=90)#fig.autofmt_xdate() does not work for me. why? https://stackoverflow.com/questions/10998621/rotate-axis-text-in-python-matplotlib

    #cpu_us,cpu_sy,cpu_id,cpu_wa,cpu_st
    plt.subplot2grid((7,3),(6,2))
    plt.plot(df_vmstat["time_datetime"], df_vmstat["cpu_us"] , label="cpu_us",  marker='h')
    plt.plot(df_vmstat["time_datetime"], df_vmstat["cpu_sy"] , label="cpu_sy",  marker='h')
    plt.plot(df_vmstat["time_datetime"], df_vmstat["cpu_id"] , label="cpu_id",  marker='h')
    plt.plot(df_vmstat["time_datetime"], df_vmstat["cpu_wa"] , label="cpu_wa",  marker='h')    
    plt.plot(df_vmstat["time_datetime"], df_vmstat["cpu_st"] , label="cpu_st",  marker='h')    
    plt.title(u"vmstat cpu")    
    plt.legend((u'cpu_us',u'cpu_sy', u'cpu_id',  u'cpu_wa', u'cpu_st'),loc='best') 
    plt.xticks(rotation=90)#fig.autofmt_xdate() does not work for me. why? https://stackoverflow.com/questions/10998621/rotate-axis-text-in-python-matplotlib
    
    fig=plt.gcf()
    fig.savefig(outputPicPrefix+'_latency_overall.png', dpi=200)
    plt.clf()


#cd /c/Users/U0127650/Desktop/test_result_20171226_v2/tmp
#python /c/baoying.wang/ws/gitnas/OrderBook/src/test/python/parseLatencyData.py \
#    Disruptor_BusySpinWaitStrategy_bg50perSec_20171226_061723_duration300Sec.latency.data.csv \
#    Disruptor_BusySpinWaitStrategy_bg50perSec_20171226_061723_duration300Sec.sysUsage.csv \
#    vmstat_begin.from20171226_061037.log.csv \
#    picPrefix

#http://www.diveintopython.net/scripts_and_streams/command_line_arguments.html
inputLatencyFile=sys.argv[1]
inputSysUsageFile=sys.argv[2]
inputVmstatFile=sys.argv[3]
outputPicPrefix=sys.argv[4]


df_latency=getLatency(inputLatencyFile)
df_sysUsage=getSysUsage(inputSysUsageFile)

df_vmstat=getVmstat(inputVmstatFile)
min_time=df_sysUsage["time_datetime"].min()
max_time=df_sysUsage["time_datetime"].max()
df_vmstat.drop( df_vmstat[df_vmstat["time_datetime"] < min_time].index, inplace=True)
df_vmstat.drop( df_vmstat[df_vmstat["time_datetime"] > max_time].index, inplace=True)


genPlot(inputLatencyFile,df_latency,df_sysUsage,df_vmstat,outputPicPrefix)


