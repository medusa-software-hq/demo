package software.medusa.demo.api.server

import io.micronaut.http.HttpResponse
import io.micronaut.http.annotation.Controller
import software.medusa.demo.api.ApiTypes
import software.medusa.demo.api.raw.controllers.RawTodoController
import software.medusa.demo.api.raw.models.RawTodo
import software.medusa.demo.api.raw.models.RawTodoCreatedReply
import software.medusa.demo.api.raw.models.RawTodoCreation
import software.medusa.demo.api.raw.models.RawTodoDoneUpdate
import software.medusa.demo.api.raw.models.RawTodoListReply
import software.medusa.demo.core.TodoId

/**
 * Mediates between the [apiHandler] and the [RawTodoController] HTTP-based interface, on behalf of
 * whoever the request names as its caller.
 *
 * The caller is read first thing in each route, before the handler is asked anything: the contract
 * does not carry it, so it comes from the request, and taking it before anything suspends keeps it
 * from depending on how far the request's context follows a coroutine.
 */
@Controller
class ProperRawTodoController(
    private val apiHandler: ApiHandler,
) : RawTodoController {
  override suspend fun listTodos(): HttpResponse<RawTodoListReply> {
    val caller = currentCaller()

    return HttpResponse.ok(
        RawTodoListReply(
            todos =
                apiHandler.handleListTodos(caller = caller).map { todo ->
                  RawTodo(todoId = todo.id.value, title = todo.title, done = todo.done)
                },
        ),
    )
  }

  override suspend fun createTodo(
      rawTodoCreation: RawTodoCreation
  ): HttpResponse<RawTodoCreatedReply> {
    val caller = currentCaller()

    return when (
        val response = apiHandler.handleCreateTodo(caller = caller, title = rawTodoCreation.title)
    ) {
      is ApiTypes.CreateTodoResponse.Created ->
          HttpResponse.ok(RawTodoCreatedReply(todoId = response.todoId.value))
      ApiTypes.CreateTodoResponse.BlankTitle -> HttpResponse.badRequest()
    }
  }

  override suspend fun deleteTodo(todoId: String): HttpResponse<Unit> {
    val caller = currentCaller()

    return when (apiHandler.handleDeleteTodo(caller = caller, todoId = TodoId(todoId))) {
      ApiTypes.DeleteTodoResponse.Deleted -> HttpResponse.noContent()
      ApiTypes.DeleteTodoResponse.NotFound -> HttpResponse.notFound()
    }
  }

  override suspend fun setTodoDone(
      rawTodoDoneUpdate: RawTodoDoneUpdate,
      todoId: String,
  ): HttpResponse<Unit> {
    val caller = currentCaller()

    return when (
        apiHandler.handleSetTodoDone(
            caller = caller,
            todoId = TodoId(todoId),
            done = rawTodoDoneUpdate.done,
        )
    ) {
      ApiTypes.SetTodoDoneResponse.Updated -> HttpResponse.noContent()
      ApiTypes.SetTodoDoneResponse.NotFound -> HttpResponse.notFound()
    }
  }
}
