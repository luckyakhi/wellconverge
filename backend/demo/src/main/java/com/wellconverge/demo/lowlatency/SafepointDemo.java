package com.wellconverge.demo.lowlatency;

import com.wellconverge.demo.Demo;
import com.wellconverge.demo.gc.HiccupMeter;

/**
 * Safepoint pauses and time-to-safepoint (TTSP).
 *
 * <p>Some VM operations need every Java thread stopped at a <i>safepoint</i> — a point where the JIT knows
 * the exact state of the stack and heap: most GC phases (even ZGC's tiny ones), deoptimization of shared
 * code, class redefinition, full thread dumps, heap dumps. The VM raises a flag and each thread stops at its
 * next <i>safepoint poll</i> (method returns, loop back-edges). Everyone waits for the slowest thread.
 *
 * <p>Classic trap: C2 used to omit polls from "counted" int loops, so one thread in a long counted loop could
 * hold the whole JVM hostage for seconds. Since JDK 10, <i>loop strip mining</i>
 * ({@code UseCountedLoopSafepoints} + {@code LoopStripMiningIter=1000}) adds a poll every N iterations.
 * Compare:
 * <pre>
 *   gradle :demo:run --args="safepoint" -Pjvm="-Xlog:safepoint"
 *   gradle :demo:run --args="safepoint" -Pjvm="-Xlog:safepoint -XX:-UseCountedLoopSafepoints"
 * </pre>
 * The {@code Reaching safepoint: N ns} value in the log is TTSP. Other things to know: many operations now
 * use per-thread handshakes instead of global safepoints; JNI/native code counts as "already safe"; and
 * sampling profilers that only sample at safepoints are biased — prefer async-profiler / JFR.
 */
public final class SafepointDemo {

    private static volatile boolean running = true;

    public static void main(String[] args) throws Exception {
        Demo.header("Low latency: safepoints and time-to-safepoint");
        report("baseline         ");

        Thread spinner = new Thread(() -> {
            long acc = 0;
            while (running) {
                acc += countedLoop(Integer.MAX_VALUE); // ~1-2 s per call
            }
            Demo.sink(acc);
        }, "counted-loop-spinner");
        spinner.setDaemon(true);
        spinner.start();
        Thread.sleep(1000); // let C2 compile countedLoop

        report("with counted loop");
        System.out.println("(with -XX:-UseCountedLoopSafepoints expect ~seconds: every thread waited for the loop to finish)");
        running = false;
    }

    /**
     * Over a 4 s window: time a global safepoint op every 400 ms, and let a hiccup meter record how long
     * an unrelated thread was frozen — both wait for the slowest thread to reach its safepoint poll.
     */
    private static void report(String label) throws Exception {
        long worstOpNanos = 0;
        try (HiccupMeter meter = HiccupMeter.start()) {
            for (int i = 0; i < 10; i++) {
                Thread.sleep(400);
                long start = System.nanoTime();
                Demo.sink(Thread.getAllStackTraces().size()); // VM_ThreadDump: needs a global safepoint
                worstOpNanos = Math.max(worstOpNanos, System.nanoTime() - start);
            }
            System.out.printf("%s: worst thread-dump op %,8.1f ms | worst stall seen by another thread %,8.1f ms%n",
                    label, worstOpNanos / 1e6, meter.maxHiccupMs());
        }
    }

    /** An int-indexed loop with a known trip count — a "counted loop" in C2 terms. */
    static long countedLoop(int n) {
        long acc = 0;
        for (int i = 0; i < n; i++) {
            acc += (i ^ acc) * 31;
        }
        return acc;
    }
}
