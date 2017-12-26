import sys
sys.path.append('C:\\baoying.wang\\ws\\github\\ML_Kaggle')
from by_common_import import *
import os
from datetime import datetime

#cd /c/baoying.wang/ws/gitnas/OrderBook/log   
#python /c/baoying.wang/ws/gitnas/OrderBook/src/test/python/parseLatencyData.py LatencyData_test.start20171225_071647.342Z.csv sysUsage_app.start20171225_071605.351Z.csv picPrefix
def processLatencyFile(inputLatencyFile, outputSummaryFile, outputPicPrefix):

	#os.chdir("C:\\baoying.wang\\ws\\gitnas\\OrderBook\\build\\libs\\log")
	df_latency=pd.read_csv(inputLatencyFile)
	
	#the heading lines are always bad performance(why?warm up?), below can be used to remove them(heading 500)
	#df_latency=df_latency[51:df_latency.size]
	
	df_latency["put2InputQ_us"]=df_latency["put2InputQ"]/1000
	df_latency["pickFromInputQ_us"]=df_latency["pickFromInputQ"]/1000
	df_latency["match_us"]=df_latency["match"]/1000
	df_latency["pickFromOutputQ_us"]=df_latency["pickFromOutputQ"]/1000
	df_latency.drop(['put2InputQ', 'pickFromInputQ', 'match', 'pickFromOutputQ'], axis=1, inplace=True)
 
	#https://stackoverflow.com/questions/31247198/python-pandas-write-content-of-dataframe-into-text-file
	describeResult = df_latency.describe(percentiles=[.25,.5,.75,.9, .95, .99 ])
	describeResult.to_csv(outputSummaryFile)
	
	#https://stackoverflow.com/questions/19079143/how-to-plot-time-series-in-python
	from datetime import datetime
	df_latency['recvTime_datetime'] = df_latency['recvTime'].map(lambda x: datetime.strptime(x,"%Y-%m-%dT%H:%M:%S.%fZ"))

	#https://matplotlib.org/api/pyplot_api.html
	#http://blog.csdn.net/han_xiaoyang/article/details/49797143
	#https://matplotlib.org/api/_as_gen/matplotlib.pyplot.subplot.html#matplotlib.pyplot.subplot
	plt.figure(1)
	fig=plt.gcf()
	#https://stackoverflow.com/questions/39370584/how-do-you-add-an-overall-title-to-a-figure-with-subplots-in-matplotlib
	fig.suptitle('from '+inputLatencyFile)
	fig.set_size_inches(10, 8)
	
	plt.subplot(2,3,1)
	plt.plot(df_latency["recvTime_datetime"], df_latency["put2InputQ_us"]  , label="put2InputQ_us" ,  marker='h' )
	plt.ylabel(u"us")
	plt.title(u"put2InputQ_us")

	plt.subplot(2,3,2)
	plt.plot(df_latency["recvTime_datetime"], df_latency["pickFromInputQ_us"]  , label="pickFromInputQ_us" , marker='h' )
	plt.ylabel(u"us")
	plt.title(u"pickFromInputQ_us")
	
	plt.subplot(2,3,3)
	plt.plot(df_latency["recvTime_datetime"], df_latency["pickFromOutputQ_us"] , label="pickFromOutputQ_us",  marker='h')
	plt.ylabel(u"us")
	plt.title(u"pickFromOutputQ_us")
	
	plt.subplot(2,3,4)
	plt.plot(df_latency["recvTime_datetime"], df_latency["match_us"]  , label="match_us" ,  marker='h' )
	plt.ylabel(u"us")
	plt.title(u"match_us")	
	
	plt.subplot(2,3,5)
	plt.text(0, 0.2 ,describeResult.to_string())
	plt.title(u"latency summary")		
	
	fig=plt.gcf()
	fig.savefig(outputPicPrefix+'_latency_overall.png')
	plt.clf()

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
	df=pd.read_csv(sysUsageFile)
	
	#replace the space with understand in column name - https://github.com/pandas-dev/pandas/issues/6508
	cols = df.columns
	cols = cols.map(lambda x: x.replace(' ', '_') )
	df.columns = cols

	df['time_datetime'] = df['time'].map(lambda x: datetime.strptime(x,"%Y-%m-%dT%H:%M:%S.%fZ"))
	return df

	
def genPlot(plotTitle,df_latency,df_sysUsage, outputPicPrefix):

	#https://matplotlib.org/api/pyplot_api.html
	#http://blog.csdn.net/han_xiaoyang/article/details/49797143
	#https://matplotlib.org/api/_as_gen/matplotlib.pyplot.subplot.html#matplotlib.pyplot.subplot
	plt.figure(1)
	fig=plt.gcf()
	#https://stackoverflow.com/questions/39370584/how-do-you-add-an-overall-title-to-a-figure-with-subplots-in-matplotlib
	fig.suptitle(plotTitle)
	fig.set_size_inches(12, 16)
	
	#http://blog.csdn.net/han_xiaoyang/article/details/49797143
	#https://matplotlib.org/users/gridspec.html
	plt.subplot2grid((4,3),(0,0))  #4 rows, 3 columns. The index start from 0.
	
	
	#================================df_latency==============================
	plt.subplot2grid((4,3),(0,0), colspan=2) 
	#https://stackoverflow.com/questions/31247198/python-pandas-write-content-of-dataframe-into-text-file
	describeResult = df_latency.describe(percentiles=[.25,.5,.75,.9, .95, .99 ])
	plt.text(0, 0.2 ,describeResult.to_string())
	plt.title(u"latency summary")	

	plt.subplot2grid((5,3),(0,2))
	plt.plot(df_latency["recvTime_datetime"], df_latency["pickFromInputQ_us"]  , label="pickFromInputQ_us" , marker='h' )
	plt.ylabel(u"us")
	plt.title(u"pickFromInputQ_us")

	plt.subplot2grid((4,3),(1,0)) 
	plt.plot(df_latency["recvTime_datetime"], df_latency["put2InputQ_us"]  , label="put2InputQ_us" ,  marker='h' )
	plt.ylabel(u"us")
	plt.title(u"put2InputQ_us")

	plt.subplot2grid((4,3),(1,1)) 
	plt.plot(df_latency["recvTime_datetime"], df_latency["match_us"]  , label="match_us" ,  marker='h' )
	plt.ylabel(u"us")
	plt.title(u"match_us")	
	
	plt.subplot2grid((4,3),(1,2)) 
	plt.plot(df_latency["recvTime_datetime"], df_latency["pickFromOutputQ_us"] , label="pickFromOutputQ_us",  marker='h')
	plt.ylabel(u"us")
	plt.title(u"pickFromOutputQ_us")
	

	
	#================================df_sysUsage==== 

	plt.subplot2grid((4,3),(2,0))
	plt.plot(df_sysUsage["time_datetime"], df_sysUsage["cpu_ProcessCpuLoad"]  , label="cpu_ProcessCpuLoad" ,  marker='h' )
	plt.ylabel(u"%")
	plt.title(u"cpu_ProcessCpuLoad")

	plt.subplot2grid((4,3),(2,1))
	plt.plot(df_sysUsage["time_datetime"], df_sysUsage["cpu_SystemLoadAverage"]  , label="cpu_SystemLoadAverage" ,  marker='h' )
	plt.ylabel(u"%")
	plt.title(u"cpu_SystemLoadAverage")

	plt.subplot2grid((4,3),(2,2))
	plt.plot(df_sysUsage["time_datetime"], df_sysUsage["thread_ThreadCount"]  , label="thread_ThreadCount" ,  marker='h' )
	plt.title(u"thread_ThreadCount")
	
	plt.subplot2grid((4,3),(3,0))
	plt.plot(df_sysUsage["time_datetime"], df_sysUsage["heapMemory_Used"]/(1024*1024)        , label="heapMemory_Used" ,  marker='h' )
	plt.plot(df_sysUsage["time_datetime"], df_sysUsage["heapMemory_Committed"]/(1024*1024)   , label="heapMemory_Committed" ,  marker='h' )
	plt.title(u"heapMemory (MB)")	
	plt.legend((u'Used', u'Committed'),loc='best') 
	
	plt.subplot2grid((4,3),(4,0))
	plt.plot(df_sysUsage["time_datetime"], df_sysUsage["non-heapMemory_Used"]/(1024*1024)       , label="non-heapMemory_Used" ,  marker='h' )
	plt.plot(df_sysUsage["time_datetime"], df_sysUsage["non-heapMemory_Committed"]/(1024*1024)  , label="non-heapMemory_Committed" ,  marker='h' )
	plt.title(u"non-heapMemory (MB)")	
	plt.legend((u'Used', u'Committed'),loc='best') 
	
	plt.subplot2grid((4,3),(3,1))
	plt.plot(df_sysUsage["time_datetime"], df_sysUsage["gc_ConcurrentMarkSweep_CollectionCount"] , label="gc_ConcurrentMarkSweep_CollectionCount",  marker='h')
	plt.plot(df_sysUsage["time_datetime"], df_sysUsage["gc_ParNew_CollectionCount"]              , label="gc_ParNew_CollectionCount"             ,  marker='h')
	plt.title(u"GC Collection Count")	
	plt.legend((u'ConcurrentMarkSweep', u'ParNew'),loc='best') 
	
	plt.subplot2grid((4,3),(4,1))
	plt.plot(df_sysUsage["time_datetime"], df_sysUsage["gc_ConcurrentMarkSweep_CollectionTime"] , label="gc_ConcurrentMarkSweep_CollectionTime",  marker='h')
	plt.plot(df_sysUsage["time_datetime"], df_sysUsage["gc_ParNew_CollectionTime"]              , label="gc_ParNew_CollectionTime"             ,  marker='h')
	plt.title(u"GC Collection Accumulated Time-MS? to be confirmed")	
	plt.legend((u'ConcurrentMarkSweep', u'ParNew'),loc='best') 
	

	#
	
	fig=plt.gcf()
	fig.savefig(outputPicPrefix+'_latency_overall.png', dpi=200)
	plt.clf()

	
#http://www.diveintopython.net/scripts_and_streams/command_line_arguments.html
inputLatencyFile=sys.argv[1]
inputSysUsageFile=sys.argv[2]
outputPicPrefix=sys.argv[3]
#processLatencyFile(inputLatencyFile, outputSummaryFile,outputPicPrefix)


df_latency=getLatency(inputLatencyFile)
df_sysUsage=getSysUsage(inputSysUsageFile)
genPlot(inputLatencyFile,df_latency,df_sysUsage,outputPicPrefix)


