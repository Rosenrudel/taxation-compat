package de.randombyte.taxation.config

import de.randombyte.kosp.config.ConfigAccessor
import de.randombyte.kosp.config.ConfigHolder
import de.randombyte.kosp.config.ConfigManager
import de.randombyte.kosp.config.toConfigHolder
import de.randombyte.kosp.extensions.toConfigurationLoader
import de.randombyte.kosp.extensions.typeToken
import ninja.leaping.configurate.objectmapping.serialize.TypeSerializerCollection
import java.math.BigDecimal
import java.nio.file.Path

class ConfigAccessor(configPath: Path) : ConfigAccessor(configPath) {

    val general = getConfigHolder<GeneralConfig>("general.conf")
    val persistenceDatabase = getConfigHolder<PersistenceDatabase>("persistence-database.conf")
    val texts = getConfigHolder<TextsConfig>("texts.conf")
    val statisticsDatabase = getConfigHolderWithSerializers<StatisticsDatabase>("statistics-database.conf") {
        registerType(BigDecimal::class.typeToken, BigDecimalTypeSerializer())
    }

    override val holders = listOf(general, persistenceDatabase, texts, statisticsDatabase)

    private inline fun <reified T : Any> getConfigHolderWithSerializers(
            configName: String,
            noinline additionalSerializers: TypeSerializerCollection.() -> Any
    ): ConfigHolder<T> {
        return ConfigManager(
                configLoader = configPath.resolve(configName).toConfigurationLoader(),
                clazz = T::class.java,
                additionalSerializers = additionalSerializers
        ).toConfigHolder()
    }
}