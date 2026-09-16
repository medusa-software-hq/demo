package software.medusa.demo.backend.storage

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import javax.sql.DataSource
import org.flywaydb.core.Flyway

/** The database the counters live in: how to reach it, and how to bring its schema up to date. */
data object Database {
  /**
   * Connections held open at once.
   *
   * One instance runs at a time and each request holds a connection for a single statement, so a
   * handful is plenty — and a handful stays well inside what Neon allows on a direct connection.
   */
  private const val poolSize = 4

  /** A pool of connections to the database at [url], which carries its own credentials. */
  fun connect(url: String): HikariDataSource =
      HikariDataSource(
          HikariConfig().apply {
            jdbcUrl = url
            // Named rather than discovered: DriverManager's ServiceLoader lookup is unreliable in a
            // packaged image, where it can report "No suitable driver" with pgjdbc on the
            // classpath.
            driverClassName = "org.postgresql.Driver"
            maximumPoolSize = poolSize
          },
      )

  /** Applies the migrations [dataSource] has not had yet, and answers how many that was. */
  fun migrate(dataSource: DataSource): Int =
      Flyway.configure().dataSource(dataSource).load().migrate().migrationsExecuted
}
