package com.wellconverge.demo;

import com.wellconverge.demo.contention.ComputeIfAbsentTrapDemo;
import com.wellconverge.demo.contention.LongAdderVsAtomicLongDemo;
import com.wellconverge.demo.contention.StampedLockDemo;
import com.wellconverge.demo.gc.AllocationRateDemo;
import com.wellconverge.demo.gc.EscapeAnalysisDemo;
import com.wellconverge.demo.gc.GcCollectorsDemo;
import com.wellconverge.demo.jmm.FalseSharingDemo;
import com.wellconverge.demo.jmm.HappensBeforeDemo;
import com.wellconverge.demo.jmm.VolatileVsFinalDemo;
import com.wellconverge.demo.lowlatency.JitWarmupDemo;
import com.wellconverge.demo.lowlatency.OffHeapDemo;
import com.wellconverge.demo.lowlatency.RingBufferDemo;
import com.wellconverge.demo.lowlatency.SafepointDemo;
import com.wellconverge.demo.vthreads.CarrierThreadDemo;
import com.wellconverge.demo.vthreads.CpuBoundDemo;
import com.wellconverge.demo.vthreads.PinningDemo;
import com.wellconverge.demo.vthreads.StructuredFanOutDemo;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Entry point: {@code gradle :demo:run --args="<demo> [args...]"}; extra JVM flags via {@code -Pjvm="..."}.
 * Each demo is independent — run one per JVM, since several of them depend on JVM flags or leave threads behind.
 */
public final class DemoRunner {

    private interface Main {
        void run(String[] args) throws Exception;
    }

    private record Entry(Main main, String about) {
    }

    private static final Map<String, Entry> DEMOS = new LinkedHashMap<>();

    static {
        // Virtual threads
        DEMOS.put("carrier", new Entry(CarrierThreadDemo::main, "virtual threads mount/unmount on a few carrier threads"));
        DEMOS.put("pinning", new Entry(PinningDemo::main, "synchronized pins the carrier; ReentrantLock doesn't   [-Djdk.tracePinnedThreads=short]"));
        DEMOS.put("cpu", new Entry(CpuBoundDemo::main, "no speedup for CPU-bound work, and no time-slicing"));
        DEMOS.put("structured", new Entry(StructuredFanOutDemo::main, "StructuredTaskScope fan-out, fail-fast and hedging"));
        // Java Memory Model
        DEMOS.put("happens-before", new Entry(HappensBeforeDemo::main, "visibility needs a happens-before edge"));
        DEMOS.put("volatile-final", new Entry(VolatileVsFinalDemo::main, "final-field freeze vs volatile; volatile is not atomic"));
        DEMOS.put("false-sharing", new Entry(FalseSharingDemo::main, "two independent counters on one cache line"));
        // Contention
        DEMOS.put("long-adder", new Entry(LongAdderVsAtomicLongDemo::main, "striped LongAdder vs single-CAS AtomicLong"));
        DEMOS.put("stamped-lock", new Entry(StampedLockDemo::main, "optimistic reads vs ReentrantReadWriteLock"));
        DEMOS.put("compute-if-absent", new Entry(ComputeIfAbsentTrapDemo::main, "recursive computeIfAbsent and the safe alternatives"));
        // GC
        DEMOS.put("gc", new Entry(GcCollectorsDemo::main, "same workload under G1 vs generational ZGC   [args: liveMb seconds]"));
        DEMOS.put("allocation-rate", new Entry(AllocationRateDemo::main, "allocation rate drives GC frequency"));
        DEMOS.put("escape-analysis", new Entry(EscapeAnalysisDemo::main, "scalar replacement makes allocations vanish   [-XX:-DoEscapeAnalysis]"));
        // Low latency
        DEMOS.put("jit-warmup", new Entry(JitWarmupDemo::main, "interpreter -> C1 -> C2, and a deoptimization spike"));
        DEMOS.put("safepoint", new Entry(SafepointDemo::main, "time-to-safepoint and counted loops   [-XX:-UseCountedLoopSafepoints -Xlog:safepoint]"));
        DEMOS.put("off-heap", new Entry(OffHeapDemo::main, "flat off-heap records the GC never traces"));
        DEMOS.put("ring-buffer", new Entry(RingBufferDemo::main, "Disruptor-style SPSC ring buffer vs ArrayBlockingQueue"));
    }

    public static void main(String[] args) throws Exception {
        if (args.length == 0 || !DEMOS.containsKey(args[0])) {
            System.out.println("usage: gradle :demo:run --args=\"<demo> [args]\" [-Pjvm=\"<jvm flags>\"]");
            DEMOS.forEach((name, entry) -> System.out.printf("  %-18s %s%n", name, entry.about()));
            return;
        }
        DEMOS.get(args[0]).main().run(Arrays.copyOfRange(args, 1, args.length));
    }
}
