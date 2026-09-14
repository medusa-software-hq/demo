package software.medusa.demo.api.client

import com.fasterxml.jackson.core.JacksonException
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import java.io.IOException
import java.net.HttpURLConnection
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import org.slf4j.LoggerFactory
import software.medusa.demo.api.ApiTypes
import software.medusa.demo.api.raw.client.ApiClientException
import software.medusa.demo.api.raw.client.ApiException
import software.medusa.demo.api.raw.client.ApiResponse
import software.medusa.demo.api.raw.client.ApiServerException
import software.medusa.demo.api.raw.client.RawCounterClient
import software.medusa.demo.core.Counter
import software.medusa.demo.core.CounterId

private val logger = LoggerFactory.getLogger(ApiClient::class.java)

/** Demo API client. */
class ApiClient(
    private val rawCounterClient: RawCounterClient,
) {
  /**
   * The call was not answered.
   *
   * Sealed, so that a caller deciding what to do about a failure has to decide about all of them: a
   * new one added here stops compiling wherever the old ones were handled, rather than falling
   * through as something nobody expected.
   *
   * These carry nothing — no message, no cause, no stack trace. What happened is in the log, put
   * there where it was discarded.
   */
  sealed class CallError : Exception() {
    final override fun fillInStackTrace(): Throwable = this
  }

  /** The client didn't receive a proper response over the network in time. */
  @Suppress("ObjectInheritsException", "JavaIoSerializableObjectMustHaveReadResolve")
  data object NetworkError : CallError()

  /**
   * The server returned an unexpected response. It might indicate that it doesn't speak the exact
   * API protocol this client understands (potentially, it's newer) or it's misconfigured or
   * misbehaving.
   */
  @Suppress("ObjectInheritsException", "JavaIoSerializableObjectMustHaveReadResolve")
  data object IncompatibilityError : CallError()

  /** The server encountered an unexpected internal error during processing the request. */
  @Suppress("ObjectInheritsException", "JavaIoSerializableObjectMustHaveReadResolve")
  data object InternalServerError : CallError()

  companion object {
    /**
     * Connects to the service at [baseUrl], over [okHttpClient].
     *
     * The HTTP client is the caller's to give, because what every request has to carry besides the
     * call itself — who is making it — is the caller's to know.
     */
    fun connect(baseUrl: String, okHttpClient: OkHttpClient = OkHttpClient()): ApiClient =
        ApiClient(
            rawCounterClient =
                RawCounterClient(
                    objectMapper = ObjectMapper().registerKotlinModule(),
                    baseUrl = baseUrl,
                    okHttpClient = okHttpClient,
                ),
        )
  }

  /**
   * Creates a counter.
   *
   * @return The new counter's id.
   */
  suspend fun createCounter(): CounterId =
      callRaw(
          operation = "createCounter",
          call = { rawCounterClient.createCounter() },
          onSuccess = { response ->
            when (response.statusCode) {
              HttpURLConnection.HTTP_OK ->
                  CounterId(value = response.requireData(operation = "createCounter").counterId)

              else -> null
            }
          },
          // The contract gives this operation no 4xx at all.
          onClientError = { null },
      )

  /**
   * Lists every counter.
   *
   * @return The counters, oldest first.
   */
  suspend fun listCounters(): List<Counter> =
      callRaw(
          operation = "listCounters",
          call = { rawCounterClient.listCounters() },
          onSuccess = { response ->
            when (response.statusCode) {
              HttpURLConnection.HTTP_OK ->
                  response.requireData(operation = "listCounters").counters.map { rawCounter ->
                    Counter(id = CounterId(value = rawCounter.counterId), count = rawCounter.count)
                  }

              else -> null
            }
          },
          // The contract gives this operation no 4xx at all.
          onClientError = { null },
      )

  /** Deletes the counter [counterId] identifies. */
  suspend fun deleteCounter(counterId: CounterId): ApiTypes.DeleteCounterResponse =
      callRaw(
          operation = "deleteCounter",
          call = { rawCounterClient.deleteCounter(counterId = counterId.value) },
          onSuccess = { response ->
            when (response.statusCode) {
              HttpURLConnection.HTTP_NO_CONTENT -> ApiTypes.DeleteCounterResponse.Deleted

              else -> null
            }
          },
          onClientError = { exception ->
            when (exception.statusCode) {
              HttpURLConnection.HTTP_NOT_FOUND -> ApiTypes.DeleteCounterResponse.NotFound

              else -> null
            }
          },
      )

  /** Reads the current count of the counter [counterId] identifies. */
  suspend fun getCount(counterId: CounterId): ApiTypes.GetCountResponse =
      callRaw(
          operation = "getCount",
          call = { rawCounterClient.getCount(counterId = counterId.value) },
          onSuccess = { response ->
            when (response.statusCode) {
              HttpURLConnection.HTTP_OK ->
                  ApiTypes.GetCountResponse.Retrieved(
                      currentCount = response.requireData(operation = "getCount").count,
                  )

              else -> null
            }
          },
          onClientError = { exception ->
            when (exception.statusCode) {
              HttpURLConnection.HTTP_NOT_FOUND -> ApiTypes.GetCountResponse.NotFound

              else -> null
            }
          },
      )

  /** Increments the counter [counterId] identifies. */
  suspend fun incrementCount(counterId: CounterId): ApiTypes.IncrementCountResponse =
      callRaw(
          operation = "incrementCount",
          call = { rawCounterClient.incrementCount(counterId = counterId.value) },
          onSuccess = { response ->
            when (response.statusCode) {
              HttpURLConnection.HTTP_OK ->
                  ApiTypes.IncrementCountResponse.Incremented(
                      newCount = response.requireData(operation = "incrementCount").count,
                  )

              else -> null
            }
          },
          onClientError = { exception ->
            when (exception.statusCode) {
              HttpURLConnection.HTTP_NOT_FOUND -> ApiTypes.IncrementCountResponse.NotFound

              else -> null
            }
          },
      )

  /** Decrements the counter [counterId] identifies. */
  suspend fun decrementCount(counterId: CounterId): ApiTypes.DecrementCountResponse =
      callRaw(
          operation = "decrementCount",
          call = { rawCounterClient.decrementCount(counterId = counterId.value) },
          onSuccess = { response ->
            when (response.statusCode) {
              HttpURLConnection.HTTP_OK ->
                  ApiTypes.DecrementCountResponse.Decremented(
                      newCount = response.requireData(operation = "decrementCount").count,
                  )

              else -> null
            }
          },
          onClientError = { exception ->
            when (exception.statusCode) {
              HttpURLConnection.HTTP_NOT_FOUND -> ApiTypes.DecrementCountResponse.NotFound

              else -> null
            }
          },
      )

  /**
   * Calls the generated client, on a thread that may block, and classifies what came back.
   *
   * [onSuccess] and [onClientError] answer `null` for a status this operation was not promised — a
   * 2xx included. A server that says 207 where the contract says 200 is not speaking the contract,
   * and a client that shrugs and assumes 200 was meant is a client that will one day report a count
   * nobody ever sent it.
   *
   * Everything the errors deliberately do not carry is written to the log here, because here is
   * where it stops existing. A caller that retries and then succeeds would otherwise leave no
   * record that anything went wrong at all.
   */
  @Suppress("ThrowsCount")
  private suspend fun <RawResultT : Any, ResultT : Any> callRaw(
      operation: String,
      call: () -> ApiResponse<RawResultT>,
      onSuccess: (ApiResponse<RawResultT>) -> ResultT?,
      onClientError: (ApiClientException) -> ResultT?,
  ): ResultT =
      withContext(Dispatchers.IO) {
        try {
          val response = call()

          onSuccess(response)
              ?: run {
                logger.error(
                    "{}: the server answered {}, which this operation was not promised",
                    operation,
                    response.statusCode,
                )

                throw IncompatibilityError
              }
        } catch (exception: JacksonException) {
          // Before `IOException`, which this extends: a body that cannot be read is the server
          // failing to speak the contract, not the network failing to carry it.
          logger.error("{}: the response body could not be read", operation, exception)

          throw IncompatibilityError
        } catch (exception: IOException) {
          logger.warn("{}: the call did not complete over the network", operation, exception)

          throw NetworkError
        } catch (exception: ApiServerException) {
          logger.error("{}: the server failed the request", operation, exception)

          throw InternalServerError
        } catch (exception: ApiClientException) {
          onClientError(exception)
              ?: run {
                logger.error("{}: the server rejected the request", operation, exception)

                throw IncompatibilityError
              }
        } catch (exception: ApiException) {
          // A redirect, or a status the generated client has no class for.
          logger.error("{}: the server answered unusably", operation, exception)

          throw IncompatibilityError
        }
      }

  /** A response with nothing in it, where the contract promises a body. */
  private fun <RawResultT> ApiResponse<RawResultT>.requireData(operation: String): RawResultT =
      when (val retrievedData = data) {
        null -> {
          logger.error("{}: the server answered {} with no body", operation, statusCode)

          throw IncompatibilityError
        }

        else -> retrievedData
      }
}
