package software.medusa.demo.backend.stack

/** Entry point for the local stack. */
fun main() {
  BackendStackStarter.start().use { stackHandle ->
    println("Demo service port: ${stackHandle.serviceHandle.port}")

    Thread.currentThread().join()
  }
}
