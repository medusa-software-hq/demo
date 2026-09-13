package software.medusa.demo.backend.stack

/**
 * One database on a [LocalDatabaseCluster], by the URL the Service takes to reach it.
 *
 * Nothing to close: it lasts as long as its cluster does.
 */
class LocalDatabase(
    /** In the form the Service takes: credentials as query parameters. */
    val url: String,
)
