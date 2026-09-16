package software.medusa.demo.backend.migrate_database

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import software.medusa.demo.backend.dump_database.ContainerDatabase
import software.medusa.demo.backend.storage.CounterStore
import software.medusa.demo.backend.storage.Database
import software.medusa.demo.backend.storage.PostgresCounterStore
import software.medusa.demo.backend.storage.PostgresTodoStore
import software.medusa.demo.backend.storage.PostgresWorkRunStore
import software.medusa.demo.backend.storage.TodoStore
import software.medusa.demo.backend.storage.WorkRunStore
import software.medusa.demo.core.Counter
import software.medusa.demo.core.CounterId
import software.medusa.demo.core.Todo
import software.medusa.demo.core.TodoId
import software.medusa.demo.core.UserId
import software.medusa.demo.core.WorkRun
import software.medusa.demo.core.WorkRunId

/**
 * Migrating databases that already hold data, judged by what the storage layer reads back.
 *
 * Each dump in `resources/database` is a database as it was while its version was the newest,
 * holding the rows `seed.sql` held then. A real database may still be at any of those versions, so
 * each one is restored, migrated the way `migrate-database` migrates, and read back through the
 * stores. A dump — and its tests here — goes when no database at its version needs migrating from
 * any more.
 *
 * Not from an empty database: a migration that works on no rows can still fail on the rows that
 * exist. And what is checked is what the app would see, the stores' answers, rather than what the
 * schema looks like.
 */
class MigrationTest {
  private companion object {
    val someone = UserId("someone@medusa.software")
    val someoneElse = UserId("someone-else@medusa.software")

    /** The counters `seed.sql` has held since version 1, in the order they were inserted. */
    val seededCounters =
        listOf(
            Counter(id = CounterId("00000000-0000-4000-8000-000000000000"), count = 0),
            Counter(id = CounterId("7f3c2d1e-9b8a-4c6d-8e5f-4a3b2c1d0e9f"), count = Long.MAX_VALUE),
            Counter(id = CounterId("b1e2c3d4-5f6a-4b7c-8d9e-0f1a2b3c4d5e"), count = Long.MIN_VALUE),
            Counter(id = CounterId("not-a-uuid-ü-ñ-漢字"), count = 42),
        )

    /**
     * The todos `seed.sql` has held since version 2: whose they are, in the order they were
     * inserted.
     */
    val seededTodos =
        mapOf(
            someone to
                listOf(
                    Todo(
                        id = TodoId("0f8d6c1e-2b3a-4c5d-8e7f-9a0b1c2d3e4f"),
                        title = "Water the plants",
                        done = false,
                    ),
                    Todo(
                        id = TodoId("1a2b3c4d-5e6f-4a7b-8c9d-0e1f2a3b4c5d"),
                        title = "Already done",
                        done = true,
                    ),
                ),
            someoneElse to
                listOf(
                    Todo(
                        id = TodoId("2b3c4d5e-6f7a-4b8c-9d0e-1f2a3b4c5d6e"),
                        title = "Zażółć gęślą jaźń 漢字 ✓",
                        done = false,
                    ),
                    Todo(
                        id = TodoId("3c4d5e6f-7a8b-4c9d-8e0f-2a3b4c5d6e7f"),
                        title = "long ".repeat(200),
                        done = true,
                    ),
                ),
        )

    /** The work runs `seed.sql` has held since version 3: whose they are, in the order inserted. */
    val seededWorkRuns =
        mapOf(
            someone to
                listOf(
                    WorkRun(
                        id = WorkRunId("4d5e6f7a-8b9c-4d0e-9f1a-3b4c5d6e7f80"),
                        stepsDone = 6,
                        stepsTotal = 6,
                        result = "Did 6 steps; the answer is 42",
                    ),
                    WorkRun(
                        id = WorkRunId("5e6f7a8b-9c0d-4e1f-8a2b-4c5d6e7f8091"),
                        stepsDone = 3,
                        stepsTotal = 6,
                        result = null,
                    ),
                ),
            someoneElse to
                listOf(
                    WorkRun(
                        id = WorkRunId("6f7a8b9c-0d1e-4f2a-9b3c-5d6e7f809102"),
                        stepsDone = 0,
                        stepsTotal = 3,
                        result = null,
                    ),
                ),
        )
  }

  /** The stores a migrated database is read back through. */
  private class Stores(
      val counters: CounterStore,
      val todos: TodoStore,
      val workRuns: WorkRunStore,
  )

  /**
   * Restores the dump of a database at [version], migrates it the way `migrate-database` does, and
   * hands [block] the stores over the result.
   */
  private fun withMigratedStores(version: Int, block: suspend Stores.() -> Unit) {
    val dump =
        checkNotNull(javaClass.getResource("/database/V$version.sql")) {
              "There is no dump of version $version; see :backend:dump-database:writeMigrationFixture"
            }
            .readText()

    ContainerDatabase.start().use { database ->
      database.execute(dump)

      migrateDatabase(database.url)

      Database.connect(database.url).use { dataSource ->
        runBlocking {
          Stores(
                  counters = PostgresCounterStore(dataSource),
                  todos = PostgresTodoStore(dataSource),
                  workRuns = PostgresWorkRunStore(dataSource),
              )
              .block()
        }
      }
    }
  }

  /** That the migrated database takes writes, carrying on from where its dump left off. */
  private suspend fun Stores.assertKeepsWorking() {
    val counterId = counters.create()

    // After the seeded counters, not among them: the identity sequence resumed from where the dump
    // left it rather than starting again.
    assertEquals(
        expected = seededCounters.map { it.id } + counterId,
        actual = counters.listAll().map { it.id },
    )

    assertEquals(
        expected = 1L,
        actual = counters.increment(CounterId("00000000-0000-4000-8000-000000000000")),
    )

    val todosBefore = todos.listFor(someone).map { it.id }
    val todoId = todos.create(owner = someone, title = "Added after migrating")

    assertEquals(expected = todosBefore + todoId, actual = todos.listFor(someone).map { it.id })

    assertTrue(todos.setDone(owner = someone, todoId = todoId, done = true))

    val runsBefore = workRuns.listFor(someone).map { it.id }
    val runId = workRuns.create(owner = someone, stepsTotal = 2)

    workRuns.recordProgress(runId = runId, stepsDone = 1)
    workRuns.finish(runId = runId, result = "Finished after migrating")

    assertEquals(
        expected =
            WorkRun(id = runId, stepsDone = 2, stepsTotal = 2, result = "Finished after migrating"),
        actual = workRuns.listFor(someone).last(),
    )
    assertEquals(expected = runsBefore + runId, actual = workRuns.listFor(someone).map { it.id })
  }

  @Test
  fun `the counters a database at version 1 held read back after migrating`() =
      withMigratedStores(version = 1) {
        assertEquals(expected = seededCounters, actual = counters.listAll())
      }

  // Version 1 had no todos, so migrating from it is where they begin: empty, for everybody.
  @Test
  fun `a database at version 1 comes out with no todos for anybody`() =
      withMigratedStores(version = 1) {
        assertEquals(expected = emptyList(), actual = todos.listFor(someone))
        assertEquals(expected = emptyList(), actual = todos.listFor(someoneElse))
      }

  @Test
  fun `a database at version 1 keeps working after migrating`() =
      withMigratedStores(version = 1) { assertKeepsWorking() }

  @Test
  fun `the counters and todos a database at version 2 held read back after migrating`() =
      withMigratedStores(version = 2) {
        assertEquals(expected = seededCounters, actual = counters.listAll())

        seededTodos.forEach { (owner, todosOfOwner) ->
          assertEquals(expected = todosOfOwner, actual = todos.listFor(owner))
        }
      }

  @Test
  fun `a database at version 2 keeps working after migrating`() =
      withMigratedStores(version = 2) { assertKeepsWorking() }

  // Versions 1 and 2 had no work, so migrating from either is where it begins: none, for anybody.
  @Test
  fun `databases at versions 1 and 2 come out with no work runs for anybody`() {
    for (version in 1..2) {
      withMigratedStores(version = version) {
        assertEquals(expected = emptyList(), actual = workRuns.listFor(someone))
        assertEquals(expected = emptyList(), actual = workRuns.listFor(someoneElse))
      }
    }
  }

  @Test
  fun `everything a database at version 3 held reads back after migrating`() =
      withMigratedStores(version = 3) {
        assertEquals(expected = seededCounters, actual = counters.listAll())

        seededTodos.forEach { (owner, todosOfOwner) ->
          assertEquals(expected = todosOfOwner, actual = todos.listFor(owner))
        }

        seededWorkRuns.forEach { (owner, runsOfOwner) ->
          assertEquals(expected = runsOfOwner, actual = workRuns.listFor(owner))
        }
      }

  @Test
  fun `a database at version 3 keeps working after migrating`() =
      withMigratedStores(version = 3) { assertKeepsWorking() }
}
