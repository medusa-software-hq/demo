package software.medusa.demo.backend.service

import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpResponse
import io.micronaut.http.HttpStatus
import io.micronaut.http.annotation.RequestFilter
import io.micronaut.http.annotation.ServerFilter
import org.slf4j.LoggerFactory
import software.medusa.demo.api.server.CallerHeaders

private val logger = LoggerFactory.getLogger(NamedCallerFilter::class.java)

/**
 * Refuses every request that does not say who is calling, webhooks apart.
 *
 * The Service never sees a sign-in. The Worker in front of it checks one and names the caller in
 * headers, and those are taken at their word here because nothing but that Worker can reach the
 * Service: Cloud Run lets one account invoke it, and the Worker holds that account's key. So this
 * is not where anybody is authenticated. It is where a request that was not — one that came around
 * the Worker's check somehow, or a Worker that stopped naming callers — is turned away rather than
 * served as nobody in particular.
 *
 * Webhooks arrive naming nobody, by design: their senders cannot sign in, and what vouches for one
 * is the signature its sender puts on it, which its own route checks.
 */
@ServerFilter(ServerFilter.MATCH_ALL_PATTERN)
class NamedCallerFilter {
  companion object {
    /** Where webhooks arrive: the one path a request may reach naming nobody. */
    private const val webhooksPath = "/webhooks"

    /** Whether [path] is [prefix] itself, or beneath it — not merely spelled alike. */
    private fun isUnder(path: String, prefix: String): Boolean =
        path == prefix || path.startsWith("$prefix/")
  }

  /** Nothing, to let [request] through; a refusal, when it names no caller and should. */
  @RequestFilter
  fun requireNamedCaller(request: HttpRequest<*>): HttpResponse<*>? {
    if (isUnder(request.path, webhooksPath)) {
      return null
    }

    val named =
        listOf(CallerHeaders.subject, CallerHeaders.email).all { header ->
          !request.headers.get(header).isNullOrBlank()
        }

    if (named) {
      return null
    }

    // Forbidden rather than unauthorized: there is no sign-in to ask for here. A request that
    // names nobody did not come through the Worker the way one should.
    logger.warn("Refusing {} {}: it names no caller", request.method, request.path)

    return HttpResponse.status<Any>(HttpStatus.FORBIDDEN)
  }
}
