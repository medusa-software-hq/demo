package software.medusa.demo.api.server

import io.micronaut.http.HttpResponse
import io.micronaut.http.HttpStatus
import io.micronaut.http.annotation.Controller
import software.medusa.demo.api.ApiTypes
import software.medusa.demo.api.raw.controllers.RawWorkController
import software.medusa.demo.api.raw.models.RawWorkOverview
import software.medusa.demo.api.raw.models.RawWorkRun
import software.medusa.demo.api.raw.models.RawWorkRunStartedReply

/**
 * Mediates between the [apiHandler] and the [RawWorkController] HTTP-based interface, on behalf of
 * whoever the request names as its caller.
 */
@Controller
class ProperRawWorkController(
    private val apiHandler: ApiHandler,
) : RawWorkController {
  override suspend fun getWork(): HttpResponse<RawWorkOverview> = withCurrentCaller {
    val overview = apiHandler.handleGetWork()

    HttpResponse.ok(
        RawWorkOverview(
            enabled = overview.enabled,
            runs =
                overview.runs.map { run ->
                  RawWorkRun(
                      runId = run.id.value,
                      stepsDone = run.stepsDone,
                      stepsTotal = run.stepsTotal,
                      result = run.result,
                  )
                },
        ),
    )
  }

  override suspend fun startWork(): HttpResponse<RawWorkRunStartedReply> = withCurrentCaller {
    when (val response = apiHandler.handleStartWork()) {
      is ApiTypes.StartWorkResponse.Started ->
          HttpResponse.ok(RawWorkRunStartedReply(runId = response.runId.value))

      ApiTypes.StartWorkResponse.Disabled -> HttpResponse.status(HttpStatus.CONFLICT)
    }
  }
}
