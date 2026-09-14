package software.medusa.demo.api.server

/**
 * The headers naming who is calling.
 *
 * Not in the contract, on purpose. No client of the API sends them: the gateway in front of it sets
 * them after checking a sign-in, and removes any a caller tried to set. What the contract describes
 * is what a caller asks for; who is asking is the gateway's to say.
 */
data object CallerHeaders {
  /**
   * The caller's id, as whoever signed them in knows them. Unique, but not permanent — nothing is
   * kept against it.
   */
  const val subject = "x-medusa-user-subject"

  /**
   * The caller's address, as their identity provider verified it. What personal data belongs to.
   */
  const val email = "x-medusa-user-email"
}
