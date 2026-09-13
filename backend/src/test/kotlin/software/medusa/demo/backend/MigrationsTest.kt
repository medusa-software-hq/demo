package software.medusa.demo.backend

import app.cash.sqldelight.driver.jdbc.asJdbcDriver
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.runBlocking
import software.medusa.demo.api.client.ApiClient
import software.medusa.demo.backend.service.Database
import software.medusa.demo.backend.service.db.DemoDatabase
import software.medusa.demo.backend.stack.BackendStackStarter
import software.medusa.demo.backend.stack.LocalDatabase
import software.medusa.demo.core.Counter
import software.medusa.demo.core.CounterId

/**
 * The migrations against a database that already holds data, and against the schema the queries are
 * checked against.
 *
 * Those are two descriptions of one schema — Flyway's migrations, and the `CREATE TABLE` statements
 * in the `.sq` files — and nothing makes them agree but this. It starts from a dump of the oldest
 * database still supported, because a migration that works on an empty database can still fail on
 * rows that exist, and because once old migrations are deleted an empty database is no longer where
 * any real one starts.
 */
class MigrationsTest {
  private val oldestSupported: String =
      checkNotNull(javaClass.getResource("/database/oldest-supported.sql")) {
            "The migration fixture is missing; see writeMigrationFixture"
          }
          .readText()

  @Test
  fun `the oldest supported database migrates to the schema the queries are checked against`() {
    val migrated =
        LocalDatabase.start().use { database ->
          database.execute(oldestSupported)

          Database.connect(database.url).use { dataSource -> Database.migrate(dataSource) }

          database.dumpSchema()
        }

    val defined =
        LocalDatabase.start().use { database ->
          Database.connect(database.url).use { dataSource ->
            DemoDatabase.Schema.create(dataSource.asJdbcDriver())
          }

          database.dumpSchema()
        }

    assertEquals(expected = defined, actual = migrated)
  }

  @Test
  fun `what the oldest supported database held is still there once the service has migrated it`() =
      runBlocking {
        LocalDatabase.start().use { database ->
          database.execute(oldestSupported)

          // Starting the service is what migrates a real one.
          BackendStackStarter.start(database).use { stackHandle ->
            val apiClient =
                ApiClient.connect(baseUrl = "http://localhost:${stackHandle.serviceHandle.port}")

            // The rows in oldest-supported.seed.sql, in the order they were inserted.
            assertEquals(
                expected =
                    listOf(
                        Counter(id = CounterId("00000000-0000-4000-8000-000000000000"), count = 0),
                        Counter(
                            id = CounterId("7f3c2d1e-9b8a-4c6d-8e5f-4a3b2c1d0e9f"),
                            count = Long.MAX_VALUE,
                        ),
                        Counter(
                            id = CounterId("b1e2c3d4-5f6a-4b7c-8d9e-0f1a2b3c4d5e"),
                            count = Long.MIN_VALUE,
                        ),
                        Counter(id = CounterId("not-a-uuid-ü-ñ-漢字"), count = 42),
                    ),
                actual = apiClient.listCounters(),
            )
          }
        }
      }
}
