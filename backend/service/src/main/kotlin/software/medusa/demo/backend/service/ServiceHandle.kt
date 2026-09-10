package software.medusa.demo.backend.service

/** Handle to a locally running Service. */
interface ServiceHandle : AutoCloseable {
  /** The port the Service is listening on. */
  val port: Int
}
