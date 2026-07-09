// Application layer: use cases, ports (in/out), commands. Framework-free (ADR-0003).
// Owns the executable BDD acceptance specs, driven through the ports with in-memory adapters.
plugins {
    `java-library`
}

dependencies {
    api(project(":membership:membership-domain"))

    testImplementation(platform("org.junit:junit-bom:5.11.0"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testImplementation("org.junit.platform:junit-platform-suite")
    testImplementation("org.assertj:assertj-core:3.26.3")
    testImplementation("io.cucumber:cucumber-java:7.18.1")
    testImplementation("io.cucumber:cucumber-junit-platform-engine:7.18.1")
}

// Cucumber discovers features via the JUnit Platform suite (see RunCucumberTest).
tasks.test {
    systemProperty("cucumber.junit-platform.naming-strategy", "long")
}
