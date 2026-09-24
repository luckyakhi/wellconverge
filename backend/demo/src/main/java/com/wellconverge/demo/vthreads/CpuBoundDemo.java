package com.wellconverge.demo.vthreads;

import com.wellconverge.demo.Demo;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadFactory;

/**
 * Virtual threads don't help CPU-bound work.
 *
 * <ol>
 *   <li><b>Throughput:</b> there are still only #cores carriers doing the computing, so N CPU-bound tasks
 *       take the same wall time on virtual threads as on a fixed pool of #cores platform threads.</li>
 *   <li><b>Latency:</b> the virtual-thread scheduler is not time-sliced — a virtual thread only gives up its
 *       carrier when it blocks (or yields). A few CPU hogs can starve latency-sensitive virtual threads,
 *       whereas the OS preempts platform threads every few ms.</li>
 * </ol>
 * Keep CPU-heavy work (hashing, compression, big JSON) on a bounded platform pool / {@code ForkJoinPool}.
 */
public final class CpuBoundDemo {

    private static final long WORK_ITERATIONS = 200_000_000L;

    public static void main(String[] args) throws Exception {
        Demo.header("Virtual threads: no help for CPU-bound work");

        int cores = Demo.cores();
        int tasks = cores * 4;
        long virtualMs = throughput(Executors.newVirtualThreadPerTaskExecutor(), tasks);
        long platformMs = throughput(Executors.newFixedThreadPool(cores), tasks);
        System.out.printf("%d CPU-bound tasks: virtual threads %,d ms vs %d platform threads %,d ms%n",
                tasks, virtualMs, cores, platformMs);

        System.out.println("\nStarvation: saturate every core with CPU hogs, then start one tiny task...");
        starvation(Thread.ofVirtual().factory(), "virtual ");
        starvation(Thread.ofPlatform().factory(), "platform");
    }

    private static long throughput(ExecutorService executor, int tasks) throws Exception {
        return Demo.timeMs(() -> {
            try (executor) {
                List<Future<Long>> results = new ArrayList<>();
                for (int i = 0; i < tasks; i++) {
                    results.add(executor.submit(() -> burn(WORK_ITERATIONS)));
                }
                for (Future<Long> result : results) {
                    Demo.sink(result.get());
                }
            }
        });
    }

    private static void starvation(ThreadFactory factory, String kind) throws Exception {
        List<Thread> hogs = new ArrayList<>();
        for (int i = 0; i < Demo.cores(); i++) {
            Thread hog = factory.newThread(() -> Demo.sink(burnFor(Duration.ofMillis(1500))));
            hog.start();
            hogs.add(hog);
        }
        Thread.sleep(100); // let the hogs occupy every carrier / core

        long requestedAt = System.nanoTime();
        CompletableFuture<Long> startedAt = new CompletableFuture<>();
        factory.newThread(() -> startedAt.complete(System.nanoTime())).start();
        long waitedMs = (startedAt.get() - requestedAt) / 1_000_000;

        for (Thread hog : hogs) {
            hog.join();
        }
        System.out.printf("  %s: the tiny task waited %,5d ms for a CPU%n", kind, waitedMs);
    }

    /** Fixed amount of pure CPU work (xorshift), no blocking. */
    static long burn(long iterations) {
        long x = 88172645463325252L;
        for (long i = 0; i < iterations; i++) {
            x ^= x << 13;
            x ^= x >>> 7;
            x ^= x << 17;
        }
        return x;
    }

    private static long burnFor(Duration duration) {
        long deadline = System.nanoTime() + duration.toNanos();
        long x = 0;
        while (System.nanoTime() < deadline) {
            x += burn(1_000);
        }
        return x;
    }
}
