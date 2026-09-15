package software.medusa.demo.backend.stack

/**
 * The one Temporal dev server a test JVM runs.
 *
 * The same bargain as [SharedDatabaseCluster]: started on first use, and never closed, since nobody
 * knows when the last test has run. The server stops itself when the JVM ends. Every stack still
 * gets a task queue of its own, so no stack's worker picks up another stack's work.
 */
data object SharedTemporalServer {
  val shared: LocalTemporalServer by lazy { LocalTemporalServer.start(withUi = false) }
}
