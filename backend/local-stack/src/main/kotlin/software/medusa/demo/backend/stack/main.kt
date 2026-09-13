package software.medusa.demo.backend.stack

/** Entry point for the local stack. */
fun main() {
  LocalDatabaseCluster.start().use { cluster ->
    BackendStackStarter.start(cluster).use { stackHandle ->
      println("Demo service port: ${stackHandle.serviceHandle.port}")

      Thread.currentThread().join()
    }
  }
}
