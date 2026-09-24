// Standalone learning module: runnable JVM concurrency / JMM / GC / low-latency demos.
// Not a bounded context and not wired into :bootstrap. Plain JDK only.
//
//   gradle :demo:run --args="pinning"
//   gradle :demo:run --args="gc" -Pjvm="-Xmx1g -XX:+UseZGC -XX:+ZGenerational"
//
// --enable-preview is needed for StructuredTaskScope and the FFM API, both preview in JDK 21.

plugins {
    application
}

application {
    mainClass.set("com.wellconverge.demo.DemoRunner")
}

tasks.withType<JavaCompile>().configureEach {
    options.release.set(21)
    options.compilerArgs.add("--enable-preview")
}

tasks.named<JavaExec>("run") {
    jvmArgs("--enable-preview")
    (findProperty("jvm") as String?)
        ?.split(" ")
        ?.filter { it.isNotBlank() }
        ?.let { jvmArgs(it) }
}
