package com.wellconverge.demo.lowlatency;

import com.wellconverge.demo.Demo;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.util.concurrent.ArrayBlockingQueue;

/**
 * Ring buffer / LMAX Disruptor patterns, as a minimal single-producer single-consumer ring.
 *
 * <ul>
 *   <li><b>Pre-allocated, mutable events</b> reused forever: zero allocation in steady state -> no GC.</li>
 *   <li><b>Sequences instead of locks:</b> the producer owns one counter, the consumer another (single-writer
 *       principle — no CAS needed). Slot = {@code sequence & mask} (power-of-two size, no modulo).</li>
 *   <li><b>Release/acquire, not full fences:</b> {@code setRelease} ("lazySet") publishes the event's fields
 *       before the sequence; on x86 it's a plain store — vs a volatile store's {@code lock}-prefixed fence.</li>
 *   <li><b>Padded sequences</b> so producer and consumer counters never falsely share a cache line.</li>
 *   <li><b>Gating cache:</b> the producer re-reads the consumer's sequence only when it thinks the ring is full.</li>
 *   <li><b>Batching:</b> the consumer handles every event up to the latest published sequence per acquire,
 *       so it catches up faster the further behind it is.</li>
 *   <li><b>Wait strategy:</b> here busy-spin ({@code Thread.onSpinWait}) — lowest latency, burns a core.
 *       The Disruptor also offers yielding, sleeping and blocking strategies.</li>
 * </ul>
 * The real Disruptor adds multi-producer claiming (CAS on the cursor), consumer dependency graphs and
 * batch-publish; JCTools/Agrona provide production-grade SPSC/MPSC queues.
 */
public final class RingBufferDemo {

    private static final int EVENTS = 20_000_000;
    private static final int CAPACITY = 1 << 14;

    /** The pre-allocated event, mutated in place. */
    static final class StepEvent {
        long memberId;
        long steps;
    }

    static class LhsPadding {
        long p01, p02, p03, p04, p05, p06, p07;
    }

    static class SequenceValue extends LhsPadding {
        long value = -1;
    }

    static class RhsPadding extends SequenceValue {
        long p09, p10, p11, p12, p13, p14, p15;
    }

    /** A cache-line-isolated counter with release/acquire accessors. */
    static final class Sequence extends RhsPadding {
        private static final VarHandle VALUE;

        static {
            try {
                VALUE = MethodHandles.lookup().findVarHandle(SequenceValue.class, "value", long.class);
            } catch (ReflectiveOperationException e) {
                throw new ExceptionInInitializerError(e);
            }
        }

        long getAcquire() {
            return (long) VALUE.getAcquire(this);
        }

        void setRelease(long v) {
            VALUE.setRelease(this, v);
        }
    }

    static final class SpscRingBuffer {
        private final StepEvent[] slots;
        private final int mask;
        final Sequence published = new Sequence(); // highest sequence visible to the consumer
        final Sequence consumed = new Sequence();  // highest sequence the consumer has finished with
        private long nextSequence;                 // producer-only
        private long cachedConsumed = -1;          // producer-only gating cache

        SpscRingBuffer(int capacity) {
            if (Integer.bitCount(capacity) != 1) {
                throw new IllegalArgumentException("capacity must be a power of two");
            }
            slots = new StepEvent[capacity];
            for (int i = 0; i < capacity; i++) {
                slots[i] = new StepEvent();
            }
            mask = capacity - 1;
        }

        /** Producer: claim the next sequence, waiting while the ring is full. */
        long next() {
            long sequence = nextSequence++;
            long wrapPoint = sequence - slots.length; // the slot's previous occupant
            if (wrapPoint > cachedConsumed) {
                while (wrapPoint > (cachedConsumed = consumed.getAcquire())) {
                    Thread.onSpinWait();
                }
            }
            return sequence;
        }

        StepEvent get(long sequence) {
            return slots[(int) (sequence & mask)];
        }

        /** Producer: make everything written to the event visible, then advance the cursor. */
        void publish(long sequence) {
            published.setRelease(sequence);
        }
    }

    public static void main(String[] args) throws Exception {
        Demo.header("Low latency: ring buffer (Disruptor-style) vs ArrayBlockingQueue");
        long expected = 0;
        for (int i = 0; i < EVENTS; i++) {
            expected += i & 1023;
        }
        for (int round = 1; round <= 2; round++) {
            long[] ringSum = new long[1];
            long ringMs = Demo.timeMs(() -> ringSum[0] = runRingBuffer());
            long[] queueSum = new long[1];
            long queueMs = Demo.timeMs(() -> queueSum[0] = runBlockingQueue());
            System.out.printf("round %d: ring buffer %,5d ms (%,4.0f M events/s, checksum %s) | ArrayBlockingQueue %,5d ms (%,4.0f M events/s, checksum %s)%n",
                    round,
                    ringMs, EVENTS / 1e3 / Math.max(1, ringMs), ringSum[0] == expected ? "ok" : "BAD",
                    queueMs, EVENTS / 1e3 / Math.max(1, queueMs), queueSum[0] == expected ? "ok" : "BAD");
        }
    }

    private static long runRingBuffer() throws InterruptedException {
        SpscRingBuffer ring = new SpscRingBuffer(CAPACITY);
        long[] sum = new long[1];
        Thread consumer = new Thread(() -> {
            long next = 0;
            long total = 0;
            while (next < EVENTS) {
                long available = ring.published.getAcquire();
                if (available < next) {
                    Thread.onSpinWait();
                    continue;
                }
                for (long s = next; s <= available; s++) { // batch: one acquire, many events
                    total += ring.get(s).steps;
                }
                ring.consumed.setRelease(available);
                next = available + 1;
            }
            sum[0] = total;
        });
        consumer.start();
        for (int i = 0; i < EVENTS; i++) {
            long sequence = ring.next();
            StepEvent event = ring.get(sequence);
            event.memberId = i % 10_000;
            event.steps = i & 1023;
            ring.publish(sequence);
        }
        consumer.join();
        return sum[0];
    }

    private static long runBlockingQueue() throws InterruptedException {
        ArrayBlockingQueue<Long> queue = new ArrayBlockingQueue<>(CAPACITY);
        long[] sum = new long[1];
        Thread consumer = new Thread(() -> {
            long total = 0;
            try {
                for (int i = 0; i < EVENTS; i++) {
                    total += queue.take(); // lock + condition signalling + unboxing
                }
            } catch (InterruptedException e) {
                return;
            }
            sum[0] = total;
        });
        consumer.start();
        for (int i = 0; i < EVENTS; i++) {
            queue.put((long) (i & 1023)); // boxing (small values are cached here, arbitrary ones wouldn't be)
        }
        consumer.join();
        return sum[0];
    }
}
