package software.medusa.demo.api.server

import software.medusa.demo.api.ApiTypes
import software.medusa.demo.core.Counter
import software.medusa.demo.core.CounterId
import software.medusa.demo.core.Todo
import software.medusa.demo.core.TodoId
import software.medusa.demo.core.UserId

/**
 * What a backend implements to answer the API.
 *
 * The API in the product's own words: counter ids rather than strings, and an outcome per operation
 * rather than a status code. What that becomes on the wire is [ProperRawCounterController]'s
 * business, and an implementation of this never has to know.
 */
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

  /** Lists [caller]'s own todos, oldest first. */
  suspend fun handleListTodos(caller: UserId): List<Todo>

  /** Adds a todo titled [title] for [caller]. */
  suspend fun handleCreateTodo(caller: UserId, title: String): ApiTypes.CreateTodoResponse

  /** Marks [caller]'s todo [todoId] done, or not. */
  suspend fun handleSetTodoDone(
      caller: UserId,
      todoId: TodoId,
      done: Boolean,
  ): ApiTypes.SetTodoDoneResponse

  /** Deletes [caller]'s todo [todoId]. */
  suspend fun handleDeleteTodo(caller: UserId, todoId: TodoId): ApiTypes.DeleteTodoResponse
}
