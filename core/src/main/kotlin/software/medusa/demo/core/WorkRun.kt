package software.medusa.demo.core

/**
 * A piece of work somebody set going, and how far it has got.
 *
 * Finished once it has a [result]; until then, [stepsDone] of [stepsTotal] says how far along it
 * is.
 */
data class WorkRun(
    val id: WorkRunId,
    val stepsDone: Int,
    val stepsTotal: Int,
    val result: String?,
)
