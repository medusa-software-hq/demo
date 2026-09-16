package software.medusa.demo.backend.service

import io.temporal.client.WorkflowClient
import io.temporal.client.WorkflowOptions
import io.temporal.workflow.Functions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import software.medusa.demo.backend.work.FakeWorkWorkflow
import software.medusa.demo.backend.work.WorkAvailability
import software.medusa.demo.core.WorkRunId

/**
 * [WorkLauncher] that starts a [FakeWorkWorkflow] on Temporal, as [work] describes.
 *
 * Starting a workflow only asks Temporal to record that it should run; whichever worker polls the
 * task queue does it. So this returns as soon as Temporal has said yes, whether or not a worker is
 * running anywhere at the time.
 */
class TemporalWorkLauncher(
    private val work: WorkAvailability.Enabled,
) : WorkLauncher {
  /** Opened lazily by Temporal: nothing connects until the first run is started. */
  private val stubs = work.connection.openStubs()

  private val workflowClient: WorkflowClient = work.connection.workflowClient(stubs)

  override val stepsPerRun: Int = work.plan.steps

  override suspend fun launch(runId: WorkRunId) {
    val workflow =
        workflowClient.newWorkflowStub(
            FakeWorkWorkflow::class.java,
            WorkflowOptions.newBuilder()
                .setTaskQueue(work.taskQueue)
                // The run's own id, so a run started twice is refused by Temporal rather than done
                // twice.
                .setWorkflowId(runId.value)
                .build(),
        )

    // A blocking gRPC call to Temporal, kept off the thread that serves other requests.
    withContext(Dispatchers.IO) {
      WorkflowClient.start(
          Functions.Proc {
            workflow.run(
                runId.value,
                work.plan.steps,
                work.plan.pauseBeforeEachStep.toMillis(),
            )
          },
      )
    }
  }

  override fun close() {
    stubs.shutdown()
  }
}
