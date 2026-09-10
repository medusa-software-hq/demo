plugins {
  // Allow automatic download of JDKs
  id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

rootProject.name = "demo"

include(
    ":core",
    ":api",
    ":api:client:raw",
    ":api:client",
    ":api:server",
    ":backend",
    ":backend:service",
    ":backend:local-stack",
    // How the Service is started on Cloud Run.
    ":backend:service:cloud-run",
)

// `entries/` groups a module's deployables on disk, where it reads well. It carries no meaning in a
// dependency coordinate, so it is kept out of the Gradle path.
project(":backend:service:cloud-run").projectDir = file("backend/service/entries/cloud-run")
