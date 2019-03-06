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
        @Setting("info") val info: String = "Remaining time of the session: $REMAINING_DURATION"
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