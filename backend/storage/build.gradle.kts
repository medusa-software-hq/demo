plugins {
  alias(libs.plugins.kotlin.jvm)
  alias(libs.plugins.sqldelight)

  `java-library`
}

// Where the app's data lives: the store, the schema its queries are checked against, and the
// migrations that bring a real database to that schema. Its own module because more than one thing
// runs against the data — the Service, the tool that migrates a database on purpose, and a worker
// when there is one — and none of them should need the others to reach it.
dependencies {
  implementation(project(":backend"))
  // `api`: the store speaks in counters and their ids.
  api(project(":core"))

  implementation(libs.kotlinx.coroutines.core)

  // `api`: `Database.connect` hands its caller the pool, which the caller then owns and closes.
  api(libs.hikaricp)
  implementation(libs.flyway.core)
  implementation(libs.sqldelight.jdbc.driver)
  runtimeOnly(libs.flyway.database.postgresql)
  runtimeOnly(libs.postgresql)
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
      packageName.set("software.medusa.demo.backend.storage.db")
      dialect(libs.sqldelight.postgresql.dialect)
    }
  }
}

base { archivesName = "backend-storage" }
