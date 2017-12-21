package baoying.orderbook.example;

import java.lang.management.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.nio.file.StandardOpenOption.APPEND;
import static java.nio.file.StandardOpenOption.CREATE;

public class SysPerfDataCollectionEngine {

    private final ThreadMXBean threadMXBean = ManagementFactory.getThreadMXBean();
    private final OperatingSystemMXBean osMXBean = ManagementFactory.getOperatingSystemMXBean();
    private final List<GarbageCollectorMXBean> gcMXBeans = ManagementFactory.getGarbageCollectorMXBeans();

    private final ScheduledExecutorService executor;

    //as a util, without starting executor service
    SysPerfDataCollectionEngine(){
        executor = null;
    }

    SysPerfDataCollectionEngine(long period, TimeUnit unit, Path usageFile) throws Exception{

        if (!Files.exists(usageFile)) {
            //Files.createFile(usageFile); TODO is it required to create here? since CREATE option is used next/while-writing?
            Files.write(usageFile, (csvUsageHeader() + "\n").getBytes(), APPEND, CREATE);
        }

        executor = Executors.newSingleThreadScheduledExecutor();
        Runnable command = new Runnable() {
            @Override
            public void run(){
                try {
                    Files.write(usageFile, (csvUsage() + "\n").getBytes(), APPEND, CREATE);
                }catch (Exception e){
                    //TODO log exception
                    e.printStackTrace();
                }

            }
        };
        long initialDelay = 0;
        executor.scheduleAtFixedRate( command,  initialDelay,   period,   unit);
    }

    Map<String, String> threadUsage()
    {
        Map<String, String> result = new HashMap<>();
        result.put("thread ThreadCount", String.valueOf(threadMXBean.getThreadCount()));
        return result;
    }

    private Map<String, String> memUsage(MemoryUsage memoryUsage, String prefix)
    {
        Map<String, String> result = new HashMap<>();
        result.put(prefix +"Memory Used", String.valueOf(memoryUsage.getUsed()));
        result.put(prefix +"Memory Max", String.valueOf(memoryUsage.getMax()));
        result.put(prefix +"Memory Init", String.valueOf(memoryUsage.getInit()));
        result.put(prefix +"Memory Committed",  String.valueOf(memoryUsage.getCommitted()));
        return result;
    }

    Map<String, String> memUsage()
    {
        Map<String, String> data= new TreeMap<>();
        Map<String, String> heapUsage = memUsage(ManagementFactory.getMemoryMXBean().getHeapMemoryUsage(), "heap");
        Map<String, String> nonHeapUsage = memUsage(ManagementFactory.getMemoryMXBean().getNonHeapMemoryUsage(), "non-heap");

        data.putAll(heapUsage);
        data.putAll(nonHeapUsage);

        return data;
    }

    Map<String, String> cpuUsage()
    {

        Map<String, String> data = new TreeMap<>();
        data.put("cpu SystemLoadAverage", String.valueOf(osMXBean.getSystemLoadAverage())); ////always -1 on windows
        return data;
    }

    Map<String, String> gcUsage()
    {
        Map<String, String> data = new TreeMap<>();

        AtomicInteger gcCounter = new AtomicInteger((0));
        gcMXBeans.forEach( bean -> {
            data.put("gc "+ bean.getName()+" CollectionCount", String.valueOf(bean.getCollectionCount()));
            data.put("gc "+ bean.getName()+" CollectionTime", String.valueOf(bean.getCollectionTime()));
        });

        return data;
    }

    Map<String, String> allUsage(){
        Map<String, String> usage = new TreeMap<>();
        usage.putAll(cpuUsage());
        usage.putAll(memUsage());
        usage.putAll(threadUsage());
        usage.putAll(gcUsage());
        return  usage;
    }
    Map<String, String> config(){

        Map<String, String> data = new TreeMap<>();
        data.put("os Arch", osMXBean.getArch()); //amd64
        data.put("os AvailableProcessors",String.valueOf(osMXBean.getAvailableProcessors())); //4
        data.put("os Name",String.valueOf(osMXBean.getName())); //Windows 7
        data.put("os Version",osMXBean.getVersion()); //6.1

        List<GarbageCollectorMXBean> gcMXBeans = ManagementFactory.getGarbageCollectorMXBeans();
        AtomicInteger gcCounter = new AtomicInteger((0));
        gcMXBeans.forEach( bean -> {
            data.put("gc Name " + gcCounter.incrementAndGet(), bean.getName());
        });

        RuntimeMXBean runtimeBean = ManagementFactory.getRuntimeMXBean();
        data.put("runtime BootClassPath",	runtimeBean.getBootClassPath());
        data.put("runtime ClassPath",	runtimeBean.getClassPath());
        data.put("runtime InputArguments",	String.valueOf(runtimeBean.getInputArguments()));
        data.put("runtime LibraryPath",	runtimeBean.getLibraryPath());
        data.put("runtime ManagementSpecVersion",	runtimeBean.getManagementSpecVersion());
        data.put("runtime Name",	runtimeBean.getName());
        data.put("runtime SpecName",	runtimeBean.getSpecName());
        data.put("runtime SpecVendor",	runtimeBean.getSpecVendor());
        data.put("runtime SpecVersion",	runtimeBean.getSpecVersion());
        data.put("runtime StartTime",	String.valueOf(runtimeBean.getStartTime()));
        data.put("runtime SystemProperties",	String.valueOf(runtimeBean.getSystemProperties()));
        data.put("runtime Uptime",	String.valueOf(runtimeBean.getUptime()));
        data.put("runtime VmName",	runtimeBean.getVmName());
        data.put("runtime VmVendor",	runtimeBean.getVmVendor());
        data.put("runtime VmVersion",	runtimeBean.getVmVersion());
        return data;
    }

    String csvUsageHeader(){
        StringBuilder usageHeader = new StringBuilder("time");
        {
            Map<String, String> usage = allUsage();
            usage.keySet().forEach(it2 -> usageHeader.append(",").append(it2));
        }

        return usageHeader.toString();
    }

    String csvUsage(){
        Map<String, String> usage = allUsage();
        StringBuilder usageData = new StringBuilder(Instant.now().toString());
        usage.values().forEach(it -> usageData.append(",").append(it));

        return usageData.toString();
    }

}
