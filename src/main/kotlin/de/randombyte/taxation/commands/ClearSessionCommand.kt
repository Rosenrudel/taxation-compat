package de.randombyte.taxation.commands

import de.randombyte.kosp.extensions.green
import de.randombyte.taxation.Taxation
import org.spongepowered.api.command.CommandResult
import org.spongepowered.api.command.CommandSource
import org.spongepowered.api.command.args.CommandContext
import org.spongepowered.api.command.spec.CommandExecutor
import org.spongepowered.api.entity.living.player.User

class ClearSessionCommand : CommandExecutor {
    override fun execute(src: CommandSource, args: CommandContext): CommandResult {
        val user = args.getOne<User>(Taxation.USER_ARG).get()

        Taxation.INSTANCE.database!!.deletePlayer(user.uniqueId)

        src.sendMessage("Cleared ${user.name}'s session!".green())

        return CommandResult.success()
    }
}