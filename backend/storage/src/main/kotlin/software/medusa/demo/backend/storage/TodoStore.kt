package software.medusa.demo.backend.storage

import software.medusa.demo.core.Todo
import software.medusa.demo.core.TodoId
import software.medusa.demo.core.UserId

/**
 * Stores each person's own todos.
 *
 * Every operation is on behalf of an owner, and sees that owner's todos and nothing else. A todo
 * belonging to somebody else is indistinguishable from one that does not exist, which is the only
 * answer that tells a caller nothing about anybody else.
 *
 * Suspending, because the store is a database: a call that waits on the network must not do it on
 * the thread that serves every other request.
 */
interface TodoStore {
  /** @return Every todo [owner] has, oldest first. */
  suspend fun listFor(owner: UserId): List<Todo>

  /**
   * Adds a todo for [owner], not done yet.
   *
   * @return The new todo's id.
   */
  suspend fun create(owner: UserId, title: String): TodoId

  /**
   * Marks [owner]'s todo [todoId] done, or not.
   *
   * @return Whether [owner] has such a todo.
   */
  suspend fun setDone(owner: UserId, todoId: TodoId, done: Boolean): Boolean

  /**
   * Deletes [owner]'s todo [todoId].
   *
   * @return Whether [owner] had such a todo.
   */
  suspend fun delete(owner: UserId, todoId: TodoId): Boolean
}
