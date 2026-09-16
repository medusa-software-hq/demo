package software.medusa.demo.backend

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.runBlocking
import software.medusa.demo.api.ApiTypes
import software.medusa.demo.backend.stack.BackendStackStarter
import software.medusa.demo.backend.stack.SharedDatabaseCluster
import software.medusa.demo.backend.stack.SharedTemporalServer
import software.medusa.demo.backend.stack.TestCaller
import software.medusa.demo.core.Todo
import software.medusa.demo.core.TodoId

class TodoRoutesTest {
  /** Creates a todo titled [title] through [apiClient], failing the test if it is not created. */
  private suspend fun createTodo(
      apiClient: software.medusa.demo.api.client.ApiClient,
      title: String,
  ): TodoId =
      when (val response = assertApiCallSucceeds { apiClient.createTodo(title = title) }) {
        is ApiTypes.CreateTodoResponse.Created -> response.todoId
        ApiTypes.CreateTodoResponse.BlankTitle -> error("\"$title\" was refused as blank")
      }

  @Test
  fun `somebody who has added nothing has no todos`() = runBlocking {
    BackendStackStarter.start(SharedDatabaseCluster.shared, SharedTemporalServer.shared).use {
        stackHandle ->
      assertEquals(
          expected = emptyList(),
          actual = assertApiCallSucceeds { stackHandle.apiClientFor().listTodos() },
      )
    }
  }

  @Test
  fun `added todos are listed not done yet, oldest first`() = runBlocking {
    BackendStackStarter.start(SharedDatabaseCluster.shared, SharedTemporalServer.shared).use {
        stackHandle ->
      val apiClient = stackHandle.apiClientFor()
      val first = createTodo(apiClient, title = "Water the plants")
      val second = createTodo(apiClient, title = "Call the plumber")

      assertEquals(
          expected =
              listOf(
                  Todo(id = first, title = "Water the plants", done = false),
                  Todo(id = second, title = "Call the plumber", done = false),
              ),
          actual = assertApiCallSucceeds { apiClient.listTodos() },
      )
    }
  }

  @Test
  fun `a todo can be marked done and back, and it sticks`() = runBlocking {
    BackendStackStarter.start(SharedDatabaseCluster.shared, SharedTemporalServer.shared).use {
        stackHandle ->
      val apiClient = stackHandle.apiClientFor()
      val todoId = createTodo(apiClient, title = "Water the plants")

      assertEquals(
          expected = ApiTypes.SetTodoDoneResponse.Updated,
          actual = assertApiCallSucceeds { apiClient.setTodoDone(todoId = todoId, done = true) },
      )

      assertEquals(
          expected = listOf(Todo(id = todoId, title = "Water the plants", done = true)),
          actual = assertApiCallSucceeds { apiClient.listTodos() },
      )

      assertEquals(
          expected = ApiTypes.SetTodoDoneResponse.Updated,
          actual = assertApiCallSucceeds { apiClient.setTodoDone(todoId = todoId, done = false) },
      )

      assertEquals(
          expected = listOf(Todo(id = todoId, title = "Water the plants", done = false)),
          actual = assertApiCallSucceeds { apiClient.listTodos() },
      )
    }
  }

  @Test
  fun `a title that says nothing is refused, and nothing is added`() = runBlocking {
    BackendStackStarter.start(SharedDatabaseCluster.shared, SharedTemporalServer.shared).use {
        stackHandle ->
      val apiClient = stackHandle.apiClientFor()

      assertEquals(
          expected = ApiTypes.CreateTodoResponse.BlankTitle,
          actual = assertApiCallSucceeds { apiClient.createTodo(title = " \t ") },
      )

      assertEquals(expected = emptyList(), actual = assertApiCallSucceeds { apiClient.listTodos() })
    }
  }

  @Test
  fun `a deleted todo is gone`() = runBlocking {
    BackendStackStarter.start(SharedDatabaseCluster.shared, SharedTemporalServer.shared).use {
        stackHandle ->
      val apiClient = stackHandle.apiClientFor()
      val todoId = createTodo(apiClient, title = "Water the plants")

      assertEquals(
          expected = ApiTypes.DeleteTodoResponse.Deleted,
          actual = assertApiCallSucceeds { apiClient.deleteTodo(todoId = todoId) },
      )

      assertEquals(expected = emptyList(), actual = assertApiCallSucceeds { apiClient.listTodos() })

      assertEquals(
          expected = ApiTypes.DeleteTodoResponse.NotFound,
          actual = assertApiCallSucceeds { apiClient.deleteTodo(todoId = todoId) },
      )

      assertEquals(
          expected = ApiTypes.SetTodoDoneResponse.NotFound,
          actual = assertApiCallSucceeds { apiClient.setTodoDone(todoId = todoId, done = true) },
      )
    }
  }

  // The one the rest depend on: what is personal is only personal if somebody else cannot see it,
  // change it, or remove it — and cannot even tell it is there.
  @Test
  fun `somebody else's todos can be neither seen nor touched`() = runBlocking {
    BackendStackStarter.start(SharedDatabaseCluster.shared, SharedTemporalServer.shared).use {
        stackHandle ->
      val owner = stackHandle.apiClientFor(TestCaller.someone)
      val stranger = stackHandle.apiClientFor(TestCaller.someoneElse)
      val todoId = createTodo(owner, title = "Water the plants")

      assertEquals(expected = emptyList(), actual = assertApiCallSucceeds { stranger.listTodos() })

      assertEquals(
          expected = ApiTypes.SetTodoDoneResponse.NotFound,
          actual = assertApiCallSucceeds { stranger.setTodoDone(todoId = todoId, done = true) },
      )

      assertEquals(
          expected = ApiTypes.DeleteTodoResponse.NotFound,
          actual = assertApiCallSucceeds { stranger.deleteTodo(todoId = todoId) },
      )

      assertEquals(
          expected = listOf(Todo(id = todoId, title = "Water the plants", done = false)),
          actual = assertApiCallSucceeds { owner.listTodos() },
      )
    }
  }
}
