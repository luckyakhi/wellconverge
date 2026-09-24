package com.wellconverge.demo.gc;

import com.wellconverge.demo.Demo;

import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.util.HashMap;
import java.util.Map;

/**
 * Allocation rate is the real driver of GC pause pressure.
 *
 * <p>Young GC <b>frequency</b> ~= allocation rate / eden size. Each young pause's <b>cost</b> ~= the objects
 * still alive (they get copied), not the garbage. So a high allocation rate means more pauses, and more
 * objects caught "in flight" and prematurely promoted into the old generation — which later forces
 * concurrent marking, mixed collections, or (worst case) a full GC. On ZGC the same pressure surfaces as
 * allocation stalls. Tuning GC flags treats the symptom; cutting MB/s treats the cause.
 *
 * <p>Measure it: JFR ({@code jdk.ObjectAllocationSample}), async-profiler {@code -e alloc}, {@code -Xlog:gc},
 * or per-thread counters as here. Typical fixes: avoid boxing and per-call string building on hot paths,
 * use primitive arrays / primitive collections, reuse buffers, size collections up front.
 */
public final class AllocationRateDemo {

    private static final int EVENTS = 20_000_000;
    private static final int MEMBERS = 10_000;

    private static final com.sun.management.ThreadMXBean THREADS =
            (com.sun.management.ThreadMXBean) ManagementFactory.getThreadMXBean();

    public static void main(String[] args) throws Exception {
        Demo.header("GC: allocation rate drives pause pressure");
        System.out.println("Counting step events per member, two ways (second run of each is after warm-up):");
        for (int round = 1; round <= 2; round++) {
            measure("boxed  (HashMap<String,Long>, \"member-\"+id)", AllocationRateDemo::countBoxed);
            measure("lean   (long[] indexed by id)             ", AllocationRateDemo::countLean);
        }
    }

    private static long countBoxed() {
        Map<String, Long> counts = new HashMap<>();
        for (int i = 0; i < EVENTS; i++) {
            counts.merge("member-" + (i % MEMBERS), 1L, Long::sum); // new String + new Long per event
        }
        return counts.size();
    }

    private static long countLean() {
        long[] counts = new long[MEMBERS];
        for (int i = 0; i < EVENTS; i++) {
            counts[i % MEMBERS]++;
        }
        return counts.length;
    }

    private static void measure(String label, java.util.function.LongSupplier work) {
        long gcsBefore = totalGcCount();
        long bytesBefore = THREADS.getCurrentThreadAllocatedBytes();
        long start = System.nanoTime();
        Demo.sink(work.getAsLong());
        long nanos = System.nanoTime() - start;
        long bytes = THREADS.getCurrentThreadAllocatedBytes() - bytesBefore;
        System.out.printf("  %s %,6d ms  %,8d MB allocated  %,6.0f MB/s  %3d GCs%n",
                label, nanos / 1_000_000, bytes >> 20, (bytes >> 20) / (nanos / 1e9), totalGcCount() - gcsBefore);
    }

    private static long totalGcCount() {
        long total = 0;
        for (GarbageCollectorMXBean gc : ManagementFactory.getGarbageCollectorMXBeans()) {
            total += Math.max(0, gc.getCollectionCount());
        }
        return total;
    }
}
