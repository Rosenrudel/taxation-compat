package de.randombyte.taxation.commands

import de.randombyte.kosp.extensions.deserialize
import de.randombyte.kosp.extensions.red
import de.randombyte.kosp.extensions.replace
import de.randombyte.kosp.extensions.tryReplacePlaceholders
import de.randombyte.taxation.Taxation
import de.randombyte.taxation.config.TextsConfig.Placeholders
import de.randombyte.taxation.serialize
import de.randombyte.taxation.texts
import org.spongepowered.api.command.CommandResult
import org.spongepowered.api.command.CommandSource
import org.spongepowered.api.command.args.CommandContext
import org.spongepowered.api.command.spec.CommandExecutor

class SessionInfoCommand : CommandExecutor {
    override fun execute(src: CommandSource, args: CommandContext): CommandResult {
        if (Taxation.INSTANCE.session?.isRunning == true) {
            val session = Taxation.INSTANCE.session!!

            val remainingDurationString = session.remainingDuration.serialize(outputMillis = false)
            src.sendMessage(
                    texts.info
                            .replace(Placeholders.REMAINING_DURATION to remainingDurationString)
                            .tryReplacePlaceholders(source = src)
                            .deserialize())
        } else src.sendMessage("No sessions is running!".red())

        return CommandResult.success()
    }
}