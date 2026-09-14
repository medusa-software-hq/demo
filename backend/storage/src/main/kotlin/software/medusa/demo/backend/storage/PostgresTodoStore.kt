package software.medusa.demo.backend.storage

import app.cash.sqldelight.driver.jdbc.asJdbcDriver
import java.util.UUID
import javax.sql.DataSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import software.medusa.demo.backend.storage.db.DemoDatabase
import software.medusa.demo.core.Todo
import software.medusa.demo.core.TodoId
import software.medusa.demo.core.UserId

/**
 * [TodoStore] kept in Postgres, in the `todos` table the migrations create.
 *
 * The queries are in `Todos.sq`, each one naming the owner, so no query here can reach somebody
 * else's todo by forgetting to. Each call runs on the IO dispatcher, since it waits on the network.
 */
class PostgresTodoStore(
    dataSource: DataSource,
) : TodoStore {
  private val queries = DemoDatabase(dataSource.asJdbcDriver()).todosQueries

  override suspend fun listFor(owner: UserId): List<Todo> = io {
    queries
        .listForOwner(owner = owner.value) { id, title, done ->
          Todo(id = TodoId(id), title = title, done = done)
        }
        .executeAsList()
  }

  override suspend fun create(owner: UserId, title: String): TodoId = io {
    val todoId = TodoId(UUID.randomUUID().toString())

    queries.create(id = todoId.value, owner = owner.value, title = title)

    todoId
  }

  override suspend fun setDone(owner: UserId, todoId: TodoId, done: Boolean): Boolean = io {
    queries.setDone(done = done, id = todoId.value, owner = owner.value).executeAsOneOrNull() !=
        null
  }

  override suspend fun delete(owner: UserId, todoId: TodoId): Boolean = io {
    queries.delete(id = todoId.value, owner = owner.value).value > 0
  }

  private suspend fun <ResultT> io(block: () -> ResultT): ResultT =
      withContext(Dispatchers.IO) { block() }
}
