package software.medusa.demo.backend.stack

import software.medusa.demo.backend.service.ServiceStarter

/** Backend stack starter. */
data object BackendStackStarter {
  /**
   * Starts the backend stack on a database of its own, which goes when the stack does.
   *
   * A fresh one each time, so nothing one stack leaves behind can prop another one up.
   */
  fun start(): BackendStackHandle {
    val database = LocalDatabase.start()

    val stackHandle = runCatching { start(database) }.onFailure { database.close() }.getOrThrow()

    return object : BackendStackHandle {
      override val serviceHandle = stackHandle.serviceHandle

      override fun close() {
        stackHandle.close()
        database.close()
      }
    }
  }

  /**
   * Starts the backend stack against [database], which it uses and leaves open.
   *
   * For a database that has to outlive the stack: stopping one stack and starting another on the
   * same database is what a restart of the real service looks like from the database's side.
   *
   * The Service takes the port it is told to take, the same on this machine as on Cloud Run.
   * Finding a free one is what running several of these at once needs, and that is this module's
   * concern — it is the harness — rather than something the Service carries around for it.
   */
  fun start(database: LocalDatabase): BackendStackHandle {
    // Allocated before anything starts, and let go of by the time it does: a port cannot be taken
    // while it is still being held to keep it free.
    val portAllocation = BackendStackPortAllocation.allocate()

    val serviceHandle =
        ServiceStarter.start(port = portAllocation.servicePort, databaseUrl = database.url)

    return object : BackendStackHandle {
      override val serviceHandle = serviceHandle

      override fun close() {
        serviceHandle.close()
      }
    }
  }
}
