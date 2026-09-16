package software.medusa.demo.backend.work

import io.temporal.client.WorkflowClient
import io.temporal.client.WorkflowClientOptions
import io.temporal.serviceclient.WorkflowServiceStubs
import io.temporal.serviceclient.WorkflowServiceStubsOptions

/**
 * How to reach one Temporal namespace.
 *
 * A local dev server and Temporal Cloud differ only in [auth], so nothing that holds one of these
 * needs to know which it was given.
 */
data class TemporalConnection(
    /** The Temporal frontend's address, as `host:port`. */
    val target: String,

    /** The namespace workflows are started in and polled for. */
    val namespace: String,

    /** How this connection proves it may use [namespace]. */
    val auth: Auth,
) {
  /** How a connection proves it may use its namespace. */
  sealed interface Auth {
    /** Nothing, in plaintext: the local dev server, which trusts whoever can reach it. */
    data object None : Auth

    /**
     * An API key, over TLS: Temporal Cloud.
     *
     * Unused until an environment is handed a namespace and a key of its own — and then the only
     * part of this that has to change.
     */
    data class ApiKey(val key: String) : Auth {
      /** Without the key: a connection gets logged, and a credential must not be. */
      override fun toString(): String = "ApiKey(key=…)"
    }
  }

  /** Opens a connection to the frontend. Whoever opens it shuts it down. */
  fun openStubs(): WorkflowServiceStubs =
      WorkflowServiceStubs.newServiceStubs(
          WorkflowServiceStubsOptions.newBuilder()
              .setTarget(target)
              .apply {
                when (auth) {
                  Auth.None -> setEnableHttps(false)

                  is Auth.ApiKey -> {
                    setEnableHttps(true)
                    addApiKey { auth.key }
                  }
                }
              }
              .build(),
      )

  /** A client for [namespace], over [stubs]. */
  fun workflowClient(stubs: WorkflowServiceStubs): WorkflowClient =
      WorkflowClient.newInstance(
          stubs,
          WorkflowClientOptions.newBuilder().setNamespace(namespace).build(),
      )
}
