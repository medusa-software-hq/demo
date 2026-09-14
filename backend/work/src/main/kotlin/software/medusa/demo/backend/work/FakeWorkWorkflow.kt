package software.medusa.demo.backend.work

import io.temporal.workflow.WorkflowInterface
import io.temporal.workflow.WorkflowMethod

/**
 * Work that stands in for real work: some waiting, some computing, and progress written down as it
 * goes.
 *
 * Its arguments are plain values rather than a [WorkPlan] or a run id type. They are what Temporal
 * keeps in the workflow's history, and a number or a string reads the same in any SDK, in the CLI
 * and in the web UI.
 */
@WorkflowInterface
interface FakeWorkWorkflow {
  /**
   * Does [steps] steps of work for the run [runId], waiting [pauseMillisBeforeEachStep] before
   * each, and records what it came to.
   */
  @WorkflowMethod fun run(runId: String, steps: Int, pauseMillisBeforeEachStep: Long)
}
