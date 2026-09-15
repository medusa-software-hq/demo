package software.medusa.demo.backend.worker

import io.temporal.activity.ActivityOptions
import io.temporal.workflow.Workflow
import java.time.Duration
import software.medusa.demo.backend.work.FakeWorkActivities
import software.medusa.demo.backend.work.FakeWorkWorkflow

/**
 * The fake work, as a workflow: wait, compute, write down how far it got — again, and again — then
 * the result.
 *
 * The waiting is a durable timer rather than a sleeping thread, so a worker restarted halfway
 * through picks the run up where it was rather than starting it over. That is most of what doing
 * this on Temporal is for.
 */
class FakeWorkWorkflowImpl : FakeWorkWorkflow {
  private companion object {
    /** One step's computing and one database write. A step that takes longer is retried. */
    val stepTimeout: Duration = Duration.ofSeconds(30)
  }

  private val activities: FakeWorkActivities =
      Workflow.newActivityStub(
          FakeWorkActivities::class.java,
          ActivityOptions.newBuilder().setStartToCloseTimeout(stepTimeout).build(),
      )

  override fun run(runId: String, steps: Int, pauseMillisBeforeEachStep: Long) {
    var carried = 0L

    for (step in 1..steps) {
      Workflow.sleep(Duration.ofMillis(pauseMillisBeforeEachStep))

      carried = activities.computeStep(runId, step, carried)
    }

    activities.finish(runId, "Did $steps steps; the answer is $carried")
  }
}
