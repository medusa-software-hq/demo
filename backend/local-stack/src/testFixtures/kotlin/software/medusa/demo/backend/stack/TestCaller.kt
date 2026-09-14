package software.medusa.demo.backend.stack

/**
 * A caller, named the way the Worker in front of the deployed Service names one.
 *
 * The Service refuses a request that names nobody, so whatever calls it in a test says who it is.
 * Two of them, because what is personal is only shown to be personal by somebody else not seeing
 * it.
 */
data class TestCaller(val subject: String, val email: String) {
  companion object {
    /** Whoever a test calls as when it does not care who. */
    val someone =
        TestCaller(
            subject = "7335d417-61da-459d-899c-0a01c76a3a47",
            email = "someone@medusa.software",
        )

    /** Somebody who is not [someone]. */
    val someoneElse =
        TestCaller(
            subject = "c0ffee00-61da-459d-899c-0a01c76a3a47",
            email = "someone-else@medusa.software",
        )
  }

  /** The headers naming this caller, as the Worker would set them. */
  val headers: Map<String, String>
    get() = mapOf("x-medusa-user-subject" to subject, "x-medusa-user-email" to email)
}
