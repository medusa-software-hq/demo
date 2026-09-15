package software.medusa.demo.backend.storage

import software.medusa.demo.core.UserId
import software.medusa.demo.core.WorkRun
import software.medusa.demo.core.WorkRunId

/**
 * Stores work runs: who set them going, and how far they have got.
 *
 * Two kinds of caller, and the operations split along them. Listing and starting are on behalf of
 * an owner, and see only that owner's runs. Recording progress is the worker's, which does work
 * nobody is waiting on the other end of a request for — it has a run id and no caller, so those
 * operations name the run alone.
 *
 * Suspending, because the store is a database: a call that waits on the network must not do it on
 * the thread that serves every other request.
 */
interface WorkRunStore {
  /** @return Every run [owner] has started, oldest first. */
  suspend fun listFor(owner: UserId): List<WorkRun>

  /**
   * Records a run for [owner], of [stepsTotal] steps, none of them done.
   *
   * @return The new run's id.
   */
  suspend fun create(owner: UserId, stepsTotal: Int): WorkRunId

  /** Forgets the run [runId] — one that was recorded and then could not be started. */
  suspend fun delete(runId: WorkRunId)

  /** Records that run [runId] has done [stepsDone] steps. Never moves a run backwards. */
  suspend fun recordProgress(runId: WorkRunId, stepsDone: Int)

  /** Records that run [runId] has finished every step, with [result]. */
  suspend fun finish(runId: WorkRunId, result: String)
}
