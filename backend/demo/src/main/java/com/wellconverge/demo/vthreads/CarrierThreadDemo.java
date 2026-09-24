package com.wellconverge.demo.vthreads;

import com.wellconverge.demo.Demo;

import java.time.Duration;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.stream.IntStream;

/**
 * The carrier-thread model.
 *
 * <p>A virtual thread is a Java object (a continuation + a small heap-allocated stack). To run, the
 * scheduler — a work-stealing {@code ForkJoinPool} with parallelism = #cores — <b>mounts</b> it on a
 * platform "carrier" thread. When it blocks on something the JDK knows how to park (sleep, locks from
 * j.u.c, socket I/O, {@code BlockingQueue.take}, ...) it <b>unmounts</b>: its frames are copied to the heap
 * and the carrier is free to run another virtual thread. When the blocking op completes it is re-queued
 * and may resume on a <i>different</i> carrier.
 *
 * <p>So virtual threads buy <b>concurrency for blocking work</b> (many waiting tasks on few OS threads), not
 * parallelism. Tune carriers with {@code -Djdk.virtualThreadScheduler.parallelism=N}.
 */
public final class CarrierThreadDemo {

    public static void main(String[] args) throws Exception {
        Demo.header("Virtual threads: the carrier-thread model");

        // 1. Blocking unmounts the virtual thread; it may come back on a different carrier.
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            for (int i = 0; i < 4; i++) {
                int task = i;
                executor.submit(() -> {
                    String before = Demo.carrierOf(Thread.currentThread());
                    Thread.sleep(50);
                    String after = Demo.carrierOf(Thread.currentThread());
                    System.out.printf("task %d: ran on %s, resumed after sleep on %s%n", task, before, after);
                    return null;
                });
            }
        }

        // 2. 100k tasks that each block for 1s complete in ~1s on only #cores carriers. With a platform
        //    thread per task you'd need 100k OS threads (each reserving ~1 MB of stack).
        int tasks = 100_000;
        Set<String> carriers = ConcurrentHashMap.newKeySet();
        long ms = Demo.timeMs(() -> {
            try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
                IntStream.range(0, tasks).forEach(i -> executor.submit(() -> {
                    Thread.sleep(Duration.ofSeconds(1));
                    carriers.add(Demo.carrierOf(Thread.currentThread()));
                    return i;
                }));
            } // close() waits for all tasks
        });
        System.out.printf("%n%,d tasks x 1s of blocking took %,d ms on %d distinct carriers (%d cores)%n",
                tasks, ms, carriers.size(), Demo.cores());
    }
}
