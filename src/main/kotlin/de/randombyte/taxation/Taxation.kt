package de.randombyte.taxation

import com.google.inject.Inject
import de.randombyte.kosp.extensions.getPlayer
import de.randombyte.kosp.extensions.green
import de.randombyte.kosp.extensions.sendTo
import de.randombyte.kosp.extensions.toText
import de.randombyte.taxation.Taxation.Companion.AUTHOR
import de.randombyte.taxation.Taxation.Companion.ID
import de.randombyte.taxation.Taxation.Companion.NAME
import de.randombyte.taxation.Taxation.Companion.NUCLEUS_ID
import de.randombyte.taxation.Taxation.Companion.VERSION
import de.randombyte.taxation.commands.ClearSessionCommand
import de.randombyte.taxation.commands.SessionInfoCommand
import de.randombyte.taxation.config.ConfigAccessor
import de.randombyte.taxation.config.PersistenceDatabase
import org.apache.commons.lang3.RandomUtils
import org.bstats.sponge.Metrics2
import org.slf4j.Logger
import org.spongepowered.api.Sponge
import org.spongepowered.api.command.CommandResult
import org.spongepowered.api.command.args.GenericArguments.*
import org.spongepowered.api.command.spec.CommandSpec
import org.spongepowered.api.config.ConfigDir
import org.spongepowered.api.entity.living.player.Player
import org.spongepowered.api.event.Listener
import org.spongepowered.api.event.cause.Cause
import org.spongepowered.api.event.cause.EventContext
import org.spongepowered.api.event.economy.EconomyTransactionEvent
import org.spongepowered.api.event.filter.Getter
import org.spongepowered.api.event.game.GameReloadEvent
import org.spongepowered.api.event.game.state.GameInitializationEvent
import org.spongepowered.api.event.game.state.GameStoppingServerEvent
import org.spongepowered.api.event.network.ClientConnectionEvent
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

@Plugin(id = ID,
        name = NAME,
        version = VERSION,
        authors = [AUTHOR],
        dependencies = [(Dependency(id = NUCLEUS_ID, optional = true))])
class Taxation @Inject constructor(
        val logger: Logger,
        @ConfigDir(sharedRoot = false) configPath: Path,
        private val metrics: Metrics2
) {
    companion object {
        const val ID = "taxation"
        const val NAME = "Taxation"
        const val VERSION = "1.2.0"
        const val AUTHOR = "RandomByte"

        const val NUCLEUS_ID = "nucleus"

        const val ROOT_PERMISSION = ID
        const val TAX_EXEMPT_PERMISSION = "$ROOT_PERMISSION.taxes.exempt"

        val USER_ARG = "user".toText()

        private val _INSTANCE = lazy { Sponge.getPluginManager().getPlugin(ID).get().instance.get() as Taxation }
        val INSTANCE: Taxation get() = _INSTANCE.value

        val DECIMAL_FORMATTER = DecimalFormat("0.00")
    }

    val cause = Cause.of(EventContext.empty(), this)

    val configAccessor = ConfigAccessor(configPath)

    var session: Session? = null

    @Listener
    fun onInit(event: GameInitializationEvent) {
        configAccessor.reloadAll()
        restoreSessionFromPersistence()
        registerCommands()

        logger.info("Loaded $NAME: $VERSION")
    }

    @Listener
    fun onReload(event: GameReloadEvent) {
        configAccessor.reloadAll()

        logger.info("Reloaded!")
    }

    @Listener
    fun onShutdown(event: GameStoppingServerEvent) {
        if (session?.isRunning == true) {
            val persistentConfig = session!!.toPersistentConfig()
            configAccessor.persistenceDatabase.save(persistentConfig)
            logger.info("Session saved with remaining time: ${persistentConfig.sessionRemainingTime}")
        }
    }

    @Listener
    fun onMoneyTransaction(event: EconomyTransactionEvent, @Getter("getTransactionResult") transactionResult: TransactionResult) {
        if (
                session?.isRunning == true &&
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
            session!!.trackAccount((transactionResult.account as UniqueAccount).uniqueId, previousBalance)
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
                        .child(CommandSpec.builder()
                                .permission("$ROOT_PERMISSION.session.clean")
                                .arguments(user(USER_ARG.toText()))
                                .executor(ClearSessionCommand())
                                .build(), "clear")
                        .child(CommandSpec.builder()
                                .permission("$ROOT_PERMISSION.session.reset")
                                .executor { src, _ ->
                                    resetSession()
                                    src.sendMessage("Reset session!".green())
                                    return@executor CommandResult.success()
                                }
                                .build(), "reset")
                        .child(sessionInfoCommand, "info")
                        .build(), "session")
                .build(), ID)

        Sponge.getCommandManager().register(this, sessionInfoCommand, "taxes")
    }

    fun resetSession() {
        session?.stop(isForcefullyStopped = true)
        session = Session(duration = generalConfig.sessionDuration.deserializeDuration())
        session!!.start(plugin = this)
    }

    /**
     * Restores saved data from the [PersistenceDatabase] or defaults to values set in the config
     */
    private fun restoreSessionFromPersistence() {
        if (session?.isRunning == true) throw IllegalStateException("A session is already running!")

        return

        with(configAccessor.persistenceDatabase) {
            val persistence = get()

            val remainingDuration = (persistence.sessionRemainingTime ?: generalConfig.sessionDuration).deserializeDuration()
            val displayDuration = persistence.sessionDuration?.deserializeDuration() ?: remainingDuration

            val balances = if (persistence.previousBalances != null) {
                persistence.previousBalances
                        .mapValues { (_, serializedBigDecimal) -> serializedBigDecimal.toBigDecimal() }
                        .toMutableMap()
            } else mutableMapOf()

            session = Session(
                    duration = remainingDuration,
                    originalTotalDuration = displayDuration,
                    balances = balances)

            session!!.start(this@Taxation)

            save(PersistenceDatabase()) // save empty config, only write back to it when shutting down the server
        }
    }
}