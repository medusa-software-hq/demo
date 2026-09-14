package software.medusa.demo.backend.stack

import io.temporal.api.workflowservice.v1.DescribeNamespaceRequest
import java.io.File
import java.time.Duration
import java.time.Instant
import java.util.concurrent.TimeUnit
import software.medusa.demo.backend.work.TemporalConnection

/**
 * A Temporal dev server — `temporal server start-dev` — running for as long as this is open.
 *
 * The real server rather than an in-memory stand-in: the same frontend, timers and persistence a
 * worker meets in Temporal Cloud, and, when asked for, the web UI to watch workflows in. It is the
 * Temporal CLI, which the build downloads, checks against the checksum its release publishes, and
 * hands to the JVM as a system property — so running this needs nothing installed, and no Docker.
 *
 * Keeps its state in memory, so it goes with the process.
 */
class LocalTemporalServer
private constructor(
    private val process: Process,

    /** The port clients and workers connect to. */
    val port: Int,

    /** Where the web UI answers, when it was asked for. */
    val uiPort: Int?,
) : AutoCloseable {
  companion object {
    /** The system property the build puts the CLI's path in. */
    const val cliPathProperty = "demo.temporal.cli"

    /** The namespace the server is started with, besides the `default` every one has. */
    const val namespace = "demo"

    private val readinessTimeout: Duration = Duration.ofSeconds(30)
    private val readinessPollInterval: Duration = Duration.ofMillis(100)
    private val stopTimeout: Duration = Duration.ofSeconds(10)

    /**
     * Starts a dev server on ports nothing else holds, and returns once it answers for [namespace].
     *
     * With the web UI when [withUi], which a person running the stack wants and a test does not.
     */
    fun start(withUi: Boolean): LocalTemporalServer {
      val cli =
          checkNotNull(System.getProperty(cliPathProperty)?.let(::File)) {
            "$cliPathProperty is not set. Run this through Gradle, which downloads the Temporal CLI " +
                "and sets it."
          }

      check(cli.canExecute()) { "The Temporal CLI at $cli is not executable" }

      // Chosen all at once, each while the others are still held, so no two come back the same —
      // and all let go before the server tries to take them.
      val ports = PortUtils.allocate { port ->
        PortUtils.allocate { httpPort ->
          PortUtils.allocate { metricsPort ->
            PortUtils.allocate { uiPort -> Ports(port, httpPort, metricsPort, uiPort) }
          }
        }
      }

      // Written somewhere, rather than discarded, so a server that will not start can say why.
      val log = File.createTempFile("temporal-dev-server-", ".log")

      val command =
          listOf(
              cli.path,
              "server",
              "start-dev",
              "--namespace",
              namespace,
              "--port",
              "${ports.port}",
              "--http-port",
              "${ports.httpPort}",
              "--metrics-port",
              "${ports.metricsPort}",
              "--log-level",
              "warn",
          ) + if (withUi) listOf("--ui-port", "${ports.uiPort}") else listOf("--headless")

      val process =
          ProcessBuilder(command)
              .redirectErrorStream(true)
              .redirectOutput(ProcessBuilder.Redirect.appendTo(log))
              .start()

      val server =
          LocalTemporalServer(process, port = ports.port, uiPort = ports.uiPort.takeIf { withUi })

      runCatching { server.awaitReady(log) }.onFailure { server.close() }.getOrThrow()

      return server
    }
  }

  /** The ports a dev server listens on: its API, HTTP, metrics and web UI. */
  private data class Ports(val port: Int, val httpPort: Int, val metricsPort: Int, val uiPort: Int)

  /** How a client in this JVM reaches [namespace] on this server. */
  val connection: TemporalConnection
    get() =
        TemporalConnection(
            target = "localhost:$port",
            namespace = namespace,
            auth = TemporalConnection.Auth.None,
        )

  /**
   * Waits until the server answers for [namespace].
   *
   * The port opening is not enough: the namespace is created a moment after the frontend starts
   * listening, and a worker that polls before then is refused.
   */
  private fun awaitReady(log: File) {
    val stubs = connection.openStubs()

    try {
      val deadline = Instant.now() + readinessTimeout
      val request = DescribeNamespaceRequest.newBuilder().setNamespace(namespace).build()

      while (
          runCatching {
            stubs.blockingStub().withDeadlineAfter(1, TimeUnit.SECONDS).describeNamespace(request)
          }
              .isFailure
      ) {
        check(process.isAlive) {
          "The Temporal dev server exited with ${process.exitValue()} before it was ready; see $log"
        }

        check(Instant.now() < deadline) {
          "The Temporal dev server was not ready within $readinessTimeout; see $log"
        }

        Thread.sleep(readinessPollInterval.toMillis())
      }
    } finally {
      stubs.shutdownNow()
    }
  }

  override fun close() {
    process.destroy()

    if (!process.waitFor(stopTimeout.toMillis(), TimeUnit.MILLISECONDS)) {
      process.destroyForcibly().waitFor()
    }
  }
}
