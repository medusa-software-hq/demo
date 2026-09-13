package software.medusa.demo.backend.stack

import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.images.builder.Transferable

/**
 * A throwaway Postgres, and the URL the Service takes to reach it.
 *
 * Its own type rather than a detail of [BackendStackStarter], so a database can outlive the stack
 * that used it — the only way to show that what one stack wrote is still there for the next.
 *
 * `psql` and `pg_dump` run inside the container, so the tools always match the server and nothing
 * has to be installed on the machine running the tests.
 */
class LocalDatabase
private constructor(
    private val container: PostgreSQLContainer<*>,
) : AutoCloseable {
  companion object {
    /** The major version Neon provisions, so the SQL tested here is the SQL that runs there. */
    private const val image = "postgres:18-alpine"

    /** Where a script is copied before `psql` runs it. */
    private const val scriptPath = "/tmp/script.sql"

    /** Starts a database nobody else has used. */
    fun start(): LocalDatabase = LocalDatabase(PostgreSQLContainer(image).apply { start() })

    /**
     * Lines a dump carries that say nothing about the database.
     *
     * `\restrict` and `\unrestrict` wrap every dump in a key that is random each time, so two dumps
     * of the same database never match without dropping them. The rest is headers, session settings
     * and spacing.
     */
    private val dumpNoise = Regex("""^(\\(un)?restrict .*|--.*|SET .*|.*set_config.*|)$""")
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

  /** Runs [sql] through `psql`, stopping at the first error rather than carrying on past it. */
  fun execute(sql: String) {
    // Bytes, with the encoding stated: the String overload leans on a library this classpath does
    // not carry, and would leave the encoding of anything outside ASCII to whatever it assumed.
    container.copyFileToContainer(Transferable.of(sql.toByteArray(Charsets.UTF_8)), scriptPath)

    run("psql", "-v", "ON_ERROR_STOP=1", "-q", "-o", "/dev/null", "-f", scriptPath)
  }

  /**
   * Everything in the database, as SQL that [execute] can restore: schema, rows, and where each
   * sequence had got to.
   *
   * Without the `\restrict` lines, whose random key would make a regenerated dump differ on every
   * run even when nothing in the database has.
   */
  fun dump(): String =
      run("pg_dump", "--no-owner", "--no-privileges")
          .lineSequence()
          .filterNot { it.startsWith("\\restrict ") || it.startsWith("\\unrestrict ") }
          .joinToString("\n")

  /**
   * The schema alone, reduced to the statements that define it.
   *
   * Flyway's history table is left out: it is how Flyway keeps track, not part of what the app
   * reads, and a database built without Flyway does not have one.
   */
  fun dumpSchema(): String =
      run(
              "pg_dump",
              "--schema-only",
              "--no-owner",
              "--no-privileges",
              "--no-comments",
              "--exclude-table=public.flyway_schema_history",
          )
          .lineSequence()
          .filterNot { dumpNoise.matches(it) }
          .joinToString("\n")

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
