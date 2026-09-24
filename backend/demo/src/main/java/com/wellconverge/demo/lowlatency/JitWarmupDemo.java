package com.wellconverge.demo.lowlatency;

import com.wellconverge.demo.Demo;

import java.lang.management.ManagementFactory;

/**
 * JIT warm-up. A method starts in the interpreter, is compiled by C1 (fast compile, profiling) after ~hundreds
 * of invocations, then by C2 (slow compile, optimised using the profile) after ~10k+. Until then the first
 * requests after a deploy run 10-100x slower — the "cold start" latency tail.
 *
 * <p>C2's optimisations are <i>speculative</i>: here it sees only {@code PercentOff} at the {@code apply} call
 * site, so it inlines it behind a cheap type guard. When a {@code FlatOff} shows up, the guard fails
 * (uncommon trap) -> the method is <b>deoptimised</b> back to the interpreter and recompiled later — a latency
 * spike long after "warm-up".
 *
 * <p>Mitigations: drive representative traffic (all code paths / types) before the readiness probe passes;
 * CDS/AppCDS for startup; JDK 24+ AOT cache (JEP 483/515) and CRaC for pre-warmed images; watch
 * {@code -XX:+PrintCompilation} (look for "made not entrant") or JFR {@code jdk.Deoptimization}.
 */
public final class JitWarmupDemo {

    interface Discount {
        long apply(long cents);
    }

    record PercentOff(int percent) implements Discount {
        public long apply(long cents) {
            return cents - cents * percent / 100;
        }
    }

    record FlatOff(long off) implements Discount {
        public long apply(long cents) {
            return Math.max(0, cents - off);
        }
    }

    private static final int BATCH = 2_000;

    public static void main(String[] args) {
        Demo.header("Low latency: JIT warm-up and deoptimization");
        String[] payloads = new String[1024];
        for (int i = 0; i < payloads.length; i++) {
            payloads[i] = "m-" + i + "," + (i * 37 % 20_000) + "," + (999 + i);
        }
        Discount percent = new PercentOff(10);
        Discount flat = new FlatOff(250);

        long compileMsBefore = ManagementFactory.getCompilationMXBean().getTotalCompilationTime();
        for (int batch = 1; batch <= 60; batch++) {
            boolean newTypeAppears = batch > 45; // mid-flight traffic change: a second Discount type
            long start = System.nanoTime();
            for (int i = 0; i < BATCH; i++) {
                Discount d = newTypeAppears && (i & 1) == 0 ? flat : percent;
                Demo.sink(handleRequest(payloads[i & 1023], d));
            }
            long nsPerCall = (System.nanoTime() - start) / BATCH;
            if (batch <= 12 || batch % 5 == 0 || (batch >= 45 && batch <= 50)) {
                System.out.printf("  batch %2d: %,6d ns/request%s%n", batch, nsPerCall,
                        batch == 46 ? "   <- FlatOff appears: type guard fails, deopt" : "");
            }
        }
        System.out.printf("JIT compile time spent: %,d ms%n",
                ManagementFactory.getCompilationMXBean().getTotalCompilationTime() - compileMsBefore);
    }

    /** Parses "memberId,steps,priceCents" and prices it. */
    static long handleRequest(String payload, Discount discount) {
        int c1 = payload.indexOf(',');
        int c2 = payload.indexOf(',', c1 + 1);
        long steps = Long.parseLong(payload, c1 + 1, c2, 10);
        long priceCents = Long.parseLong(payload, c2 + 1, payload.length(), 10);
        long reward = steps / 1_000;
        return discount.apply(priceCents) - reward;
    }
}
