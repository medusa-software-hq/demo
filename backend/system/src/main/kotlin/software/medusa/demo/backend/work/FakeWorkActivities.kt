package software.medusa.demo.backend.work

import io.temporal.activity.ActivityInterface
import io.temporal.activity.ActivityMethod

/**
 * What [FakeWorkWorkflow] cannot do itself: compute, and write to the database.
 *
 * A workflow has to replay exactly the same way every time it is resumed, so anything slow,
 * anything that touches the outside world, and anything that might come out differently happens
 * here, and the workflow keeps only the answers.
 */
@ActivityInterface
interface FakeWorkActivities {
  /**
   * Computes step [step] of run [runId], folds its answer into [carried], records that the step is
   * done, and answers the new carried value.
   */
  @ActivityMethod fun computeStep(runId: String, step: Int, carried: Long): Long

  /** Records that run [runId] has finished, with [result]. */
  @ActivityMethod fun finish(runId: String, result: String)
}
