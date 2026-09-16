package software.medusa.demo.backend.stack

import java.time.Duration
import software.medusa.demo.backend.work.WorkPlan

/**
 * Work that is over almost as soon as it starts: for whatever drives the stack from outside and
 * waits for runs to finish, rather than for somebody watching one happen.
 */
private const val briefPauseMillis = 100L

private val briefWorkPlan =
    WorkPlan(steps = 3, pauseBeforeEachStep = Duration.ofMillis(briefPauseMillis))

/**
 * Entry point for the local stack.
 *
 * `--work-plan=brief` makes every run of work finish in well under a second. Without it, runs are
 * paced to be watched.
 *
 * Runs until the process is told to stop, and stops everything it started when it is — the database
 * cluster included, which has no shutdown hook of its own because whoever starts one is meant to
 * know when it is done. Here that is this function, and the moment is the process ending, so a
 * harness can end it with a signal and leave nothing running behind it.
 */
fun main(args: Array<String>) {
  val workPlan =
      when (args.toList()) {
        emptyList<String>() -> WorkPlan.watchable
        listOf("--work-plan=brief") -> briefWorkPlan
        else -> error("Unrecognised arguments: ${args.joinToString(" ")}")
      }

  val started = ArrayDeque<AutoCloseable>()

  // Registered before anything starts, so a start that fails halfway still has what came before it
  // stopped. Closed in reverse, so nothing outlives what it depends on.
  Runtime.getRuntime()
      .addShutdownHook(
          Thread {
            synchronized(started) {
              while (started.isNotEmpty()) {
                runCatching { started.removeLast().close() }
              }
            }
          },
      )

  fun <ResourceT : AutoCloseable> kept(resource: ResourceT): ResourceT = resource.also {
    synchronized(started) { started.addLast(it) }
  }

  val cluster = kept(LocalDatabaseCluster.start())
  val temporal = kept(LocalTemporalServer.start(withUi = true))
  val stackHandle = kept(BackendStackStarter.start(cluster, temporal, workPlan))

  println("Demo service port: ${stackHandle.serviceHandle.port}")
  println(
      "Temporal UI: http://localhost:${temporal.uiPort}" +
          "/namespaces/${LocalTemporalServer.namespace}/workflows",
  )

  Thread.currentThread().join()
}
