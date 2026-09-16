plugins {
  alias(libs.plugins.kotlin.jvm)

  application
}

// Applies pending migrations to the database named by `DATABASE_URL`, and exits. The Service does
// the same when it starts; this is for doing it deliberately.
dependencies {
  implementation(project(":backend:system:storage"))

  runtimeOnly(libs.logback.classic)

  // Restores a real database dump into a Postgres container before migrating it.
  testImplementation(project(":backend:tools:dump-database"))
  testImplementation(libs.kotlinx.coroutines.core)
  testImplementation(libs.kotlin.test)
}

application { mainClass = "software.medusa.demo.backend.migrate_database.MainKt" }

base { archivesName = "backend-tools-migrate-database" }
