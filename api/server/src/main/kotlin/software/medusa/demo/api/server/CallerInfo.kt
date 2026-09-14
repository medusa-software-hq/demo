package software.medusa.demo.api.server

import io.micronaut.http.context.ServerRequestContext
import software.medusa.demo.core.UserId

/**
 * Who an operation on personal data is on behalf of.
 *
 * Handed to such an operation as a context rather than an argument, so it cannot be called without
 * one, and cannot be handed somebody else's by position. Only operations on somebody's data take
 * it: counters are nobody's, and their signatures say so.
 */
data class CallerInfo(
    /** Whose data the operation reads and writes. */
    val userId: UserId,

    /**
     * The caller's id, as whoever signed them in knows them. For telling people apart in what is
     * logged; nothing is kept against it.
     */
    val subject: String,
)

/**
 * Runs [block] on behalf of whoever sent the request being answered.
 *
 * The one place a request becomes a caller. Inline, so [block] may suspend — and the caller is read
 * before it does, so it does not depend on how far the request's context follows a coroutine.
 */
internal inline fun <ResultT> withCurrentCaller(block: context(CallerInfo) () -> ResultT): ResultT =
    context(currentCallerInfo()) { block() }

/**
 * Who sent the request being answered.
 *
 * Read from the request Micronaut is serving rather than passed to each route, because a generated
 * route takes what the contract names, and the caller is not in the contract. A request naming
 * nobody never gets this far — the filter in front of the routes refuses it — so its absence here
 * is a broken assumption, not an answer.
 */
internal fun currentCallerInfo(): CallerInfo {
  val request =
      ServerRequestContext.currentRequest<Any>().orElseThrow {
        IllegalStateException("No request is being served, so there is no caller to name")
      }

  fun header(name: String): String =
      checkNotNull(request.headers.get(name)) {
        "The request names no caller, which the filter in front of the routes should have refused"
      }

  return CallerInfo(
      userId = UserId(header(CallerHeaders.email)),
      subject = header(CallerHeaders.subject),
  )
}
