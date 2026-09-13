package software.medusa.demo.backend.migrate_database

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.runBlocking
import software.medusa.demo.backend.dump_database.ContainerDatabase
import software.medusa.demo.backend.storage.Database
import software.medusa.demo.backend.storage.PostgresCounterStore
import software.medusa.demo.core.Counter
import software.medusa.demo.core.CounterId

/**
 * Migrating a database that already holds data, judged by what the storage layer reads back.
 *
 * It starts from a dump of the oldest database still supported rather than from an empty one: a
 * migration that works on an empty database can still fail on rows that exist, and once old
 * migrations are deleted, an empty database is no longer where any real one starts. What it checks
 * is what the app would see — the store's answers — rather than what the schema looks like.
 */
class MigrationTest {
  private val oldestSupported: String =
      checkNotNull(javaClass.getResource("/database/oldest-supported.sql")) {
            "The migration fixture is missing; see :backend:dump-database:writeMigrationFixture"
          }
          .readText()

  /** The rows in oldest-supported.seed.sql, in the order they were inserted. */
  private val seeded =
      listOf(
          Counter(id = CounterId("00000000-0000-4000-8000-000000000000"), count = 0),
          Counter(id = CounterId("7f3c2d1e-9b8a-4c6d-8e5f-4a3b2c1d0e9f"), count = Long.MAX_VALUE),
          Counter(id = CounterId("b1e2c3d4-5f6a-4b7c-8d9e-0f1a2b3c4d5e"), count = Long.MIN_VALUE),
          Counter(id = CounterId("not-a-uuid-ü-ñ-漢字"), count = 42),
      )

  /**
   * Restores the oldest supported database, migrates it the way `migrate-database` does, and hands
   * [block] a store over the result.
   */
  private fun withMigratedStore(block: suspend (PostgresCounterStore) -> Unit) {
    ContainerDatabase.start().use { database ->
      database.execute(oldestSupported)

      migrateDatabase(database.url)

      Database.connect(database.url).use { dataSource ->
        runBlocking { block(PostgresCounterStore(dataSource)) }
      }
    }
  }

  @Test
  fun `every counter the oldest supported database held reads back after migrating`() =
      withMigratedStore { store ->
        assertEquals(expected = seeded, actual = store.listAll())
      }

  @Test
  fun `a migrated database keeps working, carrying on from where the dump left off`() =
      withMigratedStore { store ->
        val created = store.create()

        // After the seeded counters, not among them: the identity sequence resumed from where the
        // dump left it rather than starting again.
        assertEquals(
            expected = seeded.map { it.id } + created,
            actual = store.listAll().map { it.id },
        )

        assertEquals(
            expected = 1L,
            actual = store.increment(CounterId("00000000-0000-4000-8000-000000000000")),
        )
      }
}
