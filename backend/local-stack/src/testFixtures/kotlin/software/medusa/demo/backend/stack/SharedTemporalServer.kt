package software.medusa.demo.backend.stack

/**
 * The one Temporal dev server a test JVM runs.
 *
 * The same bargain as [SharedDatabaseCluster]: started on first use, ended by the JVM ending, since
 * nobody else knows when the last test has run. Every stack still gets a task queue of its own, so
 * no stack's worker picks up another stack's work.
 */
data object SharedTemporalServer {
  val shared: LocalTemporalServer by lazy {
    LocalTemporalServer.start(withUi = false).also { started ->
      Runtime.getRuntime().addShutdownHook(Thread { runCatching { started.close() } })
    }
  }
}
