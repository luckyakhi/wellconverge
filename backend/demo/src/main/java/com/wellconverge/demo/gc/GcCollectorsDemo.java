package com.wellconverge.demo.gc;

import com.sun.management.HotSpotDiagnosticMXBean;
import com.wellconverge.demo.Demo;

import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.time.Duration;
import java.util.concurrent.ThreadLocalRandom;

/**
 * G1 vs generational ZGC on JDK 21. Run the same workload twice and compare:
 * <pre>
 *   gradle :demo:run --args="gc 512 15" -Pjvm="-Xmx2g -XX:+UseG1GC -Xlog:gc"
 *   gradle :demo:run --args="gc 512 15" -Pjvm="-Xmx2g -XX:+UseZGC -XX:+ZGenerational -Xlog:gc"
 * </pre>
 *
 * <p><b>G1</b> (default): regionised, generational. Young collections are stop-the-world evacuations whose
 * cost scales with the <i>surviving</i> objects; old regions are reclaimed in mixed collections after a
 * concurrent mark. Tuned by a pause <i>goal</i> ({@code -XX:MaxGCPauseMillis=200}), not a guarantee. Best
 * throughput / footprint for most services.
 *
 * <p><b>ZGC</b>: marking, relocation and reference processing all happen concurrently using coloured
 * pointers + load barriers; STW pauses are sub-millisecond and independent of heap/live-set size. In JDK 21
 * it is non-generational by default — {@code -XX:+ZGenerational} (JEP 439) adds a young generation so it no
 * longer has to mark the whole heap to reclaim short-lived garbage (generational became the default in 23 and
 * the only mode in 24). Costs: barrier overhead on every reference load (a few % throughput), more headroom,
 * and if the app allocates faster than ZGC can collect, threads hit <i>allocation stalls</i> — which the
 * hiccup meter will show even though "pauses" stay tiny.
 */
public final class GcCollectorsDemo {

    public static void main(String[] args) throws Exception {
        Demo.header("GC: G1 vs generational ZGC");
        int liveMb = args.length > 0 ? Integer.parseInt(args[0]) : 256;
        int seconds = args.length > 1 ? Integer.parseInt(args[1]) : 10;

        printCollectorConfig();
        System.out.printf("workload: %d MB live set, churning for %d s, max heap %d MB%n",
                liveMb, seconds, Runtime.getRuntime().maxMemory() >> 20);

        long allocatedBytes;
        try (HiccupMeter meter = HiccupMeter.start()) {
            allocatedBytes = churn(liveMb, Duration.ofSeconds(seconds));
            System.out.printf("allocation rate: %,d MB/s%n", (allocatedBytes >> 20) / seconds);
            System.out.printf("application-observed stalls: max %.1f ms, %d stalls > 10 ms%n",
                    meter.maxHiccupMs(), meter.hiccupsOver10ms());
        }

        for (GarbageCollectorMXBean gc : ManagementFactory.getGarbageCollectorMXBeans()) {
            // Names: "G1 Young Generation"/"G1 Old Generation"/"G1 Concurrent GC", or
            // "ZGC Minor Cycles"/"ZGC Minor Pauses"/"ZGC Major Cycles"/"ZGC Major Pauses".
            System.out.printf("  %-22s count=%,6d  time=%,7d ms%n", gc.getName(), gc.getCollectionCount(), gc.getCollectionTime());
        }
        System.out.println("  (for ZGC, *Cycles* time is mostly concurrent; only *Pauses* stop the application)");
    }

    /**
     * Keeps {@code liveMb} of 1 KiB arrays alive (they end up in the old generation), replaces some of them
     * continuously (old-gen garbage that needs marking to reclaim) and allocates lots of short-lived garbage.
     */
    private static long churn(int liveMb, Duration duration) {
        byte[][] live = new byte[liveMb * 1024][];
        for (int i = 0; i < live.length; i++) {
            live[i] = new byte[1024];
        }
        byte[][] recent = new byte[64][]; // makes the short-lived arrays escape so they're really allocated
        ThreadLocalRandom random = ThreadLocalRandom.current();
        long allocated = (long) liveMb << 20;
        long deadline = System.nanoTime() + duration.toNanos();
        while (System.nanoTime() < deadline) {
            for (int k = 0; k < 1_000; k++) {
                byte[] garbage = new byte[64 + random.nextInt(512)]; // dies young: cheap for both collectors
                recent[k & 63] = garbage;
                allocated += garbage.length;
            }
            live[random.nextInt(live.length)] = new byte[1024];     // mid-lived: promoted, then dies
            allocated += 1024;
        }
        Demo.sink(live.length + recent.length);
        return allocated;
    }

    private static void printCollectorConfig() {
        HotSpotDiagnosticMXBean hotspot = ManagementFactory.getPlatformMXBean(HotSpotDiagnosticMXBean.class);
        StringBuilder flags = new StringBuilder("collector flags:");
        for (String flag : new String[] {"UseG1GC", "UseZGC", "ZGenerational", "UseParallelGC", "UseSerialGC", "MaxGCPauseMillis"}) {
            try {
                flags.append(' ').append(flag).append('=').append(hotspot.getVMOption(flag).getValue());
            } catch (IllegalArgumentException notOnThisJvm) {
                // flag doesn't exist on this JVM build
            }
        }
        System.out.println(flags);
    }
}
