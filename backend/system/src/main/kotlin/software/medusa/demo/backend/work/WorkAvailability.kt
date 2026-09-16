package software.medusa.demo.backend.work

/**
 * Whether an environment runs work, and if it does, where.
 *
 * A type rather than a switch, so enabling work cannot be half done. [Enabled] cannot be made
 * without everything running work takes, and [Disabled] carries nothing to forget to fill in. What
 * starts the Service, or a worker, is handed one of these and has to say what each case means for
 * it — and a new case would not compile until every one of them had.
 *
 * Enabling work in an environment is therefore handing it [Enabled]: the one line of its entry
 * point that decides, and nothing downstream of it.
 */
sealed interface WorkAvailability {
  /**
   * No work here. Runs recorded earlier can still be read; no new one can start.
   *
   * Where the deployed environments are today: none of them has a Temporal namespace yet, or
   * anywhere a worker could run.
   */
  data object Disabled : WorkAvailability

  /** Work runs on Temporal: reached through [connection], on [taskQueue], shaped by [plan]. */
  data class Enabled(
      val connection: TemporalConnection,

      /**
       * The queue the Service starts runs on and a worker polls. Both must be told the same one,
       * which is why it is decided here rather than by either of them.
       */
      val taskQueue: String,
      val plan: WorkPlan,
  ) : WorkAvailability
}
