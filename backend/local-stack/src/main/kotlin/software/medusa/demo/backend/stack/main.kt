package software.medusa.demo.backend.stack

/** Entry point for the local stack. */
fun main() {
  LocalDatabaseCluster.start().use { cluster ->
    LocalTemporalServer.start(withUi = true).use { temporal ->
      BackendStackStarter.start(cluster, temporal).use { stackHandle ->
        println("Demo service port: ${stackHandle.serviceHandle.port}")
        println(
            "Temporal UI: http://localhost:${temporal.uiPort}" +
                "/namespaces/${LocalTemporalServer.namespace}/workflows",
        )

        Thread.currentThread().join()
      }
    }
  }
}
