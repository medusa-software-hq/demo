package software.medusa.demo.backend.stack

import software.medusa.demo.backend.service.ServiceHandle
import software.medusa.demo.backend.service.ServiceStarter

/** Backend stack starter. */
data object BackendStackStarter {
  /**
   * Starts the backend stack on a port nobody has to choose.
   *
   * The Service takes the port it is told to take, the same on this machine as on Cloud Run.
   * Finding a free one is what running several of these at once needs, and that is this module's
   * concern — it is the harness — rather than something the Service carries around for it.
   */
  fun start(): BackendStackHandle {
    // Allocated before anything starts, and let go of by the time it does: a port cannot be taken
    // while it is still being held to keep it free.
    val portAllocation = BackendStackPortAllocation.allocate()

    val serviceHandle = ServiceStarter.start(port = portAllocation.servicePort)

    return object : BackendStackHandle {
      override val serviceHandle: ServiceHandle = serviceHandle

      override fun close() {
        serviceHandle.close()
      }
    }
  }
}
