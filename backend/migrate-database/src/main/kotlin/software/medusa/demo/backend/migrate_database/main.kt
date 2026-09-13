package software.medusa.demo.backend.migrate_database

import software.medusa.demo.backend.storage.Database

/** Which database to migrate. */
private const val databaseUrlVariableName = "DATABASE_URL"

/**
 * Brings a database's schema up to date, and does nothing else.
 *
 * Run it with `--no-daemon`: a reused Gradle daemon keeps the environment it started with, so it
 * would not see a `DATABASE_URL` set for this run and would fail as though none were given.
 *
 *     DATABASE_URL='jdbc:postgresql://…' ./gradlew --no-daemon :backend:migrate-database:run
 */
fun main() {
  // Never printed: it carries the database password.
  val databaseUrl =
      checkNotNull(System.getenv(databaseUrlVariableName)) { "$databaseUrlVariableName is not set" }

  Database.connect(databaseUrl).use { dataSource ->
    println("Applied ${Database.migrate(dataSource)} migration(s)")
  }
}
