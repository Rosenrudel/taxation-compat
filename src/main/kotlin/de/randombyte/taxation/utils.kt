package de.randombyte.taxation

import de.randombyte.kosp.config.serializers.duration.SimpleDurationTypeSerializer
import de.randombyte.kosp.extensions.getServiceOrFail
import de.randombyte.taxation.config.GeneralConfig
import de.randombyte.taxation.config.StatisticsDatabase
import de.randombyte.taxation.config.TextsConfig
import org.slf4j.Logger
import org.spongepowered.api.service.economy.Currency
import org.spongepowered.api.service.economy.EconomyService
import java.time.Duration

val generalConfig: GeneralConfig get() = Taxation.INSTANCE.configAccessor.general.get()
val statisticsDatabase: StatisticsDatabase get() = Taxation.INSTANCE.configAccessor.statisticsDatabase.get()
val texts: TextsConfig get() = Taxation.INSTANCE.configAccessor.texts.get()
val logger: Logger get() = Taxation.INSTANCE.logger

fun EconomyService.getCurrencyById(id: String) = currencies.firstOrNull { it.id == id }
val economyService: EconomyService get() = EconomyService::class.getServiceOrFail()
val currency: Currency = generalConfig.currency.let {
    economyService.getCurrencyById(it) ?: throw RuntimeException("Couldn't find currency $it!")
}

val currentMillis: Long get() = System.currentTimeMillis()

fun String.deserializeDuration() = SimpleDurationTypeSerializer.deserialize(this)
fun Duration.serialize(outputMillis: Boolean = true) = SimpleDurationTypeSerializer.serialize(this, outputMillis)