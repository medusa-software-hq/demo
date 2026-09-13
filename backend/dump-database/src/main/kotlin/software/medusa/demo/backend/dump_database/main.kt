package software.medusa.demo.backend.dump_database

import java.io.File
import org.flywaydb.core.Flyway
import software.medusa.demo.backend.storage.Database

/**
 * Writes the dump the migration tests start from: a database migrated to one version, holding the
 * rows in a seed file.
 *
 * Taken from a real Postgres rather than written by hand, so the Flyway history inside it carries
 * the real checksum of every migration it has — which is also what makes an edited migration fail
 * the tests.
 *
 *     ./gradlew --no-daemon :backend:dump-database:writeMigrationFixture -PtargetVersion=1
 *
 * Arguments: the version to migrate to, the seed file, the file to write.
 */
fun main(arguments: Array<String>) {
  val (targetVersion, seedPath, fixturePath) = arguments

  ContainerDatabase.start().use { database ->
    Database.connect(database.url).use { dataSource ->
      Flyway.configure().dataSource(dataSource).target(targetVersion).load().migrate()
    }

    database.execute(File(seedPath).readText())

    File(fixturePath).writeText(header(targetVersion, File(seedPath).name) + database.dump())
  }

  println("Wrote $fixturePath at version $targetVersion")
}

private fun header(targetVersion: String, seedName: String): String =
    """
    |-- The oldest database this app still has to migrate from: version $targetVersion, holding the
    |-- rows in $seedName. Generated, not written by hand:
    |--
    |--     ./gradlew --no-daemon :backend:dump-database:writeMigrationFixture -PtargetVersion=$targetVersion
    |--
    |-- Before deleting a migration, regenerate this at the version that will then be oldest.
    |
    |"""
        .trimMargin()
