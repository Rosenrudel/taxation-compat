package de.randombyte.taxation

import com.google.inject.Inject
import de.randombyte.kosp.extensions.green
import de.randombyte.kosp.extensions.toText
import de.randombyte.taxation.Taxation.Companion.ID
import de.randombyte.taxation.Taxation.Companion.NAME
import de.randombyte.taxation.Taxation.Companion.NUCLEUS_ID
import de.randombyte.taxation.Taxation.Companion.VERSION
import de.randombyte.taxation.commands.ClearSessionCommand
import de.randombyte.taxation.commands.SessionInfoCommand
import de.randombyte.taxation.config.ConfigAccessor
import org.slf4j.Logger
import org.spongepowered.api.Sponge
import org.spongepowered.api.command.CommandResult
import org.spongepowered.api.command.args.GenericArguments.*
import org.spongepowered.api.command.spec.CommandSpec
import org.spongepowered.api.config.ConfigDir
import org.spongepowered.api.event.Listener
import org.spongepowered.api.event.cause.Cause
import org.spongepowered.api.event.cause.EventContext
import org.spongepowered.api.event.economy.EconomyTransactionEvent
import org.spongepowered.api.event.filter.Getter
import org.spongepowered.api.event.game.GameReloadEvent
import org.spongepowered.api.event.game.state.GameInitializationEvent
import org.spongepowered.api.plugin.Dependency
import org.spongepowered.api.plugin.Plugin
import org.spongepowered.api.scheduler.Task
import org.spongepowered.api.service.economy.account.UniqueAccount
import org.spongepowered.api.service.economy.transaction.ResultType
import org.spongepowered.api.service.economy.transaction.TransactionResult
import org.spongepowered.api.service.economy.transaction.TransactionTypes.*
import java.nio.file.Path
import java.text.DecimalFormat
import java.util.*
import java.util.concurrent.TimeUnit


@Plugin(
    id = ID,
    name = NAME,
    version = VERSION,
    authors = ["RandomByte", "TAINCER"],
    dependencies = [(Dependency(id = NUCLEUS_ID, optional = true))]
)
class Taxation @Inject constructor(
    val logger: Logger,
    @ConfigDir(sharedRoot = false) configPath: Path
) {
    companion object {
        const val ID = "taxation"
        const val NAME = "Taxation"
        const val VERSION = "1.3.0"

        const val NUCLEUS_ID = "nucleus"

        const val ROOT_PERMISSION = ID
        const val TAX_EXEMPT_PERMISSION = "$ROOT_PERMISSION.taxes.exempt"

        val USER_ARG = "user".toText()

        private val _INSTANCE = lazy { Sponge.getPluginManager().getPlugin(ID).get().instance.get() as Taxation }
        val INSTANCE: Taxation get() = _INSTANCE.value

        val DECIMAL_FORMATTER = DecimalFormat("0.00")
    }

    val cause: Cause = Cause.of(EventContext.empty(), this)

    val configAccessor = ConfigAccessor(configPath)

    var database: Database? = null

    @Listener
    fun onInit(event: GameInitializationEvent) {
        configAccessor.reloadAll()
        registerCommands()

        Task.builder()
            .interval(1, TimeUnit.HOURS)
            .execute { -> Session().checkTaxCollection() }
            .submit(this)

        database = Database()
        database!!.createTables()

        logger.info("Loaded $NAME: $VERSION")
    }

    @Listener
    fun onReload(event: GameReloadEvent) {
        configAccessor.reloadAll()

        logger.info("Reloaded!")
    }

    @Listener
    fun onMoneyTransaction(
        event: EconomyTransactionEvent,
        @Getter("getTransactionResult") transactionResult: TransactionResult
    ) {
        if (
            transactionResult.account is UniqueAccount &&
            transactionResult.result == ResultType.SUCCESS &&
            transactionResult.currency.id == currency.id
        ) {
            val previousBalance = transactionResult.type.let { type ->
                val reversedOperation = when (type) {
                    WITHDRAW, TRANSFER -> transactionResult.amount
                    DEPOSIT -> transactionResult.amount.negate()
                    else -> throw UnsupportedOperationException("Illegal TransactionType: ${type.id}")
                }

                transactionResult.account.getBalance(currency) + reversedOperation
            }
            database!!.maybeTrackPlayer(transactionResult.account as UniqueAccount, previousBalance)
        }
    }

    private fun registerCommands() {
        val sessionInfoCommand = CommandSpec.builder()
            .permission("$ROOT_PERMISSION.session.info.self")
            .arguments(optional(requiringPermission(user(USER_ARG), "$ROOT_PERMISSION.session.info.others")))
            .executor(SessionInfoCommand())
            .build()

        Sponge.getCommandManager().register(this, CommandSpec.builder()
            .child(CommandSpec.builder()
                .child(
                    CommandSpec.builder()
                        .permission("$ROOT_PERMISSION.session.clean")
                        .arguments(user(USER_ARG.toText()))
                        .executor(ClearSessionCommand())
                        .build(), "clear"
                )
                .child(CommandSpec.builder()
                    .permission("$ROOT_PERMISSION.session.reset")
                    .executor { src, _ ->
                        database!!.emptyPlayers()
                        src.sendMessage("Reset session!".green())
                        return@executor CommandResult.success()
                    }
                    .build(), "reset")
                .child(sessionInfoCommand, "info")
                .build(), "session")
            .build(), ID)

        Sponge.getCommandManager().register(this, sessionInfoCommand, "taxes")
    }
}