import sys
sys.path.append('C:\\baoying.wang\\ws\\github\\ML_Kaggle')
from by_common_import import *
import os


#/c/baoying.wang/ws/gitnas/OrderBook/log   
#python /c/baoying.wang/ws/gitnas/OrderBook/src/test/python/parseLatencyData.py LatencyData_OverallStart_20171216_132132.485Z.csv LatencyData_OverallStart_20171216_132132.485Z.summary.txt GC.summary.csv
def process(inputLatencyFile, outputSummaryFile, inputGCSummaryFile):

	#os.chdir("C:\\baoying.wang\\ws\\gitnas\\OrderBook\\build\\libs\\log")
	df_latency=pd.read_csv(inputLatencyFile)
	
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
	plt.plot(df_latency["recvTime_datetime"], df_latency["pickFromInputQ_us"]  , label="pickFromInputQ_us" , color='r', marker='o')
	#plt.plot(df_latency["recvTime_datetime"], df_latency["match_us"],  marker='o')
	plt.plot(df_latency["recvTime_datetime"], df_latency["pickFromOutputQ_us"] , label="pickFromOutputQ_us", color='g', marker='o')
	plt.legend(loc='best')
	plt.savefig('pickFromInputQ_us_and_pickFromOutputQ_us.png', dpi=1000, format='png')
	#https://stackoverflow.com/questions/741877/how-do-i-tell-matplotlib-that-i-am-done-with-a-plot
	plt.clf()
	
	
	#gcLogTime,took_us,type
	#2017-12-16T13:21:26.403Z,190.0,Total time for which application threads were stopped
	df_gc=pd.read_csv(inputGCSummaryFile)
	df_gc['gcLogTime_datetime'] = df_gc['gcLogTime'].map(lambda x: datetime.strptime(x,"%Y-%m-%dT%H:%M:%S.%fZ"))
	plt.plot(df_gc["gcLogTime_datetime"], df_gc["took_us"],  label="gc took_us", color='b', marker='o')
	plt.legend(loc='best')
	plt.savefig('gcLogTime_datetime.png', dpi=1000, format='png')
	#https://stackoverflow.com/questions/741877/how-do-i-tell-matplotlib-that-i-am-done-with-a-plot
	plt.clf()
	
	#TODO 
	
	#https://codeyarns.com/2014/10/27/how-to-change-size-of-matplotlib-plot/
	#fig_size = plt.rcParams["figure.figsize"]
	#fig_size[0] = 24
	#fig_size[1] = 18
	#plt.rcParams["figure.figsize"] = fig_size
	
	#plt.savefig('latency_gc.png', dpi=1000, format='png')

	
#http://www.diveintopython.net/scripts_and_streams/command_line_arguments.html
inputLatencyFile=sys.argv[1]
outputSummaryFile=sys.argv[2]
inputGCSummaryFile=sys.argv[3]
process(inputLatencyFile, outputSummaryFile, inputGCSummaryFile)


#%d/%m/%y %H:%M
