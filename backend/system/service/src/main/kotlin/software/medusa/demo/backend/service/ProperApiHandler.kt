package software.medusa.demo.backend.service

import software.medusa.demo.api.ApiTypes
import software.medusa.demo.api.server.ApiHandler
import software.medusa.demo.api.server.CallerInfo
import software.medusa.demo.backend.storage.CounterStore
import software.medusa.demo.backend.storage.TodoStore
import software.medusa.demo.backend.storage.WorkRunStore
import software.medusa.demo.core.Counter
import software.medusa.demo.core.CounterId
import software.medusa.demo.core.Todo
import software.medusa.demo.core.TodoId

/** Answers the API out of its stores, starting work through a [WorkLauncher] where there is one. */
// One function per operation, as `ApiHandler` has.
@Suppress("TooManyFunctions")
class ProperApiHandler(
    private val counterStore: CounterStore,
    private val todoStore: TodoStore,
    private val workRunStore: WorkRunStore,

    /**
     * How runs are started. Absent where work is disabled, which is all this needs to know of it.
     */
    private val workLauncher: WorkLauncher?,
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

  context(caller: CallerInfo)
  override suspend fun handleGetWork(): ApiTypes.WorkOverview =
      ApiTypes.WorkOverview(
          enabled = workLauncher != null,
          // Read even where work is disabled: runs recorded while it was enabled are still the
          // caller's to see.
          runs = workRunStore.listFor(owner = caller.userId),
      )

  context(caller: CallerInfo)
  override suspend fun handleStartWork(): ApiTypes.StartWorkResponse {
    val launcher = workLauncher ?: return ApiTypes.StartWorkResponse.Disabled

    // Recorded before it is started, so the worker never reports progress on a run nobody can see.
    val runId = workRunStore.create(owner = caller.userId, stepsTotal = launcher.stepsPerRun)

    // And forgotten again if it could not be started, rather than left looking as though it might
    // still begin.
    runCatching { launcher.launch(runId) }.onFailure { workRunStore.delete(runId) }.getOrThrow()

    return ApiTypes.StartWorkResponse.Started(runId = runId)
  }
}
