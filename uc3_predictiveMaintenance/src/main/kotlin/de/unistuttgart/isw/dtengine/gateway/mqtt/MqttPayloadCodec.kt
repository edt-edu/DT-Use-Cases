package de.unistuttgart.isw.dtengine.gateway.mqtt

import java.nio.charset.Charset

class MqttPayloadCodec(
    private val charset: Charset = Charsets.UTF_8
) {
    fun decode(payload: ByteArray, valueType: MqttValueType): Any? {
        val text = payload.toString(charset).trim()
        if (text.isBlank()) return null

        return when (valueType) {
            MqttValueType.BOOLEAN -> decodeBoolean(text)
            MqttValueType.INTEGER -> text.toIntOrNull()
                ?: text.toDoubleOrNull()?.toInt()
                ?: throw IllegalArgumentException("Cannot decode integer MQTT payload: '$text'")
            MqttValueType.STRING -> text
        }
    }


    fun inferValueType(payload: ByteArray): MqttValueType {
        val text = payload.toString(charset).trim()
        if (text.isBlank()) return MqttValueType.STRING
        val lower = text.lowercase()
        if (lower in setOf("true", "false", "1", "0", "on", "off", "yes", "no", "high", "low")) {
            return MqttValueType.BOOLEAN
        }
        if (text.toIntOrNull() != null) return MqttValueType.INTEGER
        return MqttValueType.STRING
    }

    fun encode(value: Any?, valueType: MqttValueType): ByteArray {
        val text = when (valueType) {
            MqttValueType.BOOLEAN -> encodeBoolean(value)
            MqttValueType.INTEGER -> encodeInteger(value)
            MqttValueType.STRING -> value?.toString() ?: ""
        }
        return text.toByteArray(charset)
    }

    private fun decodeBoolean(text: String): Boolean = when (text.lowercase()) {
        "true", "1", "on", "yes", "high" -> true
        "false", "0", "off", "no", "low" -> false
        else -> throw IllegalArgumentException("Cannot decode boolean MQTT payload: '$text'")
    }

    private fun encodeBoolean(value: Any?): String = when (value) {
        is Boolean -> value.toString()
        is Number -> (value.toInt() != 0).toString()
        is String -> decodeBoolean(value).toString()
        null -> "false"
        else -> throw IllegalArgumentException("Cannot encode boolean MQTT payload from ${value::class.simpleName}")
    }

    private fun encodeInteger(value: Any?): String = when (value) {
        is Number -> value.toInt().toString()
        is String -> value.toIntOrNull()?.toString()
            ?: throw IllegalArgumentException("Cannot encode integer MQTT payload from '$value'")
        null -> "0"
        else -> throw IllegalArgumentException("Cannot encode integer MQTT payload from ${value::class.simpleName}")
    }
}
