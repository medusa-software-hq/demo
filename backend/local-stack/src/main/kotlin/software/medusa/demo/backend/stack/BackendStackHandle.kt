package software.medusa.demo.backend.stack

import software.medusa.demo.backend.service.ServiceHandle

/**
 * Handle to a locally running backend stack: the Service, and the Postgres it keeps counters in.
 */
interface BackendStackHandle : AutoCloseable {
  /** Service handle. */
  val serviceHandle: ServiceHandle
}
