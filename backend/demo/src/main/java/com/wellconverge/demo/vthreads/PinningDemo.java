package com.wellconverge.demo.vthreads;

import com.wellconverge.demo.Demo;

import java.time.Duration;
import java.util.concurrent.Executors;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Pinning: when a virtual thread blocks but <i>cannot</i> unmount, it holds its carrier hostage.
 *
 * <p>In JDK 21 a virtual thread is pinned when it blocks
 * <ul>
 *   <li>inside a {@code synchronized} block/method — the monitor is recorded against the carrier's
 *       identity, so the stack can't move to another carrier (fixed in JDK 24 by JEP 491);</li>
 *   <li>while a native method or foreign function is on its stack (JNI, or a native -> Java upcall
 *       that then blocks). The JVM can't copy native frames to the heap, so this remains true after
 *       JEP 491 as well. Examples: some JDBC drivers, file I/O in native libs.</li>
 * </ul>
 * With enough pinned threads every carrier is stuck and throughput collapses to "#cores tasks at a time".
 * The fix in 21 is {@link ReentrantLock} (parks via {@code LockSupport}, which unmounts cleanly).
 *
 * <p>Find pinning with {@code -Djdk.tracePinnedThreads=short|full} or the JFR event
 * {@code jdk.VirtualThreadPinned}.
 */
public final class PinningDemo {

    private static final Duration HOLD = Duration.ofMillis(100);

    @FunctionalInterface
    private interface Task {
        void run(int index) throws Exception;
    }

    public static void main(String[] args) throws Exception {
        Demo.header("Virtual threads: pinning (synchronized vs ReentrantLock)");

        int carriers = Integer.getInteger("jdk.virtualThreadScheduler.parallelism", Demo.cores());
        int tasks = carriers * 8;

        // Every task gets its OWN lock, so there is zero lock contention: any slowdown is pure pinning.
        Object[] monitors = new Object[tasks];
        ReentrantLock[] locks = new ReentrantLock[tasks];
        for (int i = 0; i < tasks; i++) {
            monitors[i] = new Object();
            locks[i] = new ReentrantLock();
        }

        long pinnedMs = runAll(tasks, i -> {
            synchronized (monitors[i]) {
                Thread.sleep(HOLD);           // blocks while holding a monitor -> carrier is pinned
            }
        });
        long unpinnedMs = runAll(tasks, i -> {
            locks[i].lock();
            try {
                Thread.sleep(HOLD);           // virtual thread unmounts; carrier runs someone else
            } finally {
                locks[i].unlock();
            }
        });

        System.out.printf("%d tasks, each blocking %d ms, on %d carriers%n", tasks, HOLD.toMillis(), carriers);
        System.out.printf("  synchronized  : %,5d ms  (~ tasks/carriers x hold = %d ms: carriers pinned)%n",
                pinnedMs, (long) tasks / carriers * HOLD.toMillis());
        System.out.printf("  ReentrantLock : %,5d ms  (~ hold: all tasks block concurrently)%n", unpinnedMs);
    }

    private static long runAll(int tasks, Task task) throws Exception {
        return Demo.timeMs(() -> {
            try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
                for (int i = 0; i < tasks; i++) {
                    int index = i;
                    executor.submit(() -> {
                        task.run(index);
                        return null;
                    });
                }
            }
        });
    }
}
