package software.medusa.demo.backend.service

import io.micronaut.context.ApplicationContext
import io.micronaut.runtime.server.EmbeddedServer
import software.medusa.demo.api.server.ProperRawCounterController
import software.medusa.demo.backend.storage.Database
import software.medusa.demo.backend.storage.PostgresCounterStore

/** Service starter. */
data object ServiceStarter {
  /**
   * Starts the Service on [port], keeping its counters in the database at [databaseUrl].
   *
   * The schema is brought up to date before the server listens, so no request meets a table that is
   * not there yet. Flyway holds a lock in the database while it migrates, so two instances starting
   * at once do not both try.
   */
  fun start(port: Int, databaseUrl: String): ServiceHandle {
    val dataSource = Database.connect(databaseUrl)

    // A start that fails here must not leave the pool open behind it.
    runCatching { Database.migrate(dataSource) }.onFailure { dataSource.close() }.getOrThrow()

    val counterController =
        ProperRawCounterController(
            apiHandler =
                ProperApiHandler(counterStore = PostgresCounterStore(dataSource = dataSource)),
        )

    val applicationContext =
        ApplicationContext.builder()
            .banner(false)
            .deduceEnvironment(false)
            // Fail the start, rather than whichever request arrives first.
            .eagerInitSingletons(true)
            .singletons(counterController)
            .properties(mapOf("micronaut.server.port" to port))
            .start()

    val server = applicationContext.getBean(EmbeddedServer::class.java).start()

    return object : ServiceHandle {
      override val port: Int = server.port

      override fun close() {
        server.close()

        // Built here, so closed here.
        applicationContext.close()
        dataSource.close()
      }
    }
  }
}
