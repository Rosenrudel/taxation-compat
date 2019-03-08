package de.randombyte.taxation.config

import de.randombyte.taxation.config.TextsConfig.Placeholders.CURRENCY_SYMBOL
import de.randombyte.taxation.config.TextsConfig.Placeholders.REMAINING_DURATION
import de.randombyte.taxation.config.TextsConfig.Placeholders.SESSION_DURATION
import de.randombyte.taxation.config.TextsConfig.Placeholders.SESSION_INCOME
import de.randombyte.taxation.config.TextsConfig.Placeholders.TAX_PERCENTAGE
import de.randombyte.taxation.config.TextsConfig.Placeholders.TAX_TOTAL
import ninja.leaping.configurate.objectmapping.Setting
import ninja.leaping.configurate.objectmapping.serialize.ConfigSerializable

@ConfigSerializable
class TextsConfig(
        @Setting("taxed") val taxed: String =
                "&eYou have been taxed $TAX_PERCENTAGE% totalling $TAX_TOTAL $CURRENCY_SYMBOL. You made a total of $SESSION_INCOME $CURRENCY_SYMBOL over $SESSION_DURATION.",
        @Setting("info") val info: List<String> = listOf(
                "&2==== Tax info ====",
                "Remaining session time: $REMAINING_DURATION",
                "Income for this session: $SESSION_INCOME $CURRENCY_SYMBOL",
                "Current tax rate: $TAX_PERCENTAGE%",
                "Current tax due: $TAX_TOTAL $CURRENCY_SYMBOL"
        ),
        @Setting("broadcast") val broadcast: String = "&eThe tax session has ended and a total of $TAX_TOTAL $CURRENCY_SYMBOL has been collected!"
) {
    object Placeholders {
        val TAX_PERCENTAGE = "tax_percentage".asPlaceholder
        val TAX_TOTAL = "tax_total".asPlaceholder
        val SESSION_INCOME = "session_income".asPlaceholder
        val SESSION_DURATION = "session_duration".asPlaceholder
        val REMAINING_DURATION = "remaining_duration".asPlaceholder
        val CURRENCY_SYMBOL = "currency_symbol".asPlaceholder

        private val String.asPlaceholder get() = "%$this%"
    }
}