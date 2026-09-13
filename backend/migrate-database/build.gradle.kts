plugins {
  alias(libs.plugins.kotlin.jvm)

  application
}

// Applies pending migrations to the database named by `DATABASE_URL`, and exits. The Service does
// the same when it starts; this is for doing it deliberately.
dependencies {
  implementation(project(":backend:storage"))

  runtimeOnly(libs.logback.classic)
}

application { mainClass = "software.medusa.demo.backend.migrate_database.MainKt" }

base { archivesName = "backend-migrate-database" }
