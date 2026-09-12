package software.medusa.demo.api.server

import io.micronaut.http.HttpResponse
import io.micronaut.http.annotation.Controller
import software.medusa.demo.api.ApiTypes
import software.medusa.demo.api.raw.controllers.RawCounterController
import software.medusa.demo.api.raw.models.RawCountReply
import software.medusa.demo.api.raw.models.RawCounter
import software.medusa.demo.api.raw.models.RawCounterCreatedReply
import software.medusa.demo.api.raw.models.RawCounterListReply
import software.medusa.demo.core.CounterId

/** Mediates between the [apiHandler] and the [RawCounterController] HTTP-based interface. */
@Controller
class ProperRawCounterController(
    private val apiHandler: ApiHandler,
) : RawCounterController {
  companion object {
    private fun replyWithCount(count: Long): HttpResponse<RawCountReply> =
        HttpResponse.ok(RawCountReply(count = count))
  }

  override suspend fun listCounters(): HttpResponse<RawCounterListReply> =
      HttpResponse.ok(
          RawCounterListReply(
              counters =
                  apiHandler.handleListCounters().map { counter ->
                    RawCounter(counterId = counter.id.value, count = counter.count)
                  },
          ),
      )

  override suspend fun createCounter(): HttpResponse<RawCounterCreatedReply> =
      HttpResponse.ok(
          RawCounterCreatedReply(counterId = apiHandler.handleCreateCounter().value),
      )

  override suspend fun deleteCounter(counterId: String): HttpResponse<Unit> =
      when (apiHandler.handleDeleteCounter(counterId = CounterId(counterId))) {
        ApiTypes.DeleteCounterResponse.Deleted -> HttpResponse.noContent()
        ApiTypes.DeleteCounterResponse.NotFound -> HttpResponse.notFound()
      }

  override suspend fun getCount(counterId: String): HttpResponse<RawCountReply> =
      when (val response = apiHandler.handleGetCount(counterId = CounterId(counterId))) {
        is ApiTypes.GetCountResponse.Retrieved -> replyWithCount(response.currentCount)
        ApiTypes.GetCountResponse.NotFound -> HttpResponse.notFound()
      }

  override suspend fun incrementCount(counterId: String): HttpResponse<RawCountReply> =
      when (val response = apiHandler.handleIncrementCount(counterId = CounterId(counterId))) {
        is ApiTypes.IncrementCountResponse.Incremented -> replyWithCount(response.newCount)
        ApiTypes.IncrementCountResponse.NotFound -> HttpResponse.notFound()
      }

  override suspend fun decrementCount(counterId: String): HttpResponse<RawCountReply> =
      when (val response = apiHandler.handleDecrementCount(counterId = CounterId(counterId))) {
        is ApiTypes.DecrementCountResponse.Decremented -> replyWithCount(response.newCount)
        ApiTypes.DecrementCountResponse.NotFound -> HttpResponse.notFound()
      }
}
