package com.wellconverge.demo.contention;

import com.wellconverge.demo.Demo;

import java.util.concurrent.atomic.LongAdder;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.concurrent.locks.StampedLock;

/**
 * StampedLock optimistic reads.
 *
 * <p>Even an uncontended {@code ReentrantReadWriteLock.readLock()} does a CAS on the lock's shared state, so
 * many readers on many cores still fight over that one cache line. {@link StampedLock#tryOptimisticRead()}
 * just reads a version stamp — <b>readers write nothing to shared memory</b>. You copy the fields into locals,
 * then {@link StampedLock#validate(long)}: if no writer got in, the copy is consistent; otherwise fall back to a
 * real read lock.
 *
 * <p>Rules for the optimistic section: only read into locals, never act on the values (no pointer chasing,
 * no loops bounded by them, nothing that can throw) until validate succeeds, because they may be torn.
 * StampedLock is not reentrant, has no conditions, and is not owner-aware — keep usage tiny and local.
 *
 * <p>The tracker keeps the invariant {@code x == y}; a torn read would break it.
 */
public final class StampedLockDemo {

    interface Tracker {
        void move();                 // writer: x++, y++ together

        boolean readIsConsistent();  // reader: returns whether the (x, y) it would return has x == y
    }

    static final class OptimisticTracker implements Tracker {
        private final StampedLock lock = new StampedLock();
        private long x;
        private long y;
        final LongAdder tornViewsCaught = new LongAdder();

        @Override
        public void move() {
            long stamp = lock.writeLock();
            try {
                x++;
                y++;
            } finally {
                lock.unlockWrite(stamp);
            }
        }

        @Override
        public boolean readIsConsistent() {
            long stamp = lock.tryOptimisticRead(); // no CAS, no shared write
            long cx = x;
            long cy = y;
            if (!lock.validate(stamp)) {           // a writer intervened: copies may be torn
                if (cx != cy) {
                    tornViewsCaught.increment();   // ...and here's proof that they can be
                }
                stamp = lock.readLock();
                try {
                    cx = x;
                    cy = y;
                } finally {
                    lock.unlockRead(stamp);
                }
            }
            return cx == cy;
        }
    }

    static final class ReadWriteTracker implements Tracker {
        private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
        private long x;
        private long y;

        @Override
        public void move() {
            lock.writeLock().lock();
            try {
                x++;
                y++;
            } finally {
                lock.writeLock().unlock();
            }
        }

        @Override
        public boolean readIsConsistent() {
            lock.readLock().lock();
            try {
                return x == y;
            } finally {
                lock.readLock().unlock();
            }
        }
    }

    private static volatile boolean running;

    public static void main(String[] args) throws Exception {
        Demo.header("Contention: StampedLock optimistic reads");
        int readers = Math.max(1, Demo.cores() - 1);
        for (int round = 1; round <= 2; round++) {
            OptimisticTracker optimistic = new OptimisticTracker();
            long optimisticReads = readHeavy(optimistic, readers);
            long rwReads = readHeavy(new ReadWriteTracker(), readers);
            System.out.printf("round %d, %d readers + 1 writer, 1s: StampedLock %,d reads | RW lock %,d reads (%.1fx)%n",
                    round, readers, optimisticReads, rwReads, (double) optimisticReads / Math.max(1, rwReads));
            System.out.printf("         torn (x != y) optimistic views caught by validate(): %,d — never returned%n",
                    optimistic.tornViewsCaught.sum());
        }
    }

    /** Returns total reads; fails loudly if any read returned an inconsistent view. */
    private static long readHeavy(Tracker tracker, int readers) throws InterruptedException {
        LongAdder reads = new LongAdder();
        LongAdder inconsistent = new LongAdder();
        running = true;
        Thread writer = new Thread(() -> {
            while (running) {
                tracker.move();
            }
        });
        Thread[] readerThreads = new Thread[readers];
        for (int r = 0; r < readers; r++) {
            readerThreads[r] = new Thread(() -> {
                long local = 0;
                while (running) {
                    if (!tracker.readIsConsistent()) {
                        inconsistent.increment();
                    }
                    local++;
                }
                reads.add(local);
            });
        }
        writer.start();
        for (Thread t : readerThreads) {
            t.start();
        }
        Thread.sleep(1000);
        running = false;
        writer.join();
        for (Thread t : readerThreads) {
            t.join();
        }
        if (inconsistent.sum() != 0) {
            throw new AssertionError(inconsistent.sum() + " inconsistent reads returned");
        }
        return reads.sum();
    }
}
