package software.medusa.demo.api

import software.medusa.demo.core.TodoId

/**
 * The answers each operation can give.
 *
 * A sealed type per operation, rather than a nullable reply or a thrown exception: an outcome the
 * contract describes is a value, and the compiler can then insist that a caller has a branch for
 * each one.
 */
data object ApiTypes {
  /** What deleting a counter can come to. */
  sealed interface DeleteCounterResponse {
    /** The counter was there, and is not any more. */
    data object Deleted : DeleteCounterResponse

    /** No counter has that id. */
    data object NotFound : DeleteCounterResponse
  }

  /** What reading a count can come to. */
  sealed interface GetCountResponse {
    /** The counter's current value. */
    data class Retrieved(val currentCount: Long) : GetCountResponse

    /** No counter has that id. */
    data object NotFound : GetCountResponse
  }

  /** What incrementing a counter can come to. */
  sealed interface IncrementCountResponse {
    /** The value after incrementing. */
    data class Incremented(val newCount: Long) : IncrementCountResponse

    /** No counter has that id. */
    data object NotFound : IncrementCountResponse
  }

  /** What decrementing a counter can come to. */
  sealed interface DecrementCountResponse {
    /** The value after decrementing. */
    data class Decremented(val newCount: Long) : DecrementCountResponse

    /** No counter has that id. */
    data object NotFound : DecrementCountResponse
  }

  /** What adding a todo can come to. */
  sealed interface CreateTodoResponse {
    /** The todo was added. */
    data class Created(val todoId: TodoId) : CreateTodoResponse

    /** The title says nothing, so there was nothing to add. */
    data object BlankTitle : CreateTodoResponse
  }

  /** What marking a todo done, or not, can come to. */
  sealed interface SetTodoDoneResponse {
    /** The todo is now as asked. */
    data object Updated : SetTodoDoneResponse

    /** The caller has no todo with that id. */
    data object NotFound : SetTodoDoneResponse
  }

  /** What deleting a todo can come to. */
  sealed interface DeleteTodoResponse {
    /** The todo was there, and is not any more. */
    data object Deleted : DeleteTodoResponse

    /** The caller has no todo with that id. */
    data object NotFound : DeleteTodoResponse
  }
}
