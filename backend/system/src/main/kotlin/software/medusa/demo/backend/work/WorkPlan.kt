package software.medusa.demo.backend.work

import java.time.Duration

/**
 * The shape of a run of fake work: how many steps, and how long it waits before each one.
 *
 * Decided by whoever starts runs, and carried into the workflow as its arguments — so a test runs
 * the very same workflow in a fraction of the time somebody watching it would want.
 */
data class WorkPlan(
    val steps: Int,
    val pauseBeforeEachStep: Duration,
) {
  companion object {
    /** Slow enough to watch happen: a few seconds a step. */
    val watchable = WorkPlan(steps = 6, pauseBeforeEachStep = Duration.ofSeconds(3))
  }
}
