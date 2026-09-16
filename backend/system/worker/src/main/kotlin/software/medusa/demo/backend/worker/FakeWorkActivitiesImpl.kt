package software.medusa.demo.backend.worker

import kotlinx.coroutines.runBlocking
import software.medusa.demo.backend.storage.WorkRunStore
import software.medusa.demo.backend.work.FakeWorkActivities
import software.medusa.demo.core.WorkRunId

/**
 * The fake work's computing and bookkeeping.
 *
 * The computing is counting primes, which is real work of a size that grows with each step and
 * whose answer never changes — so a retried step comes to the same thing it would have the first
 * time.
 *
 * Blocking, because Temporal's Java SDK runs activities on threads of its own and expects them to
 * return rather than suspend.
 */
class FakeWorkActivitiesImpl(
    private val workRunStore: WorkRunStore,
) : FakeWorkActivities {
  private companion object {
    /** How far step 1 counts primes. Step N counts N times as far. */
    const val primeSearchLimitPerStep = 200_000

    /** Folds each step's answer into the last, so the result depends on the order they came in. */
    const val foldMultiplier = 31L

    /** How many primes there are below [limit], by sieve. */
    fun countPrimesBelow(limit: Int): Int {
      val composite = BooleanArray(limit)
      var count = 0

      for (candidate in 2 until limit) {
        if (!composite[candidate]) {
          count++

          for (multiple in candidate.toLong() * candidate until limit step candidate.toLong()) {
            composite[multiple.toInt()] = true
          }
        }
      }

      return count
    }
  }

  override fun computeStep(runId: String, step: Int, carried: Long): Long {
    val answer = countPrimesBelow(primeSearchLimitPerStep * step)

    runBlocking { workRunStore.recordProgress(runId = WorkRunId(runId), stepsDone = step) }

    return carried * foldMultiplier + answer
  }

  override fun finish(runId: String, result: String) {
    runBlocking { workRunStore.finish(runId = WorkRunId(runId), result = result) }
  }
}
