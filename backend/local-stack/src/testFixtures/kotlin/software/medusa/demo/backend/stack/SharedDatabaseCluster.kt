package software.medusa.demo.backend.stack

/**
 * The one embedded cluster a test JVM runs.
 *
 * [LocalDatabaseCluster] has no shutdown hook, because whoever starts one normally knows when it is
 * finished — `main` closes it. A test suite is the case where nobody does: there is no `main`, and
 * JUnit tells no one when the last test has run. So the decision is taken here, once: started on
 * first use, and ended by the JVM ending.
 *
 * One per JVM because starting Postgres costs about a second and a database in it milliseconds.
 * Every stack still gets a database of its own, so no test sees another's rows.
 */
data object SharedDatabaseCluster {
  val shared: LocalDatabaseCluster by lazy {
    LocalDatabaseCluster.start().also { started ->
      Runtime.getRuntime().addShutdownHook(Thread { runCatching { started.close() } })
    }
  }
}
