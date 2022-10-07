package de.randombyte.taxation.config

import ninja.leaping.configurate.objectmapping.Setting
import ninja.leaping.configurate.objectmapping.serialize.ConfigSerializable

@ConfigSerializable
class GeneralConfig(
    @Setting("session-duration", comment = "Time in Minutes") val sessionDuration: Int = 60 * 72,
    @Setting("connection-jdbc", comment = "Example: jdbc:mariadb://username:password@ip:port/database")
    val connectionJdbc: String = "",
    @Setting("currency") val currency: String = "economylite:coin",
    @Setting("virtual-tax-account") val taxAccount: String = "<put your virtual account in here>",
    @Setting("taxation-rates") val rates: Map<Int, Double> = emptyMap(),

    @Setting("session-end-commands", comment = "Commands that should be run when a session has stopped.")
    val endTriggerCommands: List<String> = emptyList()
)