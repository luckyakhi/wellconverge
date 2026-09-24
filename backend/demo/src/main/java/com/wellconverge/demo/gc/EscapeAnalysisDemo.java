package com.wellconverge.demo.gc;

import com.wellconverge.demo.Demo;

import java.lang.management.ManagementFactory;
import java.util.function.IntToDoubleFunction;

/**
 * Escape analysis: C2 proves an object never leaves the compiled method (after inlining) and replaces it with
 * its fields in registers ("scalar replacement") — the allocation disappears. It's why small immutable value
 * objects like records are often free on hot paths.
 *
 * <p>Limits: it only happens in C2-compiled code (so not during warm-up), everything the object is passed to
 * must be inlined (a call too big or too deep to inline = escape), storing it in a field/array/static or
 * returning it = escape, and merging different allocations at a control-flow join has historically defeated
 * it. Compare with {@code -XX:-DoEscapeAnalysis}.
 */
public final class EscapeAnalysisDemo {

    record Vec(double x, double y) {
        Vec plus(Vec other) {
            return new Vec(x + other.x, y + other.y);
        }

        double length() {
            return Math.sqrt(x * x + y * y);
        }
    }

    private static Vec lastSeen; // storing into a static = escape

    private static final com.sun.management.ThreadMXBean THREADS =
            (com.sun.management.ThreadMXBean) ManagementFactory.getThreadMXBean();

    public static void main(String[] args) {
        Demo.header("GC: escape analysis / scalar replacement");
        int n = 10_000_000;
        System.out.printf("interpreted/cold : %6.1f bytes per iteration (no C2 yet)%n", bytesPerIteration(EscapeAnalysisDemo::nonEscaping, 20_000));
        for (int i = 0; i < 200; i++) { // warm up so C2 compiles both methods
            Demo.sink((long) nonEscaping(20_000));
            Demo.sink((long) escaping(20_000));
        }
        System.out.printf("non-escaping     : %6.1f bytes per iteration%n", bytesPerIteration(EscapeAnalysisDemo::nonEscaping, n));
        System.out.printf("escaping         : %6.1f bytes per iteration (one Vec per iteration stored to a static)%n",
                bytesPerIteration(EscapeAnalysisDemo::escaping, n));
        System.out.println("each iteration creates 3 Vecs (~32 bytes each); run with -XX:-DoEscapeAnalysis to see them all");
    }

    /** Three temporaries per iteration, none of which outlive it. */
    static double nonEscaping(int n) {
        double acc = 0;
        for (int i = 0; i < n; i++) {
            Vec v = new Vec(i, i + 1).plus(new Vec(1, 1));
            acc += v.length();
        }
        return acc;
    }

    /** Same, but the result is published — that allocation must happen for real. */
    static double escaping(int n) {
        double acc = 0;
        for (int i = 0; i < n; i++) {
            Vec v = new Vec(i, i + 1).plus(new Vec(1, 1));
            lastSeen = v;
            acc += v.length();
        }
        return acc;
    }

    private static double bytesPerIteration(IntToDoubleFunction work, int n) {
        long before = THREADS.getCurrentThreadAllocatedBytes();
        Demo.sink((long) work.applyAsDouble(n));
        return (double) (THREADS.getCurrentThreadAllocatedBytes() - before) / n;
    }
}
