package software.medusa.demo.backend

import kotlin.test.fail
import okhttp3.OkHttpClient
import software.medusa.demo.api.client.ApiClient
import software.medusa.demo.backend.stack.BackendStackHandle
import software.medusa.demo.backend.stack.TestCaller

/**
 * A client for this stack's service, calling as [caller].
 *
 * Every request names its caller the way the Worker in front of the deployed service would, since
 * the service refuses any that do not.
 */
fun BackendStackHandle.apiClientFor(caller: TestCaller = TestCaller.someone): ApiClient =
    ApiClient.connect(
        baseUrl = "http://localhost:${serviceHandle.port}",
        okHttpClient =
            OkHttpClient.Builder()
                .addInterceptor { chain ->
                  chain.proceed(
                      chain
                          .request()
                          .newBuilder()
                          .apply { caller.headers.forEach { (name, value) -> header(name, value) } }
                          .build(),
                  )
                }
                .build(),
    )

/**
 * Runs [call], failing the test rather than crashing it when the API does not answer.
 *
 * A service that cannot be reached is a red test, not a broken one: nothing is wrong with the code
 * under test, and the report should not say there is. None of these errors carry anything — what
 * happened is in the log, written where the client gave up on it.
 */
inline fun <ResponseT> assertApiCallSucceeds(call: () -> ResponseT): ResponseT =
    try {
      call()
    } catch (callError: ApiClient.CallError) {
      // Exhaustive, so a new kind of failure fails to compile here rather than arriving as a crash
      // nobody chose a shade of red for.
      val what =
          when (callError) {
            ApiClient.NetworkError -> "the network did not carry it"
            ApiClient.IncompatibilityError -> "the server did not speak the contract"
            ApiClient.InternalServerError -> "the server failed the request"
          }

      fail("Expected the API call to succeed, but $what")
    }
