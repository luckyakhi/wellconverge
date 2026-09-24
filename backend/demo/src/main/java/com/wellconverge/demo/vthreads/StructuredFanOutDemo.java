package com.wellconverge.demo.vthreads;

import com.wellconverge.demo.Demo;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.StructuredTaskScope;
import java.util.concurrent.StructuredTaskScope.Subtask;

/**
 * Structured concurrency for fan-out (preview in JDK 21, JEP 453 — needs {@code --enable-preview}).
 *
 * <p>A {@link StructuredTaskScope} ties the lifetime of forked subtasks to a lexical block: subtasks can't
 * outlive the {@code try}, failures propagate to the parent, and shutting the scope down interrupts the
 * siblings. Compare with a bare {@code ExecutorService}: if one {@code Future} fails, the others keep
 * running (leaking threads and work) unless you remember to cancel them yourself.
 * Thread dumps ({@code jcmd <pid> Thread.dump_to_file -format=json}) show the scope tree.
 *
 * <ul>
 *   <li>{@code ShutdownOnFailure}: "need all results" — first failure cancels the rest.</li>
 *   <li>{@code ShutdownOnSuccess}: "need any result" — first success cancels the rest (hedged requests).</li>
 * </ul>
 */
public final class StructuredFanOutDemo {

    record MemberDashboard(String profile, String plan, int stepsToday) {
    }

    public static void main(String[] args) throws Exception {
        Demo.header("Structured concurrency: fan-out");

        long ms = Demo.timeMs(() -> System.out.println("  " + loadDashboard("m-1", false)));
        System.out.printf("happy path: 3 calls of 100/150/120 ms took %d ms (max, not sum)%n%n", ms);

        ms = Demo.timeMs(() -> {
            try {
                loadDashboard("m-2", true);
            } catch (ExecutionException e) {
                System.out.println("  failed: " + e.getCause().getMessage());
            }
        });
        System.out.printf("one failure after 30 ms: whole fan-out failed in %d ms, siblings interrupted%n%n", ms);

        ms = Demo.timeMs(() -> System.out.println("  winner: " + fastestReplica("m-3")));
        System.out.printf("hedged request: first of two replicas answered in %d ms%n", ms);
    }

    static MemberDashboard loadDashboard(String memberId, boolean planServiceDown) throws Exception {
        try (var scope = new StructuredTaskScope.ShutdownOnFailure()) {
            Subtask<String> profile = scope.fork(() -> call("profile-service", 100, memberId));
            Subtask<String> plan = scope.fork(() -> planServiceDown
                    ? fail("plan-service", 30)
                    : call("plan-service", 150, memberId));
            Subtask<Integer> steps = scope.fork(() -> {
                call("activity-service", 120, memberId);
                return 8_432;
            });

            scope.join()             // wait for all, or for the first failure
                 .throwIfFailed();   // rethrow it as ExecutionException in the parent

            return new MemberDashboard(profile.get(), plan.get(), steps.get());
        } // leaving the scope guarantees no subtask is still running
    }

    static String fastestReplica(String memberId) throws Exception {
        try (var scope = new StructuredTaskScope.ShutdownOnSuccess<String>()) {
            scope.fork(() -> call("replica-eu", 80, memberId));
            scope.fork(() -> call("replica-us", 40, memberId));
            scope.join();
            return scope.result();
        }
    }

    private static String call(String service, long latencyMs, String memberId) throws InterruptedException {
        try {
            Thread.sleep(latencyMs); // stands in for a blocking HTTP/DB call
            return service + "(" + memberId + ")";
        } catch (InterruptedException e) {
            System.out.println("  " + service + " interrupted by scope shutdown");
            throw e;
        }
    }

    private static String fail(String service, long afterMs) throws InterruptedException {
        Thread.sleep(afterMs);
        throw new IllegalStateException(service + " unavailable");
    }
}
