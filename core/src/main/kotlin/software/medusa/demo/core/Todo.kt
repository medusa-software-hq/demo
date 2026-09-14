package software.medusa.demo.core

/**
 * Something one person means to do, and whether they have. Whose it is, is not its own business.
 */
data class Todo(val id: TodoId, val title: String, val done: Boolean)
