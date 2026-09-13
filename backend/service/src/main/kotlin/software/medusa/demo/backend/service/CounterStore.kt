package software.medusa.demo.backend.service

import software.medusa.demo.core.Counter
import software.medusa.demo.core.CounterId

/**
 * Stores counters, each one identified on its own.
 *
 * The read and write operations answer `null` for an id no counter has, rather than creating one: a
 * counter exists because someone created it, and a typo should not quietly become a counter.
 *
 * Suspending, because the store is a database: a call that waits on the network must not do it on
 * the thread that serves every other request.
 */
interface CounterStore {
  /**
   * Creates a counter.
   *
   * @return The new counter's id.
   */
  suspend fun create(): CounterId

  /**
   * @return Every counter, oldest first.
   *
   * Ordered, because the only caller renders them in a list, and a set that came back in a
   * different arrangement each time would move under the reader's cursor. The order is by creation
   * and not by value, so incrementing a counter does not make it jump.
   */
  suspend fun listAll(): List<Counter>

  /**
   * Deletes the counter [counterId] identifies.
   *
   * @return Whether there was such a counter.
   */
  suspend fun delete(counterId: CounterId): Boolean

  /**
   * @return The current value of the counter [counterId] identifies, or `null` if there is none.
   */
  suspend fun getCurrent(counterId: CounterId): Long?

  /**
   * Increments the counter [counterId] identifies.
   *
   * @return The incremented value, or `null` if there is no such counter.
   */
  suspend fun increment(counterId: CounterId): Long?

  /**
   * Decrements the counter [counterId] identifies.
   *
   * @return The decremented value, or `null` if there is no such counter.
   */
  suspend fun decrement(counterId: CounterId): Long?
}
