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
 */
@Controller
class ProperRawTodoController(
    private val apiHandler: ApiHandler,
) : RawTodoController {
  override suspend fun listTodos(): HttpResponse<RawTodoListReply> = withCurrentCaller {
    HttpResponse.ok(
        RawTodoListReply(
            todos =
                apiHandler.handleListTodos().map { todo ->
                  RawTodo(todoId = todo.id.value, title = todo.title, done = todo.done)
                },
        ),
    )
  }

  override suspend fun createTodo(
      rawTodoCreation: RawTodoCreation
  ): HttpResponse<RawTodoCreatedReply> = withCurrentCaller {
    when (val response = apiHandler.handleCreateTodo(title = rawTodoCreation.title)) {
      is ApiTypes.CreateTodoResponse.Created ->
          HttpResponse.ok(RawTodoCreatedReply(todoId = response.todoId.value))
      ApiTypes.CreateTodoResponse.BlankTitle -> HttpResponse.badRequest()
    }
  }

  override suspend fun deleteTodo(todoId: String): HttpResponse<Unit> = withCurrentCaller {
    when (apiHandler.handleDeleteTodo(todoId = TodoId(todoId))) {
      ApiTypes.DeleteTodoResponse.Deleted -> HttpResponse.noContent()
      ApiTypes.DeleteTodoResponse.NotFound -> HttpResponse.notFound()
    }
  }

  override suspend fun setTodoDone(
      rawTodoDoneUpdate: RawTodoDoneUpdate,
      todoId: String,
  ): HttpResponse<Unit> = withCurrentCaller {
    when (apiHandler.handleSetTodoDone(todoId = TodoId(todoId), done = rawTodoDoneUpdate.done)) {
      ApiTypes.SetTodoDoneResponse.Updated -> HttpResponse.noContent()
      ApiTypes.SetTodoDoneResponse.NotFound -> HttpResponse.notFound()
    }
  }
}
