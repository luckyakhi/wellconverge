package com.wellconverge.demo;

/** Tiny helpers shared by the demos. Deliberately not a benchmark harness — use JMH for real numbers. */
public final class Demo {

    private static volatile long SINK;

    private Demo() {
    }

    @FunctionalInterface
    public interface Body {
        void run() throws Exception;
    }

    public static void header(String title) {
        System.out.println();
        System.out.println("=== " + title + " ===");
    }

    public static long timeMs(Body body) throws Exception {
        long start = System.nanoTime();
        body.run();
        return (System.nanoTime() - start) / 1_000_000;
    }

    public static int cores() {
        return Runtime.getRuntime().availableProcessors();
    }

    /** Keeps a result "used" so the JIT can't dead-code-eliminate the work. Racy by design. */
    public static void sink(long value) {
        SINK ^= value;
    }

    /**
     * {@code VirtualThread#toString} looks like {@code VirtualThread[#22]/runnable@ForkJoinPool-1-worker-3};
     * the part after '@' is the carrier (platform) thread it is currently mounted on.
     */
    public static String carrierOf(Thread thread) {
        String s = thread.toString();
        int at = s.indexOf('@');
        return at < 0 ? "(not mounted)" : s.substring(at + 1);
    }
}
