plugins {
  alias(libs.plugins.kotlin.jvm)

  application
}

// Writes the database dumps migration tests start from, and restores them for those tests. Both
// need
// `psql` and `pg_dump`, which only a real Postgres installation carries, so this module runs one in
// a
// container: the one part of the build that needs Docker.
dependencies {
  implementation(project(":backend:storage"))
  // Migrates to a chosen version, which the storage module does not offer: the Service only ever
  // wants the latest.
  implementation(libs.flyway.core)
  implementation(libs.testcontainers.postgresql)

  runtimeOnly(libs.logback.classic)
}

application { mainClass = "software.medusa.demo.backend.dump_database.MainKt" }

/** Where the migration tests' starting database is kept, and the rows it is built with. */
val fixtureDirectory =
    rootProject.layout.projectDirectory.dir("backend/migrate-database/src/test/resources/database")

tasks.register<JavaExec>("writeMigrationFixture") {
  group = "database"
  description = "Writes the dump the migration tests start from, at -PtargetVersion. Needs Docker."
  classpath = sourceSets["main"].runtimeClasspath
  mainClass = "software.medusa.demo.backend.dump_database.MainKt"

  val targetVersion = providers.gradleProperty("targetVersion")

  // Read when the task runs, so every other build does not need the property.
  argumentProviders.add(
      CommandLineArgumentProvider {
        listOf(
            targetVersion.orNull
                ?: error("Pass the version to migrate to: -PtargetVersion=<version>"),
            fixtureDirectory.file("oldest-supported.seed.sql").asFile.path,
            fixtureDirectory.file("oldest-supported.sql").asFile.path,
        )
      },
  )
}

base { archivesName = "backend-dump-database" }
