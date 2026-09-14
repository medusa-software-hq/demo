package software.medusa.demo.backend.stack

import java.util.UUID
import software.medusa.demo.backend.service.ServiceStarter
import software.medusa.demo.backend.work.WorkAvailability
import software.medusa.demo.backend.work.WorkPlan
import software.medusa.demo.backend.worker.WorkerStarter

/** Backend stack starter. */
data object BackendStackStarter {
  /**
   * Starts the backend stack on a fresh database of [cluster], doing work on [temporal].
   *
   * A database of its own each time, so nothing one stack leaves behind can prop another one up.
   */
  fun start(
      cluster: LocalDatabaseCluster,
      temporal: LocalTemporalServer?,
      workPlan: WorkPlan = WorkPlan.watchable,
  ): BackendStackHandle = start(cluster.createDatabase(), temporal, workPlan)

  /**
   * Starts the backend stack against [database], which it uses and leaves alone, doing work on
   * [temporal] shaped by [workPlan].
   *
   * With no [temporal], work is disabled — the way it is in the deployed environments, and the one
   * way to see that arrangement locally.
   *
   * For a database that has to outlive the stack: stopping one stack and starting another on the
   * same database is what a restart of the real service looks like from the database's side.
   *
   * The Service takes the port it is told to take, the same on this machine as on Cloud Run.
   * Finding a free one is what running several of these at once needs, and that is this module's
   * concern — it is the harness — rather than something the Service carries around for it.
   */
  fun start(
      database: LocalDatabase,
      temporal: LocalTemporalServer?,
      workPlan: WorkPlan = WorkPlan.watchable,
  ): BackendStackHandle {
    // Allocated before anything starts, and let go of by the time it does: a port cannot be taken
    // while it is still being held to keep it free.
    val portAllocation = BackendStackPortAllocation.allocate()

    val work =
        when (temporal) {
          null -> WorkAvailability.Disabled

          else ->
              WorkAvailability.Enabled(
                  connection = temporal.connection,
                  // A queue of its own, so stacks sharing one server never do each other's work.
                  taskQueue = "work-${UUID.randomUUID()}",
                  plan = workPlan,
              )
        }

    val serviceHandle =
        ServiceStarter.start(
            port = portAllocation.servicePort,
            databaseUrl = database.url,
            work = work,
        )

    // After the Service, which migrates the database as it starts: the worker writes to tables
    // that only exist once it has.
    val workerHandle =
        when (work) {
          WorkAvailability.Disabled -> null

          is WorkAvailability.Enabled ->
              runCatching { WorkerStarter.start(work = work, databaseUrl = database.url) }
                  .onFailure { serviceHandle.close() }
                  .getOrThrow()
        }

    return object : BackendStackHandle {
      override val serviceHandle = serviceHandle

      override fun close() {
        workerHandle?.close()
        serviceHandle.close()
      }
    }
  }
}
