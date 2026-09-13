package software.medusa.demo.backend.service

import software.medusa.demo.api.ApiTypes
import software.medusa.demo.api.server.ApiHandler
import software.medusa.demo.backend.storage.CounterStore
import software.medusa.demo.core.Counter
import software.medusa.demo.core.CounterId

/** Answers the API out of a [CounterStore]. */
class ProperApiHandler(
    private val counterStore: CounterStore,
) : ApiHandler {
  override suspend fun handleCreateCounter(): CounterId = counterStore.create()

  override suspend fun handleListCounters(): List<Counter> = counterStore.listAll()

  override suspend fun handleDeleteCounter(counterId: CounterId): ApiTypes.DeleteCounterResponse =
      when (counterStore.delete(counterId = counterId)) {
        true -> ApiTypes.DeleteCounterResponse.Deleted
        false -> ApiTypes.DeleteCounterResponse.NotFound
      }

  override suspend fun handleGetCount(counterId: CounterId): ApiTypes.GetCountResponse =
      when (val currentCount = counterStore.getCurrent(counterId = counterId)) {
        null -> ApiTypes.GetCountResponse.NotFound
        else -> ApiTypes.GetCountResponse.Retrieved(currentCount = currentCount)
      }

  override suspend fun handleIncrementCount(counterId: CounterId): ApiTypes.IncrementCountResponse =
      when (val newCount = counterStore.increment(counterId = counterId)) {
        null -> ApiTypes.IncrementCountResponse.NotFound
        else -> ApiTypes.IncrementCountResponse.Incremented(newCount = newCount)
      }

  override suspend fun handleDecrementCount(counterId: CounterId): ApiTypes.DecrementCountResponse =
      when (val newCount = counterStore.decrement(counterId = counterId)) {
        null -> ApiTypes.DecrementCountResponse.NotFound
        else -> ApiTypes.DecrementCountResponse.Decremented(newCount = newCount)
      }
}
