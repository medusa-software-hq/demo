package software.medusa.demo.backend

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.fail
import kotlinx.coroutines.runBlocking
import software.medusa.demo.api.ApiTypes
import software.medusa.demo.api.client.ApiClient
import software.medusa.demo.backend.stack.BackendStackHandle
import software.medusa.demo.backend.stack.BackendStackStarter
import software.medusa.demo.backend.stack.LocalDatabase
import software.medusa.demo.core.Counter
import software.medusa.demo.core.CounterId

class CounterRoutesTest {
  private fun BackendStackHandle.apiClient(): ApiClient =
      ApiClient.connect(baseUrl = "http://localhost:${serviceHandle.port}")

  /**
   * Runs [call], failing the test rather than crashing it when the API does not answer.
   *
   * A service that cannot be reached is a red test, not a broken one: nothing is wrong with the
   * code under test, and the report should not say there is. None of these errors carry anything —
   * what happened is in the log, written where the client gave up on it.
   */
  private inline fun <ResponseT> assertApiCallSucceeds(call: () -> ResponseT): ResponseT =
      try {
        call()
      } catch (callError: ApiClient.CallError) {
        // Exhaustive, so a new kind of failure fails to compile here rather than arriving as a
        // crash nobody chose a shade of red for.
        val what =
            when (callError) {
              ApiClient.NetworkError -> "the network did not carry it"
              ApiClient.IncompatibilityError -> "the server did not speak the contract"
              ApiClient.InternalServerError -> "the server failed the request"
            }

        fail("Expected the API call to succeed, but $what")
      }

  @Test
  fun `a created counter starts at zero`() = runBlocking {
    BackendStackStarter.start().use { stackHandle ->
      val apiClient = stackHandle.apiClient()
      val counterId = assertApiCallSucceeds { apiClient.createCounter() }

      assertEquals(
          expected = ApiTypes.GetCountResponse.Retrieved(currentCount = 0),
          actual = assertApiCallSucceeds { apiClient.getCount(counterId = counterId) },
      )
    }
  }

  @Test
  fun `incrementing and decrementing move the counter, and the move sticks`() = runBlocking {
    BackendStackStarter.start().use { stackHandle ->
      val apiClient = stackHandle.apiClient()
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
    BackendStackStarter.start().use { stackHandle ->
      val apiClient = stackHandle.apiClient()
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
    BackendStackStarter.start().use { stackHandle ->
      val apiClient = stackHandle.apiClient()

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
    BackendStackStarter.start().use { stackHandle ->
      val apiClient = stackHandle.apiClient()
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
    BackendStackStarter.start().use { stackHandle ->
      val apiClient = stackHandle.apiClient()
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
    BackendStackStarter.start().use { stackHandle ->
      val apiClient = stackHandle.apiClient()
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
        BackendStackStarter.start().use { stackHandle ->
          assertApiCallSucceeds { stackHandle.apiClient().createCounter() }
        }

    BackendStackStarter.start().use { stackHandle ->
      assertEquals(
          expected = ApiTypes.GetCountResponse.NotFound,
          actual =
              assertApiCallSucceeds { stackHandle.apiClient().getCount(counterId = counterId) },
      )

      // And it does not merely deny knowing that one: it has nothing at all. Each stack gets a
      // database of its own, so no test here can pass on what another one left behind.
      assertEquals(
          expected = emptyList(),
          actual = assertApiCallSucceeds { stackHandle.apiClient().listCounters() },
      )
    }
  }

  @Test
  fun `counters outlive the service that made them`() = runBlocking {
    LocalDatabase.start().use { database ->
      val counterId =
          BackendStackStarter.start(database).use { stackHandle ->
            val apiClient = stackHandle.apiClient()
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
            actual = assertApiCallSucceeds { stackHandle.apiClient().listCounters() },
        )
      }
    }
  }
}
