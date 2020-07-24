package baoying.orderbook;

import org.junit.Test;

public class JavaTimePerfTest {



	@Test
	public void testSystem_nanoTimePerf(){

		int loopCount = 1000_000;

		long start = System.nanoTime();
		for(int i=0; i<loopCount; i++ ){
			System.nanoTime();
		}
		long end = System.nanoTime();
		System.out.println("avg nano second per System.nanoTime():" + (end - start)/loopCount);

	}

	@Test
	public void testSystem_currentTimeMillis(){

		int loopCount = 1000_000;

		long start = System.nanoTime();
		for(int i=0; i<loopCount; i++ ){
			System.currentTimeMillis();
		}
		long end = System.nanoTime();
		System.out.println("avg nano second per System.currentTimeMillis():" + (end - start)/loopCount);

	}
}
