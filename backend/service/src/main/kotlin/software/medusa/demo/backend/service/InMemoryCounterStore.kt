package software.medusa.demo.backend.service

import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import software.medusa.demo.backend.Constants
import software.medusa.demo.core.Counter
import software.medusa.demo.core.CounterId

/** In-memory [CounterStore]. */
class InMemoryCounterStore : CounterStore {
  /**
   * A counter's value, and where it came in the order they were created.
   *
   * The sequence exists only so [listAll] can answer in that order. A map that remembered insertion
   * order would do the same thing, but every such map either takes a lock for reads or is not safe
   * to share — and the values here are adjusted far more often than they are listed, so the cheap
   * operation should stay the lock-free one.
   */
  private class Entry(val sequence: Long) {
    val value = AtomicLong(Constants.initialCounterValue)
  }

  private val counters = ConcurrentHashMap<CounterId, Entry>()

  private val sequences = AtomicLong()

  override fun create(): CounterId {
    val counterId = CounterId(UUID.randomUUID().toString())

    counters[counterId] = Entry(sequence = sequences.getAndIncrement())

    return counterId
  }

  override fun listAll(): List<Counter> =
      counters.entries
          .sortedBy { (_, entry) -> entry.sequence }
          .map { (counterId, entry) -> Counter(id = counterId, count = entry.value.get()) }

  override fun delete(counterId: CounterId): Boolean = counters.remove(counterId) != null

  override fun getCurrent(counterId: CounterId): Long? = counters[counterId]?.value?.get()

  override fun increment(counterId: CounterId): Long? =
      counters[counterId]?.value?.incrementAndGet()

  override fun decrement(counterId: CounterId): Long? =
      counters[counterId]?.value?.decrementAndGet()
}
