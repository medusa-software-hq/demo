package software.medusa.demo.backend.service.cloud_run

import software.medusa.demo.backend.service.ServiceStarter
import software.medusa.demo.backend.work.WorkAvailability

/** The port Cloud Run routes to. */
private const val portVariableName = "PORT"

/** Where the counters are kept. Mounted from Secret Manager by the deployment. */
private const val databaseUrlVariableName = "DATABASE_URL"

/**
 * The Service on Cloud Run.
 *
 * Both of these are required and neither has a default. A service listening on a port nothing
 * routes to, or keeping counters somewhere nobody meant, would each look healthy from here.
 */
fun main() {
  // Cloud Run always sets this, so an absent one means this is not running where it thinks it is.
  val portText = checkNotNull(System.getenv(portVariableName)) { "$portVariableName is not set" }

  val port = checkNotNull(portText.toIntOrNull()) { "$portVariableName is not a number: $portText" }

  // Never printed: it carries the database password.
  val databaseUrl =
      checkNotNull(System.getenv(databaseUrlVariableName)) { "$databaseUrlVariableName is not set" }

  // Disabled, deliberately and in one place. No deployed environment has a Temporal namespace, or
  // anywhere a worker could run, so starting a run here could only record work nobody would do.
  // Enabling it is this line becoming a `WorkAvailability.Enabled` read from the environment — the
  // connection and key the platform will hand over — and nothing past it changes.
  val work = WorkAvailability.Disabled

  ServiceStarter.start(port = port, databaseUrl = databaseUrl, work = work).use { handle ->
    println("Demo service listening on ${handle.port}")

    Thread.currentThread().join()
  }
}
