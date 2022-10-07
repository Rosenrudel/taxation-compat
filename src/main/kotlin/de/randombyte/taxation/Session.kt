package de.randombyte.taxation

import de.randombyte.kosp.extensions.*
import de.randombyte.taxation.config.TextsConfig.Placeholders
import io.github.nucleuspowered.nucleus.api.NucleusAPI
import org.spongepowered.api.Sponge
import org.spongepowered.api.service.economy.transaction.ResultType
import java.math.BigDecimal
import java.time.Instant
import java.util.*

class Session {
    private var balances: MutableMap<UUID, BigDecimal> = mutableMapOf()

    init {
        refresh()
    }

    private fun refresh() {
        balances = Taxation.INSTANCE.database!!.getPlayers()
    }

    fun checkTaxCollection() {
        val database = Taxation.INSTANCE.database!!

        var endTime = database.getRuntimeValue("next_tax_collection_at")
        if (endTime == null) {
            endTime = (Instant.now().epochSecond + generalConfig.sessionDuration * 60).toString()
            database.setRuntimeConfig("next_tax_collection_at", endTime)
        }

        if (Instant.now().isAfter(Instant.ofEpochSecond(endTime.toLong()))) {
            processTaxes()
            database.removeRuntimeConfig("next_tax_collection_at")
            checkTaxCollection()
        }
    }

    private fun processTaxes() {
        refresh()

        val taxAccount = economyService.getOrCreateAccount(generalConfig.taxAccount)
            .orElseThrow { RuntimeException("Couldn't get the tax account!") }

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

            Taxation.INSTANCE.database!!.playerAddStatistic(accountId, tax)

            // informing the user
            val user = account.uniqueId.getUser()
                ?: throw java.lang.RuntimeException("No player associated with UUID ${account.uniqueId}!")
            val message = texts.taxed.replace(
                Placeholders.TAX_PERCENTAGE to Taxation.DECIMAL_FORMATTER.format(percentage),
                Placeholders.TAX_TOTAL to Taxation.DECIMAL_FORMATTER.format(tax),
                Placeholders.SESSION_INCOME to Taxation.DECIMAL_FORMATTER.format(income),
                Placeholders.CURRENCY_SYMBOL to currency.symbol.serialize()
            )
            if (user.isOnline) {
                user.player.get().sendMessage(message.tryReplacePlaceholders(source = user.player.get()).deserialize())
            } else {
                if (Sponge.getPluginManager().isLoaded(Taxation.NUCLEUS_ID)) {
                    NucleusAPI.getMailService().orNull()?.sendMailFromConsole(
                        user, message.tryReplacePlaceholders(source = user).deserialize().toPlain()
                    )
                }
            }

            return@mapNotNull tax
        }

        Taxation.INSTANCE.database!!.emptyPlayers()

        if (texts.broadcast.isNotBlank()) {
            var totalSessionTax = BigDecimal(0)
            totalTaxes.forEach { totalSessionTax += it }

            texts.broadcast.replace(
                Placeholders.TAX_TOTAL to Taxation.DECIMAL_FORMATTER.format(totalSessionTax),
                Placeholders.CURRENCY_SYMBOL to currency.symbol.serialize()
            ).tryReplacePlaceholders().deserialize().broadcast()
        }

        generalConfig.endTriggerCommands.forEach { it.executeAsConsole() }
    }

    fun calculateIncome(accountId: UUID): BigDecimal {
        if (!balances.containsKey(accountId)) {
            return BigDecimal(0)
        }

        val newBalance = accountId.asAccount().getBalance(currency)
        return newBalance - balances.getValue(accountId)
    }

    fun calculateTaxPercentage(income: Double): Double {
        if (generalConfig.rates.isEmpty()) throw RuntimeException("Tax rates config is empty!")
        val rateKey = generalConfig.rates.keys.sortedDescending().firstOrNull { rate -> income > rate } ?: return 0.0
        return generalConfig.rates.getValue(rateKey)
    }

    fun calculateTaxTotal(income: Double, taxRate: Double) = income * (taxRate / 100)
}