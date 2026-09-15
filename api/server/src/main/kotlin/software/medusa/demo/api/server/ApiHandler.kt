package software.medusa.demo.api.server

import software.medusa.demo.api.ApiTypes
import software.medusa.demo.core.Counter
import software.medusa.demo.core.CounterId
import software.medusa.demo.core.Todo
import software.medusa.demo.core.TodoId

/**
 * What a backend implements to answer the API.
 *
 * The API in the product's own words: counter ids rather than strings, and an outcome per operation
 * rather than a status code. What that becomes on the wire is [ProperRawCounterController]'s
 * business, and an implementation of this never has to know.
 */
// One function per operation in the contract, so its size is the contract's size: splitting it to
// stay
// under a count would only move the same operations somewhere a backend has to look for them.
@Suppress("TooManyFunctions")
interface ApiHandler {
  /**
   * Creates a counter.
   *
   * @return The new counter's id.
   */
  suspend fun handleCreateCounter(): CounterId

  /**
   * Lists every counter.
   *
   * No sealed type, because there is only one outcome: no counters at all is an empty list, not a
   * different answer.
   */
  suspend fun handleListCounters(): List<Counter>

  /** Deletes the counter [counterId] identifies. */
  suspend fun handleDeleteCounter(counterId: CounterId): ApiTypes.DeleteCounterResponse

  /** Reads the current count of the counter [counterId] identifies. */
  suspend fun handleGetCount(counterId: CounterId): ApiTypes.GetCountResponse

  /** Increments the counter [counterId] identifies. */
  suspend fun handleIncrementCount(counterId: CounterId): ApiTypes.IncrementCountResponse

  /** Decrements the counter [counterId] identifies. */
  suspend fun handleDecrementCount(counterId: CounterId): ApiTypes.DecrementCountResponse

  /** Lists the caller's own todos, oldest first. */
  context(caller: CallerInfo)
  suspend fun handleListTodos(): List<Todo>

  /** Adds a todo titled [title] for the caller. */
  context(caller: CallerInfo)
  suspend fun handleCreateTodo(title: String): ApiTypes.CreateTodoResponse

  /** Marks the caller's todo [todoId] done, or not. */
  context(caller: CallerInfo)
  suspend fun handleSetTodoDone(todoId: TodoId, done: Boolean): ApiTypes.SetTodoDoneResponse

  /** Deletes the caller's todo [todoId]. */
  context(caller: CallerInfo)
  suspend fun handleDeleteTodo(todoId: TodoId): ApiTypes.DeleteTodoResponse

  /** Whether work can be started here, and the caller's own runs, oldest first. */
  context(caller: CallerInfo)
  suspend fun handleGetWork(): ApiTypes.WorkOverview

  /** Starts a run of work for the caller. */
  context(caller: CallerInfo)
  suspend fun handleStartWork(): ApiTypes.StartWorkResponse
}
