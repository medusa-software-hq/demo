package software.medusa.demo.backend.service

import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import software.medusa.demo.backend.Constants
import software.medusa.demo.core.CounterId

/** In-memory [CounterStore]. */
class InMemoryCounterStore : CounterStore {
  private val counters = ConcurrentHashMap<CounterId, AtomicLong>()

  override fun create(): CounterId {
    val counterId = CounterId(UUID.randomUUID().toString())

    counters[counterId] = AtomicLong(Constants.initialCounterValue)

    return counterId
  }

  override fun delete(counterId: CounterId): Boolean = counters.remove(counterId) != null

  override fun getCurrent(counterId: CounterId): Long? = counters[counterId]?.get()

  override fun increment(counterId: CounterId): Long? = counters[counterId]?.incrementAndGet()

  override fun decrement(counterId: CounterId): Long? = counters[counterId]?.decrementAndGet()
}
