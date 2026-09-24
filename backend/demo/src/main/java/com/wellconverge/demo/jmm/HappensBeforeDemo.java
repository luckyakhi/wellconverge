package com.wellconverge.demo.jmm;

import com.wellconverge.demo.Demo;

/**
 * Happens-before: the only thing that guarantees one thread sees another thread's writes.
 *
 * <p>If write W happens-before read R, R sees W (or something later). Without such an edge the JIT and CPU
 * may reorder, cache in registers, or hoist reads out of loops — all legal. The edges you get for free:
 * <ul>
 *   <li>program order within one thread;</li>
 *   <li>monitor unlock -> every later lock of the same monitor (also {@code Lock.unlock} -> {@code lock});</li>
 *   <li>volatile write -> every later read of that variable that sees the write;</li>
 *   <li>{@code Thread.start()} -> first action of the started thread; last action -> {@code join()} returning;</li>
 *   <li>{@code executor.submit()} -> task runs; task completes -> {@code Future.get()} returns;
 *       put into a concurrent collection -> the get/take that sees it;</li>
 *   <li>transitivity: A hb B and B hb C => A hb C (this is what makes "publish via volatile flag" work).</li>
 * </ul>
 * Litmus-testing reorderings properly needs jcstress; this demo shows the two classic, reproducible effects.
 */
public final class HappensBeforeDemo {

    private static boolean plainStop;
    private static volatile boolean volatileStop;

    private static int payload;            // plain field ...
    private static volatile boolean ready; // ... published by this volatile flag

    public static void main(String[] args) throws Exception {
        Demo.header("JMM: happens-before");

        // 1. No happens-before: C2 may hoist the read of plainStop out of the loop -> spins forever.
        Thread plainSpinner = new Thread(() -> {
            long spins = 0;
            while (!plainStop) {
                spins++;
            }
            System.out.printf("  plain spinner noticed the flag after %,d spins%n", spins);
        });
        plainSpinner.setDaemon(true); // if it never stops, don't keep the JVM alive
        plainSpinner.start();
        Thread.sleep(1000);           // give C2 time to OSR-compile the loop
        plainStop = true;
        plainSpinner.join(1000);
        System.out.println(plainSpinner.isAlive()
                ? "plain boolean   : spinner is STILL running — the JIT hoisted the read (legal: no hb edge)"
                : "plain boolean   : spinner stopped this time — also legal; the JMM permits the stale read, it doesn't require it");

        // 2. Volatile write -> volatile read is a happens-before edge: the loop must re-read the flag.
        Thread volatileSpinner = new Thread(() -> {
            while (!volatileStop) {
                Thread.onSpinWait();
            }
        });
        volatileSpinner.start();
        Thread.sleep(1000);
        volatileStop = true;
        volatileSpinner.join(1000);
        System.out.println("volatile boolean: spinner " + (volatileSpinner.isAlive() ? "still running?!" : "stopped promptly"));

        // 3. Safe publication by transitivity: payload=42 (program order) hb ready=true (volatile) hb reader.
        Thread reader = new Thread(() -> {
            while (!ready) {
                Thread.onSpinWait();
            }
            System.out.println("publication     : reader saw ready=true, so it must see payload=" + payload);
        });
        reader.start();
        payload = 42;  // plain write ...
        ready = true;  // ... made visible by the volatile write that follows it
        reader.join();

        // 4. start()/join() edges: no volatile needed at all.
        int[] box = new int[1];
        Thread worker = new Thread(() -> box[0] = 7);
        worker.start();
        worker.join(); // everything the worker did happens-before join() returns
        System.out.println("start/join      : main sees box[0]=" + box[0]);
    }
}
