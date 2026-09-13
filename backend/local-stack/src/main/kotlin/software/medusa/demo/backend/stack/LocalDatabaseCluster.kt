package software.medusa.demo.backend.stack

import io.zonky.test.db.postgres.embedded.EmbeddedPostgres
import java.util.concurrent.atomic.AtomicInteger

/**
 * A Postgres of one's own: real binaries, the major version Neon provisions, and no Docker daemon
 * in the way.
 *
 * One cluster holds many databases. Starting it costs about a second and creating a database in it
 * costs milliseconds, so whoever stands up several stacks wants one of these and several
 * [createDatabase] calls.
 *
 * Closing it ends the cluster and everything in it. There is no shutdown hook: whoever started it
 * knows when it is done. Where nobody does — a test suite, which has no `main` — that decision is
 * taken there, on top of this.
 */
class LocalDatabaseCluster
private constructor(
    private val cluster: EmbeddedPostgres,
) : AutoCloseable {
  private val databaseCounter = AtomicInteger()

  /** A fresh, empty database on this cluster. Migrating it is the caller's business. */
  fun createDatabase(): LocalDatabase {
    val databaseName = "demo_${databaseCounter.incrementAndGet()}"

    cluster.postgresDatabase.connection.use { connection ->
      connection.createStatement().use { statement ->
        // The name is ours rather than anyone's input, and an identifier cannot be a bind
        // parameter.
        statement.execute("CREATE DATABASE $databaseName")
      }
    }

    return LocalDatabase(
        url =
            "jdbc:postgresql://localhost:${cluster.port}/$databaseName" +
                "?user=$clusterUser&password=$clusterUser",
    )
  }

  override fun close() {
    cluster.close()
  }

  companion object {
    /** The superuser an embedded cluster comes up with; it trusts local connections. */
    private const val clusterUser = "postgres"

    /** Brings a cluster up. About a second, so start one and keep it. */
    fun start(): LocalDatabaseCluster = LocalDatabaseCluster(cluster = EmbeddedPostgres.start())
  }
}
