package software.medusa.demo.backend.service

import io.micronaut.context.ApplicationContext
import io.micronaut.runtime.server.EmbeddedServer
import software.medusa.demo.api.server.ProperRawCounterController
import software.medusa.demo.api.server.ProperRawTodoController
import software.medusa.demo.api.server.ProperRawWorkController
import software.medusa.demo.backend.storage.Database
import software.medusa.demo.backend.storage.PostgresCounterStore
import software.medusa.demo.backend.storage.PostgresTodoStore
import software.medusa.demo.backend.storage.PostgresWorkRunStore
import software.medusa.demo.backend.work.WorkAvailability

/** Service starter. */
data object ServiceStarter {
  /**
   * Starts the Service on [port], keeping its data in the database at [databaseUrl], and starting
   * work as [work] says it can — or not at all.
   *
   * The schema is brought up to date before the server listens, so no request meets a table that is
   * not there yet. Flyway holds a lock in the database while it migrates, so two instances starting
   * at once do not both try.
   */
  fun start(port: Int, databaseUrl: String, work: WorkAvailability): ServiceHandle {
    val dataSource = Database.connect(databaseUrl)

    // A start that fails here must not leave the pool open behind it.
    runCatching { Database.migrate(dataSource) }.onFailure { dataSource.close() }.getOrThrow()

    val workLauncher: WorkLauncher? =
        when (work) {
          WorkAvailability.Disabled -> null
          is WorkAvailability.Enabled -> TemporalWorkLauncher(work = work)
        }

    val apiHandler =
        ProperApiHandler(
            counterStore = PostgresCounterStore(dataSource = dataSource),
            todoStore = PostgresTodoStore(dataSource = dataSource),
            workRunStore = PostgresWorkRunStore(dataSource = dataSource),
            workLauncher = workLauncher,
        )

    val applicationContext =
        ApplicationContext.builder()
            .banner(false)
            .deduceEnvironment(false)
            // Fail the start, rather than whichever request arrives first.
            .eagerInitSingletons(true)
            .singletons(
                ProperRawCounterController(apiHandler = apiHandler),
                ProperRawTodoController(apiHandler = apiHandler),
                ProperRawWorkController(apiHandler = apiHandler),
            )
            .properties(mapOf("micronaut.server.port" to port))
            .start()

    val server = applicationContext.getBean(EmbeddedServer::class.java).start()

    return object : ServiceHandle {
      override val port: Int = server.port

      override fun close() {
        server.close()

        // Built here, so closed here.
        applicationContext.close()
        workLauncher?.close()
        dataSource.close()
      }
    }
  }
}
