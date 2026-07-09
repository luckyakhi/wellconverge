rootProject.name = "wellconverge-backend"

dependencyResolutionManagement {
    repositories {
        mavenCentral()
    }
}

// --- Bounded context: Membership (hexagonal) ---
include(":membership:membership-domain")
include(":membership:membership-application")
include(":membership:membership-adapters")

// --- Deployable ---
include(":bootstrap")
