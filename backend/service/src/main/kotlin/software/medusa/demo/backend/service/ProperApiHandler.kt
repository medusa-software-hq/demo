package software.medusa.demo.backend.service

import software.medusa.demo.api.ApiTypes
import software.medusa.demo.api.server.ApiHandler
import software.medusa.demo.api.server.CallerInfo
import software.medusa.demo.backend.storage.CounterStore
import software.medusa.demo.backend.storage.TodoStore
import software.medusa.demo.core.Counter
import software.medusa.demo.core.CounterId
import software.medusa.demo.core.Todo
import software.medusa.demo.core.TodoId

/** Answers the API out of a [CounterStore] and a [TodoStore]. */
class ProperApiHandler(
    private val counterStore: CounterStore,
    private val todoStore: TodoStore,
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

  context(caller: CallerInfo)
  override suspend fun handleListTodos(): List<Todo> = todoStore.listFor(owner = caller.userId)

  context(caller: CallerInfo)
  override suspend fun handleCreateTodo(title: String): ApiTypes.CreateTodoResponse =
      when {
        // A todo that says nothing is not something anybody means to do.
        title.isBlank() -> ApiTypes.CreateTodoResponse.BlankTitle
        else ->
            ApiTypes.CreateTodoResponse.Created(
                todoStore.create(owner = caller.userId, title = title),
            )
      }

  context(caller: CallerInfo)
  override suspend fun handleSetTodoDone(
      todoId: TodoId,
      done: Boolean,
  ): ApiTypes.SetTodoDoneResponse =
      when (todoStore.setDone(owner = caller.userId, todoId = todoId, done = done)) {
        true -> ApiTypes.SetTodoDoneResponse.Updated
        false -> ApiTypes.SetTodoDoneResponse.NotFound
      }

  context(caller: CallerInfo)
  override suspend fun handleDeleteTodo(todoId: TodoId): ApiTypes.DeleteTodoResponse =
      when (todoStore.delete(owner = caller.userId, todoId = todoId)) {
        true -> ApiTypes.DeleteTodoResponse.Deleted
        false -> ApiTypes.DeleteTodoResponse.NotFound
      }
}
