package software.medusa.demo.api.server

import io.micronaut.serde.annotation.SerdeImport
import software.medusa.demo.api.raw.models.RawCountReply
import software.medusa.demo.api.raw.models.RawCounterCreatedReply

/**
 * Serde is locked down by default, so each externally-defined model is registered here rather than
 * by editing generated code.
 */
@SerdeImport(RawCountReply::class)
@SerdeImport(RawCounterCreatedReply::class)
class ServerSerdeConfig
