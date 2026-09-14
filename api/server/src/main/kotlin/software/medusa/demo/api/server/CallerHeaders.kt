package software.medusa.demo.api.server

import io.micronaut.http.context.ServerRequestContext
import software.medusa.demo.core.UserId

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

/**
 * Who sent the request being answered.
 *
 * Read from the request Micronaut is serving rather than passed to each route, because a generated
 * route takes what the contract names, and the caller is not in the contract. A request naming
 * nobody never gets this far — the filter in front of the routes refuses it — so its absence here
 * is a broken assumption, not an answer.
 */
internal fun currentCaller(): UserId {
  val request =
      ServerRequestContext.currentRequest<Any>().orElseThrow {
        IllegalStateException("No request is being served, so there is no caller to name")
      }

  return UserId(
      checkNotNull(request.headers.get(CallerHeaders.email)) {
        "The request names no caller, which the filter in front of the routes should have refused"
      },
  )
}
