package com.wellconverge.demo.contention;

import com.wellconverge.demo.Demo;

import java.math.BigInteger;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * The {@code ConcurrentHashMap.computeIfAbsent} reentrancy trap.
 *
 * <p>CHM runs the mapping function <b>while holding the lock on the key's bin</b> (a reservation node for an
 * empty bin, the first node's monitor otherwise). So the function must be short and must not touch the same
 * map. If it does — classic example: a memoised recursive function — you get, depending on bins and timing:
 * {@code IllegalStateException("Recursive update")} (JDK 9+ detects some cases), a corrupted/looping map on
 * JDK 8, or a deadlock when two threads recurse into each other's bins. {@code HashMap.computeIfAbsent} throws
 * {@code ConcurrentModificationException} for the same code.
 *
 * <p>Holding the bin lock also means a slow loader blocks every other writer whose key hashes to that bin
 * (and resizes), not just callers of the same key.
 *
 * <p>Fixes: (1) compute outside the map, then {@code putIfAbsent} (may compute twice — fine for pure
 * functions); (2) cache a {@link CompletableFuture}: the map op only installs a placeholder, the real work
 * runs outside any lock, and concurrent callers for the same key share one load.
 */
public final class ComputeIfAbsentTrapDemo {

    private static final Map<Integer, BigInteger> TRAP_MEMO = new ConcurrentHashMap<>();
    private static final Map<Integer, BigInteger> SAFE_MEMO = new ConcurrentHashMap<>();

    public static void main(String[] args) throws Exception {
        Demo.header("ConcurrentHashMap.computeIfAbsent reentrancy trap");

        Thread trap = new Thread(() -> {
            try {
                System.out.println("  trap fib(60) = " + fibTrap(60));
            } catch (IllegalStateException e) {
                System.out.println("  trap fib(60) threw IllegalStateException: " + e.getMessage());
            }
        });
        trap.setDaemon(true);
        trap.start();
        trap.join(3000);
        if (trap.isAlive()) {
            System.out.println("  trap fib(60) is hung — spinning on a reserved bin it holds itself");
        }

        System.out.println("  safe fib(60) = " + fibSafe(60) + "  (compute outside, then putIfAbsent)");

        // Future-based memoisation: 16 concurrent callers, exactly one load.
        AtomicInteger loads = new AtomicInteger();
        FutureCache cache = new FutureCache(key -> {
            loads.incrementAndGet();
            Thread.sleep(200); // slow I/O — runs outside any CHM lock
            return "profile-of-" + key;
        });
        CountDownLatch go = new CountDownLatch(1);
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            for (int i = 0; i < 16; i++) {
                executor.submit(() -> {
                    go.await();
                    return cache.get("m-42").join();
                });
            }
            go.countDown();
        }
        System.out.printf("  future cache: 16 concurrent gets for one key -> loader ran %d time(s)%n", loads.get());
    }

    /** DON'T: the mapping function re-enters the same map. */
    static BigInteger fibTrap(int n) {
        if (n < 2) {
            return BigInteger.valueOf(n);
        }
        return TRAP_MEMO.computeIfAbsent(n, k -> fibTrap(k - 1).add(fibTrap(k - 2)));
    }

    /** DO: recurse outside the map; putIfAbsent keeps the first published value. */
    static BigInteger fibSafe(int n) {
        if (n < 2) {
            return BigInteger.valueOf(n);
        }
        BigInteger cached = SAFE_MEMO.get(n);
        if (cached != null) {
            return cached;
        }
        BigInteger value = fibSafe(n - 1).add(fibSafe(n - 2));
        BigInteger raced = SAFE_MEMO.putIfAbsent(n, value);
        return raced != null ? raced : value;
    }

    @FunctionalInterface
    interface Loader {
        String load(String key) throws Exception;
    }

    static final class FutureCache {
        private final ConcurrentHashMap<String, CompletableFuture<String>> entries = new ConcurrentHashMap<>();
        private final Loader loader;

        FutureCache(Loader loader) {
            this.loader = loader;
        }

        CompletableFuture<String> get(String key) {
            CompletableFuture<String> fresh = new CompletableFuture<>();
            CompletableFuture<String> existing = entries.putIfAbsent(key, fresh); // O(1) under the bin lock
            if (existing != null) {
                return existing;
            }
            try {
                fresh.complete(loader.load(key)); // the slow part, lock-free, may even re-enter the cache
            } catch (Exception e) {
                entries.remove(key, fresh);       // don't cache failures
                fresh.completeExceptionally(e);
            }
            return fresh;
        }
    }
}
