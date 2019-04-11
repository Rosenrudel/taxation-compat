package de.randombyte.taxation

import de.randombyte.kosp.extensions.*
import de.randombyte.taxation.config.PersistenceDatabase
import de.randombyte.taxation.config.PersistenceDatabase.SerializedBigDecimal.Companion.toSerialized
import de.randombyte.taxation.config.StatisticsDatabase.Statistics
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
        // once a session is restored, the duration will only span between the restore time and the end time
        // to still be able to display the whole original duration, it is saved and restored here
        // it won't be used for any calculations
        var originalTotalDuration: Duration = duration,
        val balances: MutableMap<UUID, BigDecimal> = mutableMapOf()
) {

    private var task: Task? = null
    private var startTime: Long? = null

    val passedDuration: Duration get() = Duration.ofMillis(currentMillis - startTime!!)

    val remainingDuration: Duration get() = duration - passedDuration

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
            // session was stopped before the planned end time -> the passed time has to be calculated
            originalTotalDuration = duration - remainingDuration
        }

        val taxAccount = economyService.getOrCreateAccount(generalConfig.taxAccount)
                .orElseThrow { RuntimeException("Couldn't get the tax account!") }

        val taxStatistics = statisticsDatabase.players.toMutableMap()

        val totalTaxes = balances.keys.mapNotNull { accountId ->
            val account = accountId.asAccount()

            if (accountId.getUser()?.hasPermission(Taxation.TAX_EXEMPT_PERMISSION) == true) {
                return@mapNotNull BigDecimal.ZERO
            }

            // doing the math
            val income = calculateIncome(accountId)
            val percentage = calculateTaxPercentage(income.toDouble())
            val tax = calculateTaxTotal(income.toDouble(), percentage).toBigDecimal()

            if (account.transfer(taxAccount, currency, tax, Taxation.INSTANCE.cause).result != ResultType.SUCCESS) {
                logger.error("Couldn't transfer tax $tax of account ${account.uniqueId} to the tax account!")
                return@mapNotNull null // continue
            }

            // statistics
            taxStatistics[accountId] = (taxStatistics[accountId] ?: Statistics()).apply { totalTaxPayed += tax }

            // informing the user
            val user = account.uniqueId.getUser() ?: throw java.lang.RuntimeException("No player associated with UUID ${account.uniqueId}!")
            val message = texts.taxed.replace(
                    Placeholders.TAX_PERCENTAGE to Taxation.DECIMAL_FORMATTER.format(percentage),
                    Placeholders.TAX_TOTAL to Taxation.DECIMAL_FORMATTER.format(tax),
                    Placeholders.SESSION_INCOME to Taxation.DECIMAL_FORMATTER.format(income),
                    Placeholders.SESSION_DURATION to originalTotalDuration.serialize(outputMillis = false),
                    Placeholders.CURRENCY_SYMBOL to currency.symbol.serialize()
            )
            if (user.isOnline) {
                user.player.get().sendMessage(message.tryReplacePlaceholders(source = user.player.get()).deserialize())
            } else {
                if (Sponge.getPluginManager().isLoaded(Taxation.NUCLEUS_ID)) {
                    NucleusAPI.getMailService().orNull()?.sendMailFromConsole(user, message.tryReplacePlaceholders(source = user).deserialize().toPlain())
                }
            }

            return@mapNotNull tax
        }

        Taxation.INSTANCE.configAccessor.statisticsDatabase.save(statisticsDatabase.copy(players = taxStatistics))

        if (texts.broadcast.isNotBlank()) {
            var totalSessionTax = BigDecimal(0)
            totalTaxes.forEach { totalSessionTax += it }

            texts.broadcast
                    .replace(
                            Placeholders.TAX_TOTAL to Taxation.DECIMAL_FORMATTER.format(totalSessionTax),
                            Placeholders.CURRENCY_SYMBOL to currency.symbol.serialize()
                    )
                    .tryReplacePlaceholders()
                    .deserialize()
                    .broadcast()
        }

        generalConfig.endTriggerCommands.forEach { it.executeAsConsole() }
    }

    fun calculateIncome(accountId: UUID): BigDecimal {
        val account = accountId.asAccount()
        if (accountId !in balances.keys) trackAccount(accountId, account.getBalance(currency))
        val newBalance = account.getBalance(currency)
        return newBalance - balances.getValue(accountId)
    }

    fun calculateTaxPercentage(income: Double): Double {
        if (generalConfig.rates.isEmpty()) throw RuntimeException("Tax rates config is empty!")
        val rateKey = generalConfig.rates.keys.sortedDescending().firstOrNull { rate -> income > rate } ?: return 0.0
        return generalConfig.rates.getValue(rateKey)
    }

    fun calculateTaxTotal(income: Double, taxRate: Double) = income * (taxRate / 100)

    fun toPersistentConfig(): PersistenceDatabase {
        if (!isRunning) throw IllegalStateException("The session must be running to be saved!")

        return PersistenceDatabase(
                sessionRemainingTime = remainingDuration.serialize(),
                sessionDuration = duration.serialize(),
                previousBalances = balances.mapValues { (_, bigDecimal) -> bigDecimal.toSerialized() })
    }
}