package de.randombyte.taxation.config

import de.randombyte.kosp.config.ConfigAccessor
import java.nio.file.Path

class ConfigAccessor(configPath: Path) : ConfigAccessor(configPath) {
    val general = getConfigHolder<GeneralConfig>("general.conf")
    val texts = getConfigHolder<TextsConfig>("texts.conf")

    override val holders = listOf(general, texts)
}