# demo — JVM concurrency, JMM, GC and low-latency demos

A learning sandbox, separate from the product. It is not a bounded context, `:bootstrap` doesn't depend on
it, and it uses only the JDK. Each class is a runnable demo, and its Javadoc explains the concept behind it.
The timings are only rough; use JMH if you need real benchmark numbers.

```bash
cd backend
gradle :demo:run                                   # list demos
gradle :demo:run --args="pinning"
gradle :demo:run --args="gc 512 15" -Pjvm="-Xmx2g -XX:+UseZGC -XX:+ZGenerational -Xlog:gc"
```

`--enable-preview` is always on, because `StructuredTaskScope` and the FFM API are preview features in JDK 21.

| Package       | Demo (`--args`)     | Try with (`-Pjvm=...`)                        |
|---------------|---------------------|-----------------------------------------------|
| `vthreads`    | `carrier`           | `-Djdk.virtualThreadScheduler.parallelism=2`  |
|               | `pinning`           | `-Djdk.tracePinnedThreads=short`              |
|               | `cpu`               |                                               |
|               | `structured`        |                                               |
| `jmm`         | `happens-before`    |                                               |
|               | `volatile-final`    |                                               |
|               | `false-sharing`     |                                               |
| `contention`  | `long-adder`        |                                               |
|               | `stamped-lock`      |                                               |
|               | `compute-if-absent` |                                               |
| `gc`          | `gc`                | `-XX:+UseG1GC` vs `-XX:+UseZGC -XX:+ZGenerational` |
|               | `allocation-rate`   | `-Xlog:gc`                                    |
|               | `escape-analysis`   | `-XX:-DoEscapeAnalysis`                       |
| `lowlatency`  | `jit-warmup`        | `-XX:+PrintCompilation`                       |
|               | `safepoint`         | `-XX:-UseCountedLoopSafepoints -Xlog:safepoint` |
|               | `off-heap`          |                                               |
|               | `ring-buffer`       |                                               |
