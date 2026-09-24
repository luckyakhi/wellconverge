package com.wellconverge.demo.jmm;

import com.wellconverge.demo.Demo;

/**
 * False sharing: two threads write two <i>different</i> variables that happen to live on the same 64-byte
 * cache line. Coherence works per line, not per variable, so every write invalidates the other core's copy
 * and the line ping-pongs between cores — logically independent, physically contended.
 *
 * <p>Fix: pad so hot, independently-written fields sit on different lines (128 bytes apart also defeats the
 * adjacent-line prefetcher). HotSpot may reorder fields within a class but lays superclass fields before
 * subclass fields, hence the inheritance trick below (the same one the Disruptor uses). The JDK itself uses
 * {@code @jdk.internal.vm.annotation.Contended} (LongAdder cells, ConcurrentHashMap counter cells,
 * ThreadLocalRandom seeds); application code can use it only with {@code -XX:-RestrictContended}.
 */
public final class FalseSharingDemo {

    private static final long ITERATIONS = 50_000_000L;

    /** a and b are adjacent: almost certainly on the same cache line. */
    static final class Adjacent {
        volatile long a;
        volatile long b;
    }

    static class HasA {
        volatile long a;
    }

    static class PaddingAfterA extends HasA {
        long p01, p02, p03, p04, p05, p06, p07, p08, p09, p10, p11, p12, p13, p14, p15, p16; // 128 bytes
    }

    /** a and b are at least 128 bytes apart. */
    static final class Padded extends PaddingAfterA {
        volatile long b;
    }

    public static void main(String[] args) throws Exception {
        Demo.header("JMM: false sharing");
        for (int round = 1; round <= 2; round++) {
            Adjacent adjacent = new Adjacent();
            long adjacentMs = twoWriters(
                    () -> { for (long i = 0; i < ITERATIONS; i++) adjacent.a++; },
                    () -> { for (long i = 0; i < ITERATIONS; i++) adjacent.b++; });

            Padded padded = new Padded();
            long paddedMs = twoWriters(
                    () -> { for (long i = 0; i < ITERATIONS; i++) padded.a++; },
                    () -> { for (long i = 0; i < ITERATIONS; i++) padded.b++; });

            System.out.printf("round %d: same cache line %,5d ms | padded %,5d ms  (%.1fx)%n",
                    round, adjacentMs, paddedMs, (double) adjacentMs / Math.max(1, paddedMs));
        }
        System.out.println("(each thread only touches its own field — there is no data race, just shared hardware)");
    }

    private static long twoWriters(Runnable writerA, Runnable writerB) throws Exception {
        return Demo.timeMs(() -> {
            Thread a = new Thread(writerA);
            Thread b = new Thread(writerB);
            a.start();
            b.start();
            a.join();
            b.join();
        });
    }
}
