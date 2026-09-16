plugins {
  alias(libs.plugins.kotlin.jvm)

  application
}

// Writes the database dumps migration tests start from, and restores them for those tests. Both
// need `psql` and `pg_dump`, which only a real Postgres installation carries, so this module runs
// one in a container: the one part of the build that needs Docker.
dependencies {
  implementation(project(":backend:system:storage"))
  // Reads which version a migrated database is at, to name its dump after it.
  implementation(libs.flyway.core)
  implementation(libs.testcontainers.postgresql)

  runtimeOnly(libs.logback.classic)
}

application { mainClass = "software.medusa.demo.backend.dump_database.MainKt" }

/** Where the migration tests' starting database is kept, and the rows it is built with. */
val fixtureDirectory =
    rootProject.layout.projectDirectory.dir(
        "backend/tools/migrate-database/src/test/resources/database",
    )

tasks.register<JavaExec>("writeMigrationFixture") {
  group = "database"
  description =
      "Dumps a database at the newest version, holding the rows in seed.sql. Needs Docker."
  classpath = sourceSets["main"].runtimeClasspath
  mainClass = "software.medusa.demo.backend.dump_database.MainKt"

  args(
      fixtureDirectory.file("seed.sql").asFile.path,
      fixtureDirectory.asFile.path,
  )
}

base { archivesName = "backend-tools-dump-database" }
