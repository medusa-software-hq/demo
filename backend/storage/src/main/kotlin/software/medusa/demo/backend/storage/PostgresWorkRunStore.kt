package software.medusa.demo.backend.storage

import app.cash.sqldelight.driver.jdbc.asJdbcDriver
import java.util.UUID
import javax.sql.DataSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import software.medusa.demo.backend.storage.db.DemoDatabase
import software.medusa.demo.core.UserId
import software.medusa.demo.core.WorkRun
import software.medusa.demo.core.WorkRunId

/**
 * [WorkRunStore] kept in Postgres, in the `work_runs` table the migrations create.
 *
 * The queries are in `WorkRuns.sq`. Each call runs on the IO dispatcher, since it waits on the
 * network.
 */
class PostgresWorkRunStore(
    dataSource: DataSource,
) : WorkRunStore {
  private val queries = DemoDatabase(dataSource.asJdbcDriver()).workRunsQueries

  override suspend fun listFor(owner: UserId): List<WorkRun> = io {
    queries
        .listForOwner(owner = owner.value) { id, stepsDone, stepsTotal, result ->
          WorkRun(
              id = WorkRunId(id),
              stepsDone = stepsDone,
              stepsTotal = stepsTotal,
              result = result,
          )
        }
        .executeAsList()
  }

  override suspend fun create(owner: UserId, stepsTotal: Int): WorkRunId = io {
    val runId = WorkRunId(UUID.randomUUID().toString())

    queries.create(id = runId.value, owner = owner.value, stepsTotal = stepsTotal)

    runId
  }

  override suspend fun delete(runId: WorkRunId) {
    io { queries.delete(id = runId.value) }
  }

  override suspend fun recordProgress(runId: WorkRunId, stepsDone: Int) {
    io { queries.recordProgress(stepsDone = stepsDone, id = runId.value) }
  }

  override suspend fun finish(runId: WorkRunId, result: String) {
    io { queries.finish(result = result, id = runId.value) }
  }

  private suspend fun <ResultT> io(block: () -> ResultT): ResultT =
      withContext(Dispatchers.IO) { block() }
}
