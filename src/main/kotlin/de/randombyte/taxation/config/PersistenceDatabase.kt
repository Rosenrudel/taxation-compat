package de.randombyte.taxation.config

import ninja.leaping.configurate.objectmapping.Setting
import ninja.leaping.configurate.objectmapping.serialize.ConfigSerializable
import java.math.BigDecimal
import java.math.BigInteger
import java.util.*

@ConfigSerializable
data class PersistenceDatabase(
        // that's just for displaying text to the user, nothing is calculated with the old duration value
        @Setting("session-duration", comment = "Don't modify this file!") val sessionDuration: String? = null,
        @Setting("session-remaining-time") val sessionRemainingTime: String? = null,
        @Setting("session-start-player-balances") val previousBalances: Map<UUID, SerializedBigDecimal>? = null
) {
    @ConfigSerializable
    class SerializedBigDecimal(
            @Setting("big-integer-bytes") val bigIntegerBytes: List<Byte> = emptyList(),
            @Setting("big-decimal-scale") val bigDecimalScale: Int = 0
    ) {
        companion object {
            fun BigDecimal.toSerialized() = SerializedBigDecimal(unscaledValue().toByteArray().toList(), scale())
        }

        fun toBigDecimal() = BigDecimal(BigInteger(bigIntegerBytes.toByteArray()), bigDecimalScale)
    }
}