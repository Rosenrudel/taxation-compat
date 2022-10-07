package de.randombyte.taxation

import de.randombyte.kosp.config.serializers.duration.SimpleDurationTypeSerializer
import de.randombyte.kosp.extensions.getServiceOrFail
import de.randombyte.taxation.config.GeneralConfig
import de.randombyte.taxation.config.TextsConfig
import org.slf4j.Logger
import org.spongepowered.api.service.economy.Currency
import org.spongepowered.api.service.economy.EconomyService
import org.spongepowered.api.service.economy.account.UniqueAccount
import java.time.Duration
import java.util.*

val generalConfig: GeneralConfig get() = Taxation.INSTANCE.configAccessor.general.get()
val texts: TextsConfig get() = Taxation.INSTANCE.configAccessor.texts.get()
val logger: Logger get() = Taxation.INSTANCE.logger

fun EconomyService.getCurrencyById(id: String) = currencies.firstOrNull { it.id == id }
val economyService: EconomyService get() = EconomyService::class.getServiceOrFail()
val currency: Currency = generalConfig.currency.let {
    economyService.getCurrencyById(it) ?: throw RuntimeException("Couldn't find currency $it!")
}

fun UUID.asAccount(): UniqueAccount = economyService.getOrCreateAccount(this)
    .orElseThrow { RuntimeException("Couldn't get or create account $this!") }

fun Duration.serialize(outputMillis: Boolean = true) = SimpleDurationTypeSerializer.serialize(this, outputMillis)