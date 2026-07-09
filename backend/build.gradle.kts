// Root build: shared configuration for every module.
// The domain and application modules intentionally get NO Spring on their classpath.

subprojects {
    apply(plugin = "java")

    group = "com.wellconverge"
    version = "0.1.0"

    extensions.configure<JavaPluginExtension> {
        toolchain {
            languageVersion.set(JavaLanguageVersion.of(21))
        }
    }

    repositories {
        mavenCentral()
    }

    tasks.withType<Test> {
        useJUnitPlatform()
    }
}
