package de.randombyte.taxation.commands

import de.randombyte.kosp.extensions.*
import de.randombyte.taxation.Taxation
import de.randombyte.taxation.config.TextsConfig.Placeholders
import de.randombyte.taxation.currency
import de.randombyte.taxation.serialize
import de.randombyte.taxation.texts
import org.spongepowered.api.command.CommandException
import org.spongepowered.api.command.CommandResult
import org.spongepowered.api.command.CommandSource
import org.spongepowered.api.command.args.CommandContext
import org.spongepowered.api.command.spec.CommandExecutor
import org.spongepowered.api.entity.living.player.User

class SessionInfoCommand : CommandExecutor {
    override fun execute(src: CommandSource, args: CommandContext): CommandResult {
        if (Taxation.INSTANCE.session?.isRunning == true) {
            val session = Taxation.INSTANCE.session!!

            var target = args.getOne<User>(Taxation.USER_ARG).orNull()
            if (target == null) {
                if (src !is User) throw CommandException("Must be executed by a player or with a player as an argument!".toText())
                target = src
            } else {
                src.sendMessage("Showing tax info for player ${target.name}.".yellow())
            }

            val remainingDurationString = session.remainingDuration.serialize(outputMillis = false)
            val income = session.calculateIncome(target.uniqueId)
            val percentage = session.calculateTaxPercentage(income.toDouble())
            val taxTotal = session.calculateTaxTotal(income.toDouble(), percentage)

            texts.info.forEach { line ->
                src.sendMessage(line
                        .replace(
                                Placeholders.REMAINING_DURATION to remainingDurationString,
                                Placeholders.SESSION_INCOME to Taxation.DECIMAL_FORMATTER.format(income),
                                Placeholders.TAX_PERCENTAGE to Taxation.DECIMAL_FORMATTER.format(percentage),
                                Placeholders.TAX_TOTAL to Taxation.DECIMAL_FORMATTER.format(taxTotal),
                                Placeholders.CURRENCY_SYMBOL to currency.symbol.serialize()
                        )
                        .tryReplacePlaceholders(source = target)
                        .deserialize())
            }
        } else src.sendMessage("No session is running!".red())

        return CommandResult.success()
    }
}