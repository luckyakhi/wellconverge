package com.wellconverge.demo.lowlatency;

import com.wellconverge.demo.Demo;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * Off-heap storage: data the GC never scans, copies or pauses for.
 *
 * <p>GC cost scales with the number of live objects/references it must trace and move. Millions of small
 * long-lived objects (a cache, an order book, a big lookup table) make every marking cycle and full GC
 * expensive. Storing them as a flat array of fixed-size records in native memory makes them invisible to
 * the GC and cache-friendly (sequential, no pointer chasing, no object headers).
 *
 * <p>Options: the FFM API ({@link MemorySegment}/{@link Arena}; preview in 21, final in 22) — bounds-checked,
 * deterministic {@code close()}; {@code ByteBuffer.allocateDirect} — ≤ 2 GB, capped by
 * {@code -XX:MaxDirectMemorySize}, and freed only when the buffer object is GC'd; memory-mapped files for
 * persistence/IPC (Chronicle). Costs: manual lifetime management, (de)serialisation at the boundary, no
 * references inside, and heap dumps no longer show your data.
 */
public final class OffHeapDemo {

    private static final int COUNT = 5_000_000;

    /** On-heap: one object (16-byte header + fields, padded) per reading, plus a reference to it. */
    record Reading(long memberId, int steps, int heartRate) {
    }

    // Off-heap layout of one reading: [memberId: long][steps: int][heartRate: int] = 16 bytes, no header.
    private static final long MEMBER_ID = 0;
    private static final long STEPS = 8;
    private static final long HEART_RATE = 12;
    private static final long RECORD_SIZE = 16;

    public static void main(String[] args) throws Exception {
        Demo.header("Low latency: off-heap storage");

        long heapBefore = usedHeapAfterGc();
        Reading[] onHeap = new Reading[COUNT];
        for (int i = 0; i < COUNT; i++) {
            onHeap[i] = new Reading(i, i % 20_000, 60 + i % 100);
        }
        long heapUsed = usedHeapAfterGc() - heapBefore;
        long gcMs = Demo.timeMs(System::gc); // full GC must trace all 5M objects
        long stepsOnHeap = 0;
        for (Reading r : onHeap) {
            stepsOnHeap += r.steps();
        }
        System.out.printf("on-heap  : %,4d MB heap, full GC with them live took %,4d ms, total steps %,d%n",
                heapUsed >> 20, gcMs, stepsOnHeap);
        onHeap = null;
        Demo.sink(stepsOnHeap);

        try (Arena arena = Arena.ofConfined()) { // memory freed deterministically at close()
            heapBefore = usedHeapAfterGc();
            MemorySegment readings = arena.allocate(RECORD_SIZE * COUNT, 8);
            for (int i = 0; i < COUNT; i++) {
                long base = i * RECORD_SIZE;
                readings.set(ValueLayout.JAVA_LONG, base + MEMBER_ID, i);
                readings.set(ValueLayout.JAVA_INT, base + STEPS, i % 20_000);
                readings.set(ValueLayout.JAVA_INT, base + HEART_RATE, 60 + i % 100);
            }
            heapUsed = usedHeapAfterGc() - heapBefore;
            gcMs = Demo.timeMs(System::gc);
            long stepsOffHeap = 0;
            for (int i = 0; i < COUNT; i++) {
                stepsOffHeap += readings.get(ValueLayout.JAVA_INT, i * RECORD_SIZE + STEPS);
            }
            System.out.printf("off-heap : %,4d MB heap (+%,d MB native), full GC took %,4d ms, total steps %,d%n",
                    Math.max(0, heapUsed >> 20), readings.byteSize() >> 20, gcMs, stepsOffHeap);
        }

        // Same flyweight idea with a direct ByteBuffer (pre-FFM code, Netty, Aeron/Agrona).
        ByteBuffer direct = ByteBuffer.allocateDirect((int) RECORD_SIZE * 1024).order(ByteOrder.nativeOrder());
        direct.putLong(7 * (int) RECORD_SIZE, 42L).putInt(7 * (int) RECORD_SIZE + (int) STEPS, 9_000);
        System.out.printf("direct ByteBuffer: record 7 -> member %d, steps %d%n",
                direct.getLong(7 * (int) RECORD_SIZE), direct.getInt(7 * (int) RECORD_SIZE + (int) STEPS));
    }

    private static long usedHeapAfterGc() {
        System.gc();
        Runtime rt = Runtime.getRuntime();
        return rt.totalMemory() - rt.freeMemory();
    }
}
