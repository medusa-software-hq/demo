package software.medusa.demo.backend

import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import kotlin.test.Test
import kotlin.test.assertEquals
import software.medusa.demo.backend.stack.BackendStackStarter
import software.medusa.demo.backend.stack.SharedDatabaseCluster
import software.medusa.demo.backend.stack.TestCaller

/**
 * The Service turning away requests that do not say who is calling — and not turning away webhooks,
 * which never can.
 *
 * Plain HTTP rather than the API client: what is under test is what happens before any route
 * answers, including to requests the client would never make.
 */
class NamedCallerTest {
  private val requestTimeout: Duration = Duration.ofSeconds(20)

  /** The status [path] answers a GET with, sent with [headers] and nothing else. */
  private fun statusOf(path: String, headers: Map<String, String>): Int =
      BackendStackStarter.start(SharedDatabaseCluster.shared).use { stackHandle ->
        HttpClient.newHttpClient()
            .send(
                HttpRequest.newBuilder(
                        URI.create("http://localhost:${stackHandle.serviceHandle.port}$path"),
                    )
                    .timeout(requestTimeout)
                    .GET()
                    .apply { headers.forEach { (name, value) -> header(name, value) } }
                    .build(),
                HttpResponse.BodyHandlers.discarding(),
            )
            .statusCode()
      }

  @Test
  fun `a request naming its caller is answered`() {
    assertEquals(expected = 200, actual = statusOf("/counters", TestCaller.headers))
  }

  @Test
  fun `a request naming nobody is refused`() {
    assertEquals(expected = 403, actual = statusOf("/counters", headers = emptyMap()))
  }

  // Both or neither: an address without an id, or an id without an address, is not a caller the
  // Worker would ever have named.
  @Test
  fun `a request naming only half a caller is refused`() {
    assertEquals(
        expected = 403,
        actual = statusOf("/counters", TestCaller.headers.filterKeys { it.endsWith("-subject") }),
    )
  }

  // Past the filter and on to routing, which has nothing there yet — the point is that it was
  // asked, rather than the request being refused for naming nobody.
  @Test
  fun `a webhook naming nobody is not refused for it`() {
    assertEquals(expected = 404, actual = statusOf("/webhooks/example", headers = emptyMap()))
  }

  // Beneath the webhook path, not merely spelled like it.
  @Test
  fun `a path that only starts like the webhook path is refused`() {
    assertEquals(expected = 403, actual = statusOf("/webhooksexample", headers = emptyMap()))
  }
}
