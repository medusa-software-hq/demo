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
 * Two descriptions of the schema, on purpose.
 *
 * The `CREATE TABLE` statements in the `.sq` files are the schema as it is now, and what every
 * query is checked against at build time. The Flyway migrations in `resources/db/migration` are how
 * an existing database gets there, and old ones can be deleted once nothing still needs them. That
 * the two agree is a claim a test has to make against a real database, not something either one can
 * be derived from.
 */
sqldelight {
  databases {
    create("DemoDatabase") {
      packageName.set("software.medusa.demo.backend.service.db")
      dialect(libs.sqldelight.postgresql.dialect)
    }
  }
}

base { archivesName = "backend-service" }
