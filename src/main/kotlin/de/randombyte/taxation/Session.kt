package de.randombyte.taxation

import de.randombyte.kosp.extensions.*
import de.randombyte.taxation.config.PersistenceDatabase
import de.randombyte.taxation.config.PersistenceDatabase.SerializedBigDecimal.Companion.toSerialized
import de.randombyte.taxation.config.TextsConfig.Placeholders
import io.github.nucleuspowered.nucleus.api.NucleusAPI
import org.spongepowered.api.Sponge
import org.spongepowered.api.scheduler.Task
import org.spongepowered.api.service.economy.transaction.ResultType
import java.math.BigDecimal
import java.time.Duration
import java.util.*
import java.util.concurrent.TimeUnit

class Session(
        val duration: Duration,
        var displayDuration: Duration = duration,
        val balances: MutableMap<UUID, BigDecimal> = mutableMapOf()
) {

    private var task: Task? = null
    private var startTime: Long? = null

    val remainingDuration: Duration get() {
        val passedTime = currentMillis - startTime!!
        return Duration.ofMillis(duration.toMillis() - passedTime)
    }

    val isRunning: Boolean get() {
        return task != null
    }

    fun start(plugin: Taxation) {
        if (isRunning) throw IllegalStateException("Session is already running!")

        task = Task.builder()
                .delay(duration.seconds, TimeUnit.SECONDS)
                .execute { ->
                    stop(isForcefullyStopped = false)
                    with(Taxation.INSTANCE) {
                        session = null
                        resetSession()
                    }
                }
                .submit(plugin)
        startTime = currentMillis
    }

    fun trackAccount(account: UUID, balance: BigDecimal) {
        if (account !in balances.keys) {
            balances[account] = balance
        }
    }

    fun stop(isForcefullyStopped: Boolean) {
        if (!isRunning) throw IllegalStateException("Session is not running!")

        task!!.cancel()
        task = null

        if (isForcefullyStopped) {
            displayDuration = duration - remainingDuration
        }

        val taxAccount = economyService.getOrCreateAccount(generalConfig.taxAccount)
                .orElseThrow { RuntimeException("Couldn't get the tax account!") }

        balances.mapKeysNotNull { accountId ->
            val account = economyService.getOrCreateAccount(accountId).orNull()
            if (account == null) Taxation.INSTANCE.logger.warn("Account $accountId was deleted during the session!")
            return@mapKeysNotNull account
        }.forEach { (account, oldBalance) ->

            // doing the math
            val newBalance = account.getBalance(currency)
            val income = newBalance - oldBalance

            val percentage = calculateTaxPercentage(income.toDouble())
            val tax = income * (percentage / 100).toBigDecimal()

            if (account.transfer(taxAccount, currency, tax, Taxation.INSTANCE.cause).result != ResultType.SUCCESS) {
                logger.error("Couldn't transfer tax $tax of account ${account.uniqueId} to the tax account!")
                return@forEach
            }

            // informing the user
            val user = account.uniqueId.getUser() ?: throw java.lang.RuntimeException("No player associated with UUID ${account.uniqueId}!")
            val message = texts.taxed.replace(
                    Placeholders.TAX_PERCENTAGE to Taxation.DECIMAL_FORMATTER.format(percentage),
                    Placeholders.TAX_TOTAL to Taxation.DECIMAL_FORMATTER.format(tax),
                    Placeholders.SESSION_INCOME to Taxation.DECIMAL_FORMATTER.format(income),
                    Placeholders.SESSION_DURATION to remainingDuration.serialize(outputMillis = false),
                    Placeholders.CURRENCY_SYMBOL to currency.symbol.serialize()
            )
            if (user.isOnline) {
                user.player.get().sendMessage(message.tryReplacePlaceholders(source = user.player.get()).deserialize())
            } else {
                if (Sponge.getPluginManager().isLoaded(Taxation.NUCLEUS_ID)) {
                    val mailService = NucleusAPI.getMailService().orNull() ?: return@forEach
                    mailService.sendMailFromConsole(user, message.tryReplacePlaceholders(source = user))
                }
            }
        }
    }

    private fun calculateTaxPercentage(income: Double): Double {
        if (generalConfig.rates.isEmpty()) throw RuntimeException("Tax rates config is empty!")
        val rateKey = generalConfig.rates.keys.sortedDescending().firstOrNull { rate -> income > rate } ?: return 0.0
        return generalConfig.rates.getValue(rateKey)
    }

    fun toPersistentConfig(): PersistenceDatabase {
        if (!isRunning) throw IllegalStateException("The sessions must be running to be saved!")

        return PersistenceDatabase(
                sessionRemainingTime = remainingDuration.serialize(),
                sessionDuration = duration.serialize(),
                previousBalances = balances.mapValues { (_, bigDecimal) -> bigDecimal.toSerialized() })
    }
}