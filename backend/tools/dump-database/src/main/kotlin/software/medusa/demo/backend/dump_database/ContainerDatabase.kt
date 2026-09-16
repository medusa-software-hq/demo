package software.medusa.demo.backend.dump_database

import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.images.builder.Transferable

/**
 * A Postgres in a container, with the two tools only a real installation carries: `psql` to restore
 * a dump, and `pg_dump` to take one.
 *
 * A container rather than the embedded Postgres the local stack runs on, because embedded bundles
 * ship the server and nothing else. The tools run inside the container, so they always match the
 * server and nothing needs installing on the machine running them.
 */
class ContainerDatabase
private constructor(
    private val container: PostgreSQLContainer<*>,
) : AutoCloseable {
  companion object {
    /**
     * The major version Neon provisions, so what is dumped and restored here is what runs there.
     */
    private const val image = "postgres:18-alpine"

    /** Where a script is copied before `psql` runs it. */
    private const val scriptPath = "/tmp/script.sql"

    /**
     * The key `pg_dump` wraps a dump's `\restrict` and `\unrestrict` lines in.
     *
     * Those lines stop `psql` obeying meta-commands smuggled into the data, and by default the key
     * is random on every run, so a regenerated dump would differ even where the database had not.
     * The dumps here hold only rows this module seeded itself, so a fixed key gives nothing away.
     */
    private const val restrictKey = "migrationfixture"

    /** Starts a database nobody else has used. */
    fun start(): ContainerDatabase = ContainerDatabase(PostgreSQLContainer(image).apply { start() })
  }

  /**
   * The URL in the form the storage module takes: credentials as query parameters.
   *
   * The container's own URL may already carry parameters, so the first one is joined accordingly.
   */
  val url: String
    get() {
      val separator = if ("?" in container.jdbcUrl) "&" else "?"

      return "${container.jdbcUrl}${separator}user=${container.username}" +
          "&password=${container.password}"
    }

  /** Runs [sql] through `psql`, stopping at the first error rather than carrying on past it. */
  fun execute(sql: String) {
    // Bytes, with the encoding stated, so nothing outside ASCII is left to whatever a default
    // assumed.
    container.copyFileToContainer(Transferable.of(sql.toByteArray(Charsets.UTF_8)), scriptPath)

    run("psql", "-v", "ON_ERROR_STOP=1", "-q", "-o", "/dev/null", "-f", scriptPath)
  }

  /**
   * Everything in the database, as SQL that [execute] can restore: schema, rows, and where each
   * sequence had got to. Exactly what `pg_dump` wrote.
   */
  fun dump(): String =
      run("pg_dump", "--no-owner", "--no-privileges", "--restrict-key=$restrictKey")

  override fun close() {
    container.close()
  }

  /**
   * Runs one of the container's Postgres tools against this database, and answers its output.
   *
   * The spread copies a handful of strings to start a process in a container, which costs nothing
   * next to the process; `execInContainer` takes nothing but varargs.
   */
  @Suppress("SpreadOperator")
  private fun run(tool: String, vararg arguments: String): String {
    val command = listOf(tool, "-U", container.username, "-d", container.databaseName) + arguments

    val result = container.execInContainer(*command.toTypedArray())

    check(result.exitCode == 0) { "$tool failed (${result.exitCode}): ${result.stderr}" }

    return result.stdout
  }
}
