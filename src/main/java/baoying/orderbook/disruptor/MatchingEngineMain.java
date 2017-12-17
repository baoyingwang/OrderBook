package baoying.orderbook.disruptor;

import baoying.orderbook.MatchingEngine;
import com.lmax.disruptor.dsl.Disruptor;
import com.lmax.disruptor.RingBuffer;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

public class MatchingEngineMain
{
    public static void main(String[] args) throws Exception
    {
        MatchingEngine engine = new MatchingEngine("USDJPY");
        Executor executor = Executors.newSingleThreadExecutor();
        int bufferSize = 1024;
        Disruptor<OriginalOrderEvent> disruptor = new Disruptor<>(OriginalOrderEvent::new, bufferSize, executor);

        // Connect the handler
        disruptor.handleEventsWith((event, sequence, endOfBatch) -> System.out.println("Event: " + event));

        // Start the Disruptor, starts all threads running
        disruptor.start();

        // Get the ring buffer from the Disruptor to be used for publishing.
        RingBuffer<OriginalOrderEvent> ringBuffer = disruptor.getRingBuffer();

        for (long l = 0; true; l++)
        {
            //bb.putLong(0, l);
            //ringBuffer.publishEvent((event, sequence, buffer) -> event.set(buffer.getLong(0)), bb);
            Thread.sleep(1000);
        }
    }
}
