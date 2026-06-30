package de.serwth.dt.energyusagemock;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.MqttCallbackExtended;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;

import java.util.LinkedHashMap;
import java.util.Map;

final class EnergyUsageMqttClient implements AutoCloseable {
  private final EnergyUsageMockConfig config;
  private final EnergyUsageMockService service;
  private final ObjectMapper objectMapper = new ObjectMapper();
  private final MqttClient client;

  EnergyUsageMqttClient(EnergyUsageMockConfig config, EnergyUsageMockService service) throws MqttException {
    this.config = config;
    this.service = service;
    this.client = new MqttClient(config.brokerUri(), config.clientId(), new MemoryPersistence());
    this.client.setTimeToWait(config.publishTimeoutMillis());
    this.client.setCallback(new EnergyUsageCallback());
  }

  void start() throws MqttException {
    MqttConnectOptions options = new MqttConnectOptions();
    options.setAutomaticReconnect(true);
    options.setCleanSession(true);
    options.setConnectionTimeout(10);
    options.setKeepAliveInterval(30);

    client.connect(options);
    subscribe();
    System.out.println("Energy usage mock connected to " + config.brokerUri());
  }

  void publishAllStates() {
    for (EnergyMeasurement measurement : service.energyMeasurements()) {
      publishEnergy(measurement);
    }
  }

  @Override
  public void close() {
    try {
      if (client.isConnected()) {
        client.disconnect();
      }
      client.close();
    }
    catch (MqttException e) {
      System.err.println("Failed to close MQTT client: " + e.getMessage());
    }
  }

  private void subscribe() throws MqttException {
    client.subscribe(config.commandTopic(), 0);
    client.subscribe(config.feedbackTopic(), 0);
  }

  private void publishEnergy(EnergyMeasurement measurement) {
    try {
      if (!client.isConnected()) {
        return;
      }

      Map<String, Object> envelope = new LinkedHashMap<>();
      envelope.put("value", measurement.powerW());
      envelope.put("timestamp", measurement.timestamp().toString());

      System.out.println("Publishing " + measurement.operation() + " energy to " + measurement.topic() + ": "
          + objectMapper.writeValueAsString(envelope));
      MqttMessage message = new MqttMessage(objectMapper.writeValueAsBytes(envelope));
      message.setQos(0);
      message.setRetained(false);
      client.publish(measurement.topic(), message);
    }
    catch (Exception e) {
      System.err.println("Failed to publish energy measurement for " + measurement.operation()
          + " mapped from " + measurement.sourceDeviceClass() + "/" + measurement.sourceDeviceId() + ": "
          + e.getMessage());
    }
  }

  private final class EnergyUsageCallback implements MqttCallbackExtended {
    @Override
    public void connectComplete(boolean reconnect, String serverURI) {
      if (reconnect) {
        try {
          subscribe();
        }
        catch (MqttException e) {
          System.err.println("Failed to resubscribe after reconnect: " + e.getMessage());
        }
      }
    }

    @Override
    public void connectionLost(Throwable cause) {
      System.err.println("MQTT connection lost: " + cause.getMessage());
    }

    @Override
    public void messageArrived(String topic, MqttMessage message) {
      service.handleMessage(topic, message.getPayload());
    }

    @Override
    public void deliveryComplete(IMqttDeliveryToken token) {
    }
  }
}
