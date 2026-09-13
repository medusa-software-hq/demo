package software.medusa.demo.backend.stack

import org.testcontainers.containers.PostgreSQLContainer

/**
 * A throwaway Postgres, and the URL the Service takes to reach it.
 *
 * Its own type rather than a detail of [BackendStackStarter], so a database can outlive the stack
 * that used it — the only way to show that what one stack wrote is still there for the next.
 */
class LocalDatabase
private constructor(
    private val container: PostgreSQLContainer<*>,
) : AutoCloseable {
  companion object {
    /** The major version Neon provisions, so the SQL tested here is the SQL that runs there. */
    private const val image = "postgres:18-alpine"

    /** Starts a database nobody else has used. */
    fun start(): LocalDatabase = LocalDatabase(PostgreSQLContainer(image).apply { start() })
  }

  /**
   * The URL in the form the Service takes: credentials as query parameters.
   *
   * The container's own URL may already carry parameters, so the first one is joined accordingly.
   */
  val url: String
    get() {
      val separator = if ("?" in container.jdbcUrl) "&" else "?"

      return "${container.jdbcUrl}${separator}user=${container.username}" +
          "&password=${container.password}"
    }

  override fun close() {
    container.close()
  }
}
