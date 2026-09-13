package software.medusa.demo.backend.service.cloud_run

import software.medusa.demo.backend.service.ServiceStarter

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

  ServiceStarter.start(port = port, databaseUrl = databaseUrl).use { handle ->
    println("Demo service listening on ${handle.port}")

    Thread.currentThread().join()
  }
}
