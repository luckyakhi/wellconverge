package com.wellconverge.demo.jmm;

import com.wellconverge.demo.Demo;

import java.time.Duration;

/**
 * {@code volatile} vs {@code final} field semantics.
 *
 * <p><b>final</b> (JLS 17.5): when a constructor finishes, its final fields are "frozen". Any thread that
 * obtains a reference to the object — <i>even through a data race</i> — sees the final fields (and
 * everything reachable through them as of the freeze, e.g. the contents of a {@code final int[]}) fully
 * initialised. Non-final fields get no such promise: a racy reader may see the default 0/null. Caveats:
 * the guarantee is void if {@code this} escapes the constructor (registering a listener, starting a thread),
 * and it covers construction only — later mutations of a reachable object need their own hb edge.
 * Records and immutable value objects get this for free (all fields final), which is why they're safe to
 * share without locks.
 *
 * <p><b>volatile</b>: visibility + ordering for every read/write of that field (acquire/release plus a
 * total order over volatile accesses). But {@code count++} is still read-modify-write — two steps — so
 * volatile does NOT make it atomic.
 */
public final class VolatileVsFinalDemo {

    static final class PlainPoint {
        int x;
        int y;

        PlainPoint(int v) {
            x = v;
            y = v;
        }
    }

    static final class FinalPoint {
        final int x;
        final int y;

        FinalPoint(int v) {
            x = v;
            y = v;
        }
    }

    // Deliberately NOT volatile: these are published by a data race.
    private static PlainPoint sharedPlain;
    private static FinalPoint sharedFinal;

    private static volatile boolean running;
    private static volatile int volatileCounter;

    public static void main(String[] args) throws Exception {
        Demo.header("JMM: volatile vs final field semantics");
        System.out.println("CPU architecture: " + System.getProperty("os.arch")
                + " (x86 is TSO and rarely shows the plain-field anomaly; ARM/Apple Silicon is weaker)");

        Duration duration = Duration.ofSeconds(2);
        System.out.printf("racy publication, %ds each:%n", duration.toSeconds());
        System.out.printf("  non-final fields: saw an uninitialised field %,d times (allowed by the JMM)%n", racePlain(duration));
        System.out.printf("  final fields    : saw an uninitialised field %,d times (guaranteed 0)%n", raceFinal(duration));
        System.out.println("  note: 0 for non-final fields on your machine proves nothing — it's still a bug");

        int threads = 4;
        int perThread = 1_000_000;
        Thread[] incrementers = new Thread[threads];
        for (int t = 0; t < threads; t++) {
            incrementers[t] = new Thread(() -> {
                for (int i = 0; i < perThread; i++) {
                    volatileCounter++; // read, add, write — another thread can interleave between them
                }
            });
            incrementers[t].start();
        }
        for (Thread t : incrementers) {
            t.join();
        }
        System.out.printf("%nvolatile is not atomic: %d threads x %,d volatileCounter++ = %,d (expected %,d)%n",
                threads, perThread, volatileCounter, threads * perThread);
        System.out.println("  -> use AtomicInteger / LongAdder / a lock for read-modify-write");
    }

    private static long racePlain(Duration duration) throws InterruptedException {
        long[] anomalies = new long[1];
        running = true;
        Thread writer = new Thread(() -> {
            int v = 1;
            while (running) {
                sharedPlain = new PlainPoint(v);
                v = v == Integer.MAX_VALUE ? 1 : v + 1;
            }
        });
        Thread reader = new Thread(() -> {
            while (running) { // volatile read each iteration stops the JIT hoisting the load below
                PlainPoint p = sharedPlain;
                if (p != null && (p.x == 0 || p.y == 0)) {
                    anomalies[0]++;
                }
            }
        });
        writer.start();
        reader.start();
        Thread.sleep(duration.toMillis());
        running = false;
        writer.join();
        reader.join(); // join() gives main a hb edge to anomalies[0]
        return anomalies[0];
    }

    private static long raceFinal(Duration duration) throws InterruptedException {
        long[] anomalies = new long[1];
        running = true;
        Thread writer = new Thread(() -> {
            int v = 1;
            while (running) {
                sharedFinal = new FinalPoint(v);
                v = v == Integer.MAX_VALUE ? 1 : v + 1;
            }
        });
        Thread reader = new Thread(() -> {
            while (running) {
                FinalPoint p = sharedFinal;
                if (p != null && (p.x == 0 || p.y == 0)) {
                    anomalies[0]++;
                }
            }
        });
        writer.start();
        reader.start();
        Thread.sleep(duration.toMillis());
        running = false;
        writer.join();
        reader.join();
        return anomalies[0];
    }
}
