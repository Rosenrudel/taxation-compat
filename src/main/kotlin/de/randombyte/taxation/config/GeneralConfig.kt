package de.randombyte.taxation.config

import ninja.leaping.configurate.objectmapping.Setting
import ninja.leaping.configurate.objectmapping.serialize.ConfigSerializable

@ConfigSerializable
class GeneralConfig (
        @Setting("session-duration") val sessionDuration: String = "72h",
        @Setting("currency") val currency: String = "economylite:coin",
        @Setting("virtual-tax-account") val taxAccount: String = "<put your virtual account in here>",
        @Setting("taxation-rates") val rates: Map<Int, Double> = mapOf(
                1_000 to 1.5,
                10_000 to 3.5,
                100_000 to 5.0
        )
)