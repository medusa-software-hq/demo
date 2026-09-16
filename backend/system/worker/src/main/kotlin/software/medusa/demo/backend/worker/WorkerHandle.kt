package software.medusa.demo.backend.worker

/** Handle to a running worker. Closing it stops polling. */
interface WorkerHandle : AutoCloseable
