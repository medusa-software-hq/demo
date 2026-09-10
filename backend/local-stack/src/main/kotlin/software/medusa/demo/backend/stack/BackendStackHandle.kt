package software.medusa.demo.backend.stack

import software.medusa.demo.backend.service.ServiceHandle

/** Handle to a locally running full backend stack (currently just the Service). */
interface BackendStackHandle : AutoCloseable {
  /** Service handle. */
  val serviceHandle: ServiceHandle
}
