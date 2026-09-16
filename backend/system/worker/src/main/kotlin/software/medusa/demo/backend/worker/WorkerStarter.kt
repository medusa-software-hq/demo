package software.medusa.demo.backend.worker

import io.temporal.worker.WorkerFactory
import software.medusa.demo.backend.storage.Database
import software.medusa.demo.backend.storage.PostgresWorkRunStore
import software.medusa.demo.backend.work.WorkAvailability

/** Worker starter. */
data object WorkerStarter {
  /**
   * Starts a worker polling [work]'s task queue, writing what it does to the database at
   * [databaseUrl].
   *
   * Only for [WorkAvailability.Enabled]: where work is disabled there is nothing to poll for, and a
   * caller holding [WorkAvailability.Disabled] should not be able to ask.
   *
   * It opens a database pool of its own rather than borrowing the Service's, because deployed the
   * two would be separate processes, and that is the arrangement worth running locally too. It does
   * not migrate: the schema is brought up to date by whatever runs first, which is the Service.
   */
  fun start(work: WorkAvailability.Enabled, databaseUrl: String): WorkerHandle {
    val dataSource = Database.connect(databaseUrl)
    val stubs = work.connection.openStubs()

    val workerFactory = runCatching {
      WorkerFactory.newInstance(work.connection.workflowClient(stubs)).apply {
        newWorker(work.taskQueue).apply {
          // The class, not an instance: Temporal makes a fresh workflow object per run.
          registerWorkflowImplementationTypes(FakeWorkWorkflowImpl::class.java)

          registerActivitiesImplementations(
              FakeWorkActivitiesImpl(workRunStore = PostgresWorkRunStore(dataSource)),
          )
        }

        start()
      }
    }
        // A start that fails here must not leave the pool or the connection open behind it.
        .onFailure {
          stubs.shutdownNow()
          dataSource.close()
        }
        .getOrThrow()

    return object : WorkerHandle {
      override fun close() {
        // Now rather than gracefully: whoever closes a worker wants its pollers gone, and a test
        // suite that starts a stack per test cannot wait out a drain each time. Temporal hands an
        // interrupted activity to the next worker that polls, so nothing is lost by it.
        workerFactory.shutdownNow()

        // Opened here, so closed here.
        stubs.shutdownNow()
        dataSource.close()
      }
    }
  }
}
