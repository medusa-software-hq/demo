package software.medusa.demo.backend.service

import java.sql.Connection
import java.util.UUID
import javax.sql.DataSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import software.medusa.demo.backend.Constants
import software.medusa.demo.core.Counter
import software.medusa.demo.core.CounterId

/**
 * [CounterStore] kept in Postgres, in the `counters` table the migrations create.
 *
 * Plain JDBC, each statement written where it is used. Every call borrows a connection for its one
 * statement and hands it straight back, on the IO dispatcher.
 */
class PostgresCounterStore(
    private val dataSource: DataSource,
) : CounterStore {
  override suspend fun create(): CounterId = withConnection { connection ->
    val counterId = CounterId(UUID.randomUUID().toString())

    connection.prepareStatement("INSERT INTO counters (id, count) VALUES (?, ?)").use { statement ->
      statement.setString(1, counterId.value)
      statement.setLong(2, Constants.initialCounterValue)
      statement.executeUpdate()
    }

    counterId
  }

  override suspend fun listAll(): List<Counter> = withConnection { connection ->
    connection.prepareStatement("SELECT id, count FROM counters ORDER BY ordinal").use { statement
      ->
      statement.executeQuery().use { rows ->
        buildList {
          while (rows.next()) {
            add(Counter(id = CounterId(rows.getString("id")), count = rows.getLong("count")))
          }
        }
      }
    }
  }

  override suspend fun delete(counterId: CounterId): Boolean = withConnection { connection ->
    connection.prepareStatement("DELETE FROM counters WHERE id = ?").use { statement ->
      statement.setString(1, counterId.value)
      statement.executeUpdate() > 0
    }
  }

  override suspend fun getCurrent(counterId: CounterId): Long? = withConnection { connection ->
    connection.prepareStatement("SELECT count FROM counters WHERE id = ?").use { statement ->
      statement.setString(1, counterId.value)
      statement.executeQuery().use { rows -> if (rows.next()) rows.getLong("count") else null }
    }
  }

  override suspend fun increment(counterId: CounterId): Long? = adjust(counterId, by = 1)

  override suspend fun decrement(counterId: CounterId): Long? = adjust(counterId, by = -1)

  /**
   * Moves the counter [counterId] identifies by [by], and answers where it landed.
   *
   * One statement, so it is atomic: reading the value and writing it back plus one would let two
   * requests read the same number and both write the same result. `RETURNING` hands back the new
   * value without a second query, and matching no row means there is no such counter — nothing is
   * created.
   */
  private suspend fun adjust(counterId: CounterId, by: Long): Long? = withConnection { connection ->
    connection
        .prepareStatement("UPDATE counters SET count = count + ? WHERE id = ? RETURNING count")
        .use { statement ->
          statement.setLong(1, by)
          statement.setString(2, counterId.value)
          statement.executeQuery().use { rows -> if (rows.next()) rows.getLong("count") else null }
        }
  }

  private suspend fun <ResultT> withConnection(block: (Connection) -> ResultT): ResultT =
      withContext(Dispatchers.IO) { dataSource.connection.use(block) }
}
