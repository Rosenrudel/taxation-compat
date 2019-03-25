package de.randombyte.taxation.config

import ninja.leaping.configurate.objectmapping.Setting
import ninja.leaping.configurate.objectmapping.serialize.ConfigSerializable
import java.math.BigDecimal
import java.util.*

@ConfigSerializable
data class StatisticsDatabase(
        @Setting("players") val players: Map<UUID, Statistics> = emptyMap()
) {
    @ConfigSerializable
    class Statistics(
            @Setting("total-tax-payed") var totalTaxPayed: BigDecimal = BigDecimal.ZERO
    )
}