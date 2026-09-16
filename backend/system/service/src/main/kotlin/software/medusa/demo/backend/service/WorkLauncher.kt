package software.medusa.demo.backend.service

import software.medusa.demo.core.WorkRunId

/**
 * Sets work going. The Service's half of work: it starts runs, and never does them.
 *
 * Only exists where work is enabled. Where it is not, the Service has no launcher at all, rather
 * than one that refuses — so there is nothing that could be asked to start a run by mistake.
 */
interface WorkLauncher : AutoCloseable {
  /** How many steps each run it starts takes, for the run to be recorded with before it begins. */
  val stepsPerRun: Int

  /** Starts the run [runId], which has already been recorded. */
  suspend fun launch(runId: WorkRunId)
}
