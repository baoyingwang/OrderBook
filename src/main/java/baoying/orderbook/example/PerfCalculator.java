package baoying.orderbook.example;

import java.util.List;

public class PerfCalculator {

    void calc(List<long[]> latencyData, int startCol, int endCol){

        for(long[] timestamps : latencyData){

            int length = timestamps.length;

            long[] phaseLatencies = new long[endCol - startCol +1]; // the last data is for the overall latency
            for(int i=startCol; i< endCol; i++){
                long phaseLatency = timestamps[i+1] - timestamps[i];
                phaseLatencies[i] = phaseLatency;
            }
            phaseLatencies[endCol - startCol] = timestamps[endCol] - timestamps[startCol];
        }

    }
}

