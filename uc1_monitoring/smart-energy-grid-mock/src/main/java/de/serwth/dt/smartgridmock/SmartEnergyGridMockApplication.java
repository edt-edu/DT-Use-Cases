package de.serwth.dt.smartgridmock;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class SmartEnergyGridMockApplication {

  public static void main(String[] args) {
    SpringApplication.run(SmartEnergyGridMockApplication.class, args);
  }
}
