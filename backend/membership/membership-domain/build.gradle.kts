// Pure domain: JDK only. No Spring, no JPA, no web. This is deliberate (ADR-0003).
plugins {
    `java-library`
}

dependencies {
    testImplementation(platform("org.junit:junit-bom:5.11.0"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testImplementation("org.assertj:assertj-core:3.26.3")
}
