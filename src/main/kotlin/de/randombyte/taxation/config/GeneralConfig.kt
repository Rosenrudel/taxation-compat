package de.randombyte.taxation.config

import ninja.leaping.configurate.objectmapping.Setting
import ninja.leaping.configurate.objectmapping.serialize.ConfigSerializable

@ConfigSerializable
class GeneralConfig (
        @Setting("enable-metrics-messages", comment =
                "Since you are already editing configs, how about enabling metrics for at least this plugin? ;)\n" +
                "Go to the 'config/sponge/global.conf', scroll to the 'metrics' section and enable metrics.\n" +
                "Anonymous metrics data collection enables the developer to see how many people and servers are using this plugin.\n" +
                "Seeing that my plugin is being used is a big factor in motivating me to provide future support and updates.\n" +
                "If you really don't want to enable metrics and don't want to receive any messages anymore, you can disable this config option :("
        ) val enableMetricsMessages: Boolean = true,

        @Setting("session-duration") val sessionDuration: String = "72h",
        @Setting("currency") val currency: String = "economylite:coin",
        @Setting("virtual-tax-account") val taxAccount: String = "<put your virtual account in here>",
        @Setting("taxation-rates") val rates: Map<Int, Double> = mapOf(
                1_000 to 1.5,
                10_000 to 3.5,
                100_000 to 5.0
        ),

        @Setting("session-end-commands", comment = "Commands that should be run by when a session has stopped.")
        val endTriggerCommands: List<String> = emptyList()
)