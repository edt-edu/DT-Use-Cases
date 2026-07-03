package de.unistuttgart.isw.dtengine.gateway.mqtt

import org.eclipse.paho.client.mqttv3.IMqttAsyncClient
import org.eclipse.paho.client.mqttv3.MqttAsyncClient
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence

fun interface MqttClientFactory {
    fun create(brokerUri: String, clientId: String): IMqttAsyncClient
}

class PahoMqttClientFactory : MqttClientFactory {
    override fun create(brokerUri: String, clientId: String): IMqttAsyncClient =
        MqttAsyncClient(brokerUri, clientId, MemoryPersistence())
}
