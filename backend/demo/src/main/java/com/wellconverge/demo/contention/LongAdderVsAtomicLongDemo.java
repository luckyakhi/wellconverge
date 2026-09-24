package com.wellconverge.demo.contention;

import com.wellconverge.demo.Demo;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;

/**
 * LongAdder over AtomicLong under contention.
 *
 * <p>{@link AtomicLong#incrementAndGet()} is a single CAS (or {@code lock xadd}) on one cache line: with many
 * writers the line bounces between cores and CAS retries pile up — throughput goes <i>down</i> as you add
 * threads. {@link LongAdder} stripes the count over a {@code base} plus lazily-created, {@code @Contended}
 * padded {@code Cell}s picked by a per-thread hash; on CAS failure a thread re-hashes to another cell.
 * {@code sum()} adds them up.
 *
 * <p>Trade-offs: {@code sum()} is not an atomic snapshot (concurrent updates may or may not be counted),
 * reading is O(cells), and it uses more memory. Use AtomicLong when you need the post-increment value
 * (sequence/ID generation, CAS-based state machines) or contention is low; use LongAdder for hot
 * write-mostly statistics (request counters, metrics). {@code LongAccumulator} generalises it (max, min...).
 */
public final class LongAdderVsAtomicLongDemo {

    private static final long INCREMENTS_PER_THREAD = 5_000_000L;

    public static void main(String[] args) throws Exception {
        Demo.header("Contention: LongAdder vs AtomicLong");
        for (int threads : new int[] {1, Demo.cores(), Demo.cores() * 2}) {
            AtomicLong atomic = new AtomicLong();
            long atomicMs = hammer(threads, () -> {
                for (long i = 0; i < INCREMENTS_PER_THREAD; i++) {
                    atomic.incrementAndGet();
                }
            });

            LongAdder adder = new LongAdder();
            long adderMs = hammer(threads, () -> {
                for (long i = 0; i < INCREMENTS_PER_THREAD; i++) {
                    adder.increment();
                }
            });

            System.out.printf("%2d threads: AtomicLong %,6d ms | LongAdder %,6d ms   (both counted %,d / %,d)%n",
                    threads, atomicMs, adderMs, atomic.get(), adder.sum());
        }
    }

    private static long hammer(int threads, Runnable work) throws Exception {
        CountDownLatch startGate = new CountDownLatch(1);
        Thread[] workers = new Thread[threads];
        for (int t = 0; t < threads; t++) {
            workers[t] = new Thread(() -> {
                try {
                    startGate.await();
                } catch (InterruptedException e) {
                    return;
                }
                work.run();
            });
            workers[t].start();
        }
        return Demo.timeMs(() -> {
            startGate.countDown();
            for (Thread worker : workers) {
                worker.join();
            }
        });
    }
}
