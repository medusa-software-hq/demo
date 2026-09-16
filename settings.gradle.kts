plugins {
  // Allow automatic download of JDKs
  id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

rootProject.name = "demo"

include(
    ":core",
    ":api",
    ":api:server",
    // `backend/` in three parts: `system/` is what a deployed environment runs, `tools/` is what is
    // run against one deliberately, and `local-stack/` brings the system up on this machine.
    //
    // What the Service and a worker agree on about work: the workflow, and whether there is any.
    // The tests here are the system's own — they start a stack and call it over HTTP, so they
    // belong to none of its parts.
    ":backend:system",
    // Where the data lives: the store, the schema, the migrations. Shared by whatever reads or writes it.
    ":backend:system:storage",
    ":backend:system:service",
    // Does the work the Service starts.
    ":backend:system:worker",
    // How the Service is started on Cloud Run.
    ":backend:system:service:cloud-run",
    // Brings the system up locally, with a database and a Temporal of its own.
    ":backend:local-stack",
    // Brings a database's schema up to date, on purpose rather than as a side effect of starting.
    ":backend:tools:migrate-database",
    // Writes the database dumps the migration tests start from, and restores them for those tests.
    ":backend:tools:dump-database",
)

// `entries/` groups a module's deployables on disk, where it reads well. It carries no meaning in a
// dependency coordinate, so it is kept out of the Gradle path.
project(":backend:system:service:cloud-run").projectDir =
    file("backend/system/service/entries/cloud-run")
