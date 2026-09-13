package software.medusa.demo.backend.stack

import java.io.File
import org.flywaydb.core.Flyway
import software.medusa.demo.backend.service.Database

/**
 * Writes the dump the migration test starts from: a database migrated to one version, holding the
 * rows in a seed file.
 *
 * Generated rather than written by hand, so the Flyway history inside it carries the real checksum
 * of every migration it has — which is also what makes an edited migration fail the test.
 *
 *     ./gradlew --no-daemon :backend:local-stack:writeMigrationFixture -PtargetVersion=1
 *
 * Arguments: the version to migrate to, the seed file, the file to write.
 */
fun main(arguments: Array<String>) {
  val (targetVersion, seedPath, fixturePath) = arguments

  LocalDatabase.start().use { database ->
    Database.connect(database.url).use { dataSource ->
      Flyway.configure().dataSource(dataSource).target(targetVersion).load().migrate()
    }

    database.execute(File(seedPath).readText())

    File(fixturePath)
        .writeText(
            """
            |-- The oldest database this app still has to migrate from: version $targetVersion, holding
            |-- the rows in ${File(seedPath).name}. Generated, not written by hand:
            |--
            |--     ./gradlew --no-daemon :backend:local-stack:writeMigrationFixture -PtargetVersion=$targetVersion
            |--
            |-- Before deleting a migration, regenerate this at the version that will then be oldest.
            |
            |"""
                .trimMargin() + database.dump() + "\n",
        )
  }

  println("Wrote $fixturePath at version $targetVersion")
}
