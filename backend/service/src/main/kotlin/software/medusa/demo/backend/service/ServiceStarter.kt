package software.medusa.demo.backend.service

import io.micronaut.context.ApplicationContext
import io.micronaut.runtime.server.EmbeddedServer
import software.medusa.demo.api.server.ProperRawCounterController

/** Service starter. */
data object ServiceStarter {
  /** Starts the Service on [port]. */
  fun start(port: Int): ServiceHandle {
    val counterStore = InMemoryCounterStore()

    val counterController =
        ProperRawCounterController(
            apiHandler = ProperApiHandler(counterStore = counterStore),
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
      }
    }
  }
}
