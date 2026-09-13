package software.medusa.demo.backend.dump_database

import java.io.File
import org.flywaydb.core.Flyway
import software.medusa.demo.backend.storage.Database

/**
 * Writes a migration test's starting database: one migrated to the newest version, holding the rows
 * in the seed file, dumped as `V<version>.sql`.
 *
 * Only the newest version, because the seed describes the tables as they are now, the way the
 * SQLDelight schema does. A dump of an older version is the one written while it was newest, and is
 * kept in git from then on.
 *
 * Taken from a real Postgres rather than written by hand, so the Flyway history inside it carries
 * the real checksum of every migration it has — which is also what makes an edited migration fail
 * the tests.
 *
 *     ./gradlew --no-daemon :backend:dump-database:writeMigrationFixture
 *
 * Arguments: the seed file, the directory to write into.
 */
fun main(arguments: Array<String>) {
  val (seedPath, fixtureDirectory) = arguments

  ContainerDatabase.start().use { database ->
    val version =
        Database.connect(database.url).use { dataSource ->
          Database.migrate(dataSource)

          checkNotNull(Flyway.configure().dataSource(dataSource).load().info().current()) {
                "There are no migrations to dump a database for"
              }
              .version
              .version
        }

    database.execute(File(seedPath).readText())

    val fixture = File(fixtureDirectory, "V$version.sql")

    fixture.writeText(header(version) + database.dump())

    println("Wrote $fixture")
  }
}

private fun header(version: String): String =
    """
    |-- A database at version $version, holding the rows seed.sql held while $version was the newest
    |-- version. Generated, not written by hand:
    |--
    |--     ./gradlew --no-daemon :backend:dump-database:writeMigrationFixture
    |--
    |-- Kept once a newer version exists: it is where databases still at $version migrate from.
    |
    |"""
        .trimMargin()
