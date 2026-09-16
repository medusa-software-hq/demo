package software.medusa.demo.backend.storage

import app.cash.sqldelight.driver.jdbc.asJdbcDriver
import java.util.UUID
import javax.sql.DataSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import software.medusa.demo.backend.storage.db.DemoDatabase
import software.medusa.demo.core.Counter
import software.medusa.demo.core.CounterId

/**
 * [CounterStore] kept in Postgres, in the `counters` table the migrations create.
 *
 * The queries are in `Counters.sq`, checked at build time against the schema the migrations
 * describe, so a column that does not exist or a type that does not fit fails the build rather than
 * a request. Each call runs on the IO dispatcher, since it waits on the network.
 */
class PostgresCounterStore(
    dataSource: DataSource,
) : CounterStore {
  private val queries = DemoDatabase(dataSource.asJdbcDriver()).countersQueries

  override suspend fun create(): CounterId = io {
    val counterId = CounterId(UUID.randomUUID().toString())

    queries.create(id = counterId.value, count = 0)

    counterId
  }

  override suspend fun listAll(): List<Counter> = io {
    queries.listAll { id, count -> Counter(id = CounterId(id), count = count) }.executeAsList()
  }

  override suspend fun delete(counterId: CounterId): Boolean = io {
    queries.delete(id = counterId.value).value > 0
  }

  override suspend fun getCurrent(counterId: CounterId): Long? = io {
    queries.getCurrent(id = counterId.value).executeAsOneOrNull()
  }

  override suspend fun increment(counterId: CounterId): Long? = adjust(counterId, by = 1)

  override suspend fun decrement(counterId: CounterId): Long? = adjust(counterId, by = -1)

  private suspend fun adjust(counterId: CounterId, by: Long): Long? = io {
    queries.adjust(by = by, id = counterId.value).executeAsOneOrNull()
  }

  private suspend fun <ResultT> io(block: () -> ResultT): ResultT =
      withContext(Dispatchers.IO) { block() }
}
