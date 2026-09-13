package software.medusa.demo.backend.stack

/**
 * A caller, named the way the Worker in front of the deployed Service names one.
 *
 * The Service refuses a request that names nobody, so whatever calls it in a test says who it is —
 * and nothing a test checks depends on who that turns out to be.
 */
data object TestCaller {
  /** The headers naming this caller, as the Worker would set them. */
  val headers: Map<String, String> =
      mapOf(
          "x-medusa-user-subject" to "7335d417-61da-459d-899c-0a01c76a3a47",
          "x-medusa-user-email" to "someone@medusa.software",
      )
}
