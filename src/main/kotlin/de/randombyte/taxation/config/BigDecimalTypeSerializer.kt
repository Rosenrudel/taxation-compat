package de.randombyte.taxation.config

import com.google.common.reflect.TypeToken
import de.randombyte.kosp.extensions.typeToken
import ninja.leaping.configurate.ConfigurationNode
import ninja.leaping.configurate.objectmapping.ObjectMappingException
import ninja.leaping.configurate.objectmapping.serialize.TypeSerializer
import java.math.BigDecimal
import java.math.BigInteger

class BigDecimalTypeSerializer : TypeSerializer<BigDecimal> {

    companion object {
        private const val BYTES_NODE = "bytes"
        private const val SCALE_NODE = "scale"
    }

    override fun deserialize(type: TypeToken<*>, value: ConfigurationNode): BigDecimal {
        val bytes = value.getNode(BYTES_NODE).getValue(object : TypeToken<List<Byte>>() {})
                ?: throw ObjectMappingException("'$BYTES_NODE' not present!")
        val scale = value.getNode(SCALE_NODE).getValue(Int::class.typeToken)
                ?: throw ObjectMappingException("'$SCALE_NODE' not present!")

        return BigDecimal(BigInteger(bytes.toByteArray()), scale)
    }

    override fun serialize(type: TypeToken<*>, bigDecimal: BigDecimal?, value: ConfigurationNode) {
        if (bigDecimal == null) throw ObjectMappingException("Given BigDecimal is null!")
        value.getNode(BYTES_NODE).value = bigDecimal.unscaledValue().toByteArray().toList()
        value.getNode(SCALE_NODE).value = bigDecimal.scale()
    }
}