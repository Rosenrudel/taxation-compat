package de.randombyte.taxation.commands

import de.randombyte.kosp.extensions.*
import de.randombyte.taxation.*
import de.randombyte.taxation.Taxation.Companion.DECIMAL_FORMATTER
import de.randombyte.taxation.config.TextsConfig.Placeholders.CURRENCY_SYMBOL
import de.randombyte.taxation.config.TextsConfig.Placeholders.REMAINING_DURATION
import de.randombyte.taxation.config.TextsConfig.Placeholders.SESSION_INCOME
import de.randombyte.taxation.config.TextsConfig.Placeholders.TAX_HISTORY_TOTAL
import de.randombyte.taxation.config.TextsConfig.Placeholders.TAX_PERCENTAGE
import de.randombyte.taxation.config.TextsConfig.Placeholders.TAX_TOTAL
import org.spongepowered.api.command.CommandException
import org.spongepowered.api.command.CommandResult
import org.spongepowered.api.command.CommandSource
import org.spongepowered.api.command.args.CommandContext
import org.spongepowered.api.command.spec.CommandExecutor
import org.spongepowered.api.entity.living.player.User
import java.time.Duration
import java.time.Instant

class SessionInfoCommand : CommandExecutor {
    override fun execute(src: CommandSource, args: CommandContext): CommandResult {
        var target = args.getOne<User>(Taxation.USER_ARG).orNull()
        if (target == null) {
            if (src !is User) throw CommandException("Must be executed by a player or with a player as an argument!".toText())
            target = src
        } else {
            src.sendMessage("Showing tax info for player ${target.name}.".yellow())
        }

        val session = Session()

        val rma = Taxation.INSTANCE.database!!.getRuntimeValue("next_tax_collection_at")
            ?.let { Duration.ofSeconds(it.toLong() - Instant.now().epochSecond) }
        val remainingDurationString = rma?.serialize(outputMillis = false)
        val income = session.calculateIncome(target.uniqueId)
        val percentage =
            if (target.hasPermission(Taxation.TAX_EXEMPT_PERMISSION)) 0.0 else session.calculateTaxPercentage(income.toDouble())
        val taxTotal = session.calculateTaxTotal(income.toDouble(), percentage)
        val taxTotalHistory = Taxation.INSTANCE.database!!.getPlayerStatistics(target.uniqueId)

        texts.info.forEach { line ->
            src.sendMessage(
                line
                    .replace(
                        (REMAINING_DURATION to remainingDurationString) as Pair<String, String>,
                        SESSION_INCOME to DECIMAL_FORMATTER.format(income),
                        TAX_PERCENTAGE to DECIMAL_FORMATTER.format(percentage),
                        TAX_TOTAL to DECIMAL_FORMATTER.format(taxTotal),
                        TAX_HISTORY_TOTAL to DECIMAL_FORMATTER.format(taxTotalHistory),
                        CURRENCY_SYMBOL to currency.symbol.serialize()
                    )
                    .tryReplacePlaceholders(source = target)
                    .deserialize()
            )
        }

        return CommandResult.success()
    }
}