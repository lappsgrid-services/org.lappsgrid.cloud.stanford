package org.lappsgrid.cloud.stanford;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Manages a CountDownLatch so threads can send "signals" to one another.
 *
 */
public class Signal {

	private CountDownLatch latch;

	public Signal() {
		this(1);
	}

	public Signal(int n) {
		latch = new CountDownLatch(n);
	}

	public void send() {
		latch.countDown();
	}

	public void await() throws InterruptedException {
		latch.await();
	}

	public void await(long time, TimeUnit unit) throws InterruptedException {
		latch.await(time, unit);
	}
}
