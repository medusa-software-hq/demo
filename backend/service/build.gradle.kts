plugins {
  alias(libs.plugins.kotlin.jvm)
  alias(libs.plugins.ksp)
  alias(libs.plugins.micronaut.library)
  alias(libs.plugins.sqldelight)

  `java-library`
}

// The Service: the store, the handler that answers the API out of it, and the starter that brings
// the whole thing up. The routes themselves belong to `:api:server`.
dependencies {
  implementation(project(":backend"))
  // `api`: the store and the handler are written in these.
  api(project(":core"))
  api(project(":api:server"))

  implementation("io.micronaut:micronaut-http-server-netty")
  implementation(libs.kotlinx.coroutines.core)

  // `api`: `Database.connect` hands its caller the pool, which the caller then owns and closes.
  api(libs.hikaricp)
  implementation(libs.flyway.core)
  implementation(libs.sqldelight.jdbc.driver)
  runtimeOnly(libs.flyway.database.postgresql)
  runtimeOnly(libs.postgresql)
  implementation("io.micronaut.kotlin:micronaut-kotlin-runtime")

  testImplementation(libs.kotlin.test)
}

micronaut {
  runtime("netty")
  testRuntime("junit5")
  processing {
    incremental(true)
    annotations("software.medusa.demo.*")
  }
}

/**
 * The migrations are the only description of the schema.
 *
 * SQLDelight derives the current tables from them to type-check every query, and writes each one
 * back out as plain SQL for Flyway to apply. So the thing queries are checked against and the thing
 * the database is built from cannot drift apart: there is only one of them.
 */
val flywayMigrations = layout.buildDirectory.dir("generated/flyway")

sqldelight {
  databases {
    create("DemoDatabase") {
      packageName.set("software.medusa.demo.backend.service.db")
      dialect(libs.sqldelight.postgresql.dialect)
      deriveSchemaFromMigrations.set(true)
      migrationOutputDirectory.set(flywayMigrations.map { it.dir("db/migration") })
      migrationOutputFileFormat.set(".sql")
    }
  }
}

// Where Flyway looks by default, `classpath:db/migration`. Registered with the task that produces
// it, so everything that reads resources — packaging, the classpath inspection, the IDE — knows to
// generate first, rather than only whichever task was told by hand.
sourceSets {
  main { resources.srcDir(files(flywayMigrations).builtBy("generateMainDemoDatabaseMigrations")) }
}

base { archivesName = "backend-service" }
