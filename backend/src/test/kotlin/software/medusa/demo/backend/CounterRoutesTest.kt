package software.medusa.demo.backend

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.runBlocking
import software.medusa.demo.api.ApiTypes
import software.medusa.demo.backend.stack.BackendStackStarter
import software.medusa.demo.backend.stack.SharedDatabaseCluster
import software.medusa.demo.core.Counter
import software.medusa.demo.core.CounterId

class CounterRoutesTest {
  @Test
  fun `a created counter starts at zero`() = runBlocking {
    BackendStackStarter.start(SharedDatabaseCluster.shared).use { stackHandle ->
      val apiClient = stackHandle.apiClientFor()
      val counterId = assertApiCallSucceeds { apiClient.createCounter() }

      assertEquals(
          expected = ApiTypes.GetCountResponse.Retrieved(currentCount = 0),
          actual = assertApiCallSucceeds { apiClient.getCount(counterId = counterId) },
      )
    }
  }

  @Test
  fun `incrementing and decrementing move the counter, and the move sticks`() = runBlocking {
    BackendStackStarter.start(SharedDatabaseCluster.shared).use { stackHandle ->
      val apiClient = stackHandle.apiClientFor()
      val counterId = assertApiCallSucceeds { apiClient.createCounter() }

      assertEquals(
          expected = ApiTypes.IncrementCountResponse.Incremented(newCount = 1),
          actual = assertApiCallSucceeds { apiClient.incrementCount(counterId = counterId) },
      )

      assertEquals(
          expected = ApiTypes.IncrementCountResponse.Incremented(newCount = 2),
          actual = assertApiCallSucceeds { apiClient.incrementCount(counterId = counterId) },
      )

      assertEquals(
          expected = ApiTypes.DecrementCountResponse.Decremented(newCount = 1),
          actual = assertApiCallSucceeds { apiClient.decrementCount(counterId = counterId) },
      )

      // A separate request, so the count survived the one that changed it rather than only being
      // reported back by it.
      assertEquals(
          expected = ApiTypes.GetCountResponse.Retrieved(currentCount = 1),
          actual = assertApiCallSucceeds { apiClient.getCount(counterId = counterId) },
      )
    }
  }

  @Test
  fun `one counter moving leaves the others where they were`() = runBlocking {
    BackendStackStarter.start(SharedDatabaseCluster.shared).use { stackHandle ->
      val apiClient = stackHandle.apiClientFor()
      val movedCounterId = assertApiCallSucceeds { apiClient.createCounter() }
      val untouchedCounterId = assertApiCallSucceeds { apiClient.createCounter() }

      assertApiCallSucceeds { apiClient.incrementCount(counterId = movedCounterId) }

      assertEquals(
          expected = ApiTypes.GetCountResponse.Retrieved(currentCount = 1),
          actual = assertApiCallSucceeds { apiClient.getCount(counterId = movedCounterId) },
      )

      assertEquals(
          expected = ApiTypes.GetCountResponse.Retrieved(currentCount = 0),
          actual = assertApiCallSucceeds { apiClient.getCount(counterId = untouchedCounterId) },
      )
    }
  }

  @Test
  fun `listing answers the counters that were created, oldest first`() = runBlocking {
    BackendStackStarter.start(SharedDatabaseCluster.shared).use { stackHandle ->
      val apiClient = stackHandle.apiClientFor()

      assertEquals(
          expected = emptyList(),
          actual = assertApiCallSucceeds { apiClient.listCounters() },
      )

      val firstCounterId = assertApiCallSucceeds { apiClient.createCounter() }
      val secondCounterId = assertApiCallSucceeds { apiClient.createCounter() }

      assertApiCallSucceeds { apiClient.incrementCount(counterId = secondCounterId) }

      // The order is by creation, so the one that moved has not moved in the list.
      assertEquals(
          expected =
              listOf(
                  Counter(id = firstCounterId, count = 0),
                  Counter(id = secondCounterId, count = 1),
              ),
          actual = assertApiCallSucceeds { apiClient.listCounters() },
      )
    }
  }

  @Test
  fun `listing stops reporting a counter that was deleted`() = runBlocking {
    BackendStackStarter.start(SharedDatabaseCluster.shared).use { stackHandle ->
      val apiClient = stackHandle.apiClientFor()
      val keptCounterId = assertApiCallSucceeds { apiClient.createCounter() }
      val doomedCounterId = assertApiCallSucceeds { apiClient.createCounter() }

      assertApiCallSucceeds { apiClient.deleteCounter(counterId = doomedCounterId) }

      assertEquals(
          expected = listOf(Counter(id = keptCounterId, count = 0)),
          actual = assertApiCallSucceeds { apiClient.listCounters() },
      )
    }
  }

  @Test
  fun `a deleted counter is gone`() = runBlocking {
    BackendStackStarter.start(SharedDatabaseCluster.shared).use { stackHandle ->
      val apiClient = stackHandle.apiClientFor()
      val counterId = assertApiCallSucceeds { apiClient.createCounter() }

      assertEquals(
          expected = ApiTypes.DeleteCounterResponse.Deleted,
          actual = assertApiCallSucceeds { apiClient.deleteCounter(counterId = counterId) },
      )

      assertEquals(
          expected = ApiTypes.GetCountResponse.NotFound,
          actual = assertApiCallSucceeds { apiClient.getCount(counterId = counterId) },
      )

      assertEquals(
          expected = ApiTypes.DeleteCounterResponse.NotFound,
          actual = assertApiCallSucceeds { apiClient.deleteCounter(counterId = counterId) },
      )
    }
  }

  @Test
  fun `an id no counter has is not quietly created by using it`() = runBlocking {
    BackendStackStarter.start(SharedDatabaseCluster.shared).use { stackHandle ->
      val apiClient = stackHandle.apiClientFor()
      val strangerCounterId = CounterId(value = "no-such-counter")

      assertEquals(
          expected = ApiTypes.IncrementCountResponse.NotFound,
          actual =
              assertApiCallSucceeds { apiClient.incrementCount(counterId = strangerCounterId) },
      )

      assertEquals(
          expected = ApiTypes.GetCountResponse.NotFound,
          actual = assertApiCallSucceeds { apiClient.getCount(counterId = strangerCounterId) },
      )
    }
  }

  @Test
  fun `a fresh stack has none of the counters the last one had`() = runBlocking {
    val counterId =
        BackendStackStarter.start(SharedDatabaseCluster.shared).use { stackHandle ->
          assertApiCallSucceeds { stackHandle.apiClientFor().createCounter() }
        }

    BackendStackStarter.start(SharedDatabaseCluster.shared).use { stackHandle ->
      assertEquals(
          expected = ApiTypes.GetCountResponse.NotFound,
          actual =
              assertApiCallSucceeds { stackHandle.apiClientFor().getCount(counterId = counterId) },
      )

      // And it does not merely deny knowing that one: it has nothing at all. Each stack gets a
      // database of its own, so no test here can pass on what another one left behind.
      assertEquals(
          expected = emptyList(),
          actual = assertApiCallSucceeds { stackHandle.apiClientFor().listCounters() },
      )
    }
  }

  @Test
  fun `counters outlive the service that made them`() = runBlocking {
    SharedDatabaseCluster.shared.createDatabase().let { database ->
      val counterId =
          BackendStackStarter.start(database).use { stackHandle ->
            val apiClient = stackHandle.apiClientFor()
            val createdCounterId = assertApiCallSucceeds { apiClient.createCounter() }

            assertApiCallSucceeds { apiClient.incrementCount(counterId = createdCounterId) }

            createdCounterId
          }

      // A second service on the same database is what a restart, a new revision and a cold start
      // after scaling to zero all look like from the database's side. It also migrates a database
      // that is already up to date, which has to do nothing.
      BackendStackStarter.start(database).use { stackHandle ->
        assertEquals(
            expected = listOf(Counter(id = counterId, count = 1)),
            actual = assertApiCallSucceeds { stackHandle.apiClientFor().listCounters() },
        )
      }
    }
  }
}
