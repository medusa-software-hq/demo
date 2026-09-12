package software.medusa.demo.api.server

import com.fasterxml.jackson.annotation.JsonInclude
import io.micronaut.serde.annotation.SerdeImport
import software.medusa.demo.api.raw.models.RawCountReply
import software.medusa.demo.api.raw.models.RawCounter
import software.medusa.demo.api.raw.models.RawCounterCreatedReply
import software.medusa.demo.api.raw.models.RawCounterListReply

/**
 * Serde is locked down by default, so each externally-defined model is registered here rather than
 * by editing generated code.
 */
@SerdeImport(RawCountReply::class)
@SerdeImport(RawCounter::class)
@SerdeImport(RawCounterCreatedReply::class)
@SerdeImport(value = RawCounterListReply::class, mixin = AlwaysComplete::class)
class ServerSerdeConfig

/**
 * Send every property, including the ones that happen to be empty.
 *
 * Serde leaves an empty collection out altogether by default, so a reply carrying no counters goes
 * out as `{}` rather than `{"counters":[]}`. The contract marks the property required, so a client
 * is right to reject that — and the state it happens in is the one every environment starts in.
 */
@JsonInclude(JsonInclude.Include.ALWAYS) abstract class AlwaysComplete
