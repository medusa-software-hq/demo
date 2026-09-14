package software.medusa.demo.backend

import java.time.Duration
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import software.medusa.demo.api.ApiTypes
import software.medusa.demo.api.client.ApiClient
import software.medusa.demo.backend.stack.BackendStackHandle
import software.medusa.demo.backend.stack.BackendStackStarter
import software.medusa.demo.backend.stack.LocalTemporalServer
import software.medusa.demo.backend.stack.SharedDatabaseCluster
import software.medusa.demo.backend.stack.SharedTemporalServer
import software.medusa.demo.backend.stack.TestCaller
import software.medusa.demo.backend.work.WorkPlan
import software.medusa.demo.core.WorkRun
import software.medusa.demo.core.WorkRunId

/** Work, through the API, with a real Temporal and a real worker behind it — and without one. */
class WorkRoutesTest {
  private companion object {
    /**
     * Several steps, so there is an in-between; short ones, so a run finishes in well under a
     * second.
     */
    val quickPlan = WorkPlan(steps = 3, pauseBeforeEachStep = Duration.ofMillis(50))

    val finishTimeout: Duration = Duration.ofSeconds(30)
    val pollInterval: Duration = Duration.ofMillis(50)
  }

  private fun startStack(
      temporal: LocalTemporalServer? = SharedTemporalServer.shared
  ): BackendStackHandle =
      BackendStackStarter.start(SharedDatabaseCluster.shared, temporal, quickPlan)

  /** Starts a run through [apiClient], failing the test if work is not enabled. */
  private suspend fun startWork(apiClient: ApiClient): WorkRunId =
      when (val response = assertApiCallSucceeds { apiClient.startWork() }) {
        is ApiTypes.StartWorkResponse.Started -> response.runId
        ApiTypes.StartWorkResponse.Disabled -> fail("Work was disabled where it should be enabled")
      }

  /** Asks [apiClient] about [runId] until it has finished, and answers it as it finished. */
  private suspend fun awaitFinished(apiClient: ApiClient, runId: WorkRunId): WorkRun {
    val deadline = Instant.now() + finishTimeout

    while (true) {
      val run = assertApiCallSucceeds { apiClient.getWork() }.runs.single { it.id == runId }

      if (run.result != null) {
        return run
      }

      check(Instant.now() < deadline) { "Run $runId did not finish within $finishTimeout" }

      delay(pollInterval.toMillis())
    }
  }

  @Test
  fun `where work is enabled, it says so, and nobody has any runs to begin with`() = runBlocking {
    startStack().use { stackHandle ->
      assertEquals(
          expected = ApiTypes.WorkOverview(enabled = true, runs = emptyList()),
          actual = assertApiCallSucceeds { stackHandle.apiClientFor().getWork() },
      )
    }
  }

  @Test
  fun `a started run is listed at once, and finishes with every step done`() = runBlocking {
    startStack().use { stackHandle ->
      val apiClient = stackHandle.apiClientFor()
      val runId = startWork(apiClient)

      // Listed before the worker has done anything with it: recorded first, then started.
      val listed = assertApiCallSucceeds { apiClient.getWork() }.runs.single()

      assertEquals(expected = runId, actual = listed.id)
      assertEquals(expected = quickPlan.steps, actual = listed.stepsTotal)

      val finished = awaitFinished(apiClient, runId)

      assertEquals(expected = quickPlan.steps, actual = finished.stepsDone)
      assertTrue(
          finished.result.orEmpty().startsWith("Did ${quickPlan.steps} steps"),
          "Unexpected result: ${finished.result}",
      )
    }
  }

  @Test
  fun `somebody else's runs are not listed`() = runBlocking {
    startStack().use { stackHandle ->
      val owner = stackHandle.apiClientFor(TestCaller.someone)
      val stranger = stackHandle.apiClientFor(TestCaller.someoneElse)

      startWork(owner)

      assertEquals(
          expected = emptyList(),
          actual = assertApiCallSucceeds { stranger.getWork() }.runs,
      )
    }
  }

  // The deployed arrangement: no Temporal, so no work — said plainly, rather than a run that is
  // recorded and never moves.
  @Test
  fun `where work is disabled, it says so, and a run cannot be started`() = runBlocking {
    startStack(temporal = null).use { stackHandle ->
      val apiClient = stackHandle.apiClientFor()

      assertEquals(
          expected = ApiTypes.WorkOverview(enabled = false, runs = emptyList()),
          actual = assertApiCallSucceeds { apiClient.getWork() },
      )

      assertEquals(
          expected = ApiTypes.StartWorkResponse.Disabled,
          actual = assertApiCallSucceeds { apiClient.startWork() },
      )

      assertEquals(
          expected = emptyList(),
          actual = assertApiCallSucceeds { apiClient.getWork() }.runs,
      )
    }
  }
}
