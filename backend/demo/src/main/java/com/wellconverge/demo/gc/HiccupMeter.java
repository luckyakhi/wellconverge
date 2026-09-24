package com.wellconverge.demo.gc;

/**
 * A poor man's jHiccup: a thread that sleeps 1 ms in a loop and records how late it wakes up. Stop-the-world
 * pauses (GC, safepoints) show up as big overshoots — this measures what the <i>application</i> experienced,
 * which is the number that matters, rather than what the GC log claims.
 */
public final class HiccupMeter implements AutoCloseable {

    private final Thread thread;
    private volatile boolean running = true;
    private volatile long maxHiccupNanos;
    private volatile long hiccupsOver10ms;

    private HiccupMeter() {
        thread = new Thread(this::measure, "hiccup-meter");
        thread.setDaemon(true);
    }

    public static HiccupMeter start() {
        HiccupMeter meter = new HiccupMeter();
        meter.thread.start();
        return meter;
    }

    private void measure() {
        long max = 0;
        long over10 = 0;
        while (running) {
            long before = System.nanoTime();
            try {
                Thread.sleep(1);
            } catch (InterruptedException e) {
                return;
            }
            long hiccup = System.nanoTime() - before - 1_000_000;
            if (hiccup > max) {
                max = hiccup;
                maxHiccupNanos = max;
            }
            if (hiccup > 10_000_000) {
                hiccupsOver10ms = ++over10;
            }
        }
    }

    public double maxHiccupMs() {
        return maxHiccupNanos / 1e6;
    }

    public long hiccupsOver10ms() {
        return hiccupsOver10ms;
    }

    @Override
    public void close() throws InterruptedException {
        running = false;
        thread.join();
    }
}
