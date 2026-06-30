package monitoringdt;

import functionstructure4fenix.Category;
import functionstructure4fenix.Energy;
import functionstructure4fenix.FunctionStructure4FenixManager;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.io.IOException;
import java.util.Optional;

@jsweet.lang.Erased
@EnableScheduling
public class MonitoringDTServerApplication extends MonitoringDTServerApplicationTOP {
  private static final String DEFAULT_FUNCTION_NAME = "factory";

  @Override
  public void init() throws IOException {
    super.init();

    initEnergyShadow();

    FunctionStructure4FenixManager.setDefaultFunctionName(DEFAULT_FUNCTION_NAME);
    Factory factory = new FactoryFuStruCreator().createFunctionStructure();
    try {
      new FactoryMqttConnector().connectToMqtt(factory);
    } catch (MqttException e) {
      throw new IOException(e);
    }
  }

  protected void initEnergyShadow() {
    EnergyShadow es = MonitoringDTManager.getEnergyShadow();

    Optional<Category> energyCategory = getEnergyCategory();

    if(!es.isPresentAvailableEnergy()){
      es.setAvailableEnergy(
          FunctionStructure4FenixManager.doubleStreamBuilder().category(energyCategory.get()).build().get()
      );
    }
    if(!es.isPresentEnergyDifference()){
      es.setEnergyDifference(
          FunctionStructure4FenixManager.doubleStreamBuilder().category(energyCategory.get()).build().get()
      );
    }
  }

  protected Optional<Category> getEnergyCategory() {
    Optional<Category> energyCategory = FunctionStructure4FenixManager.getCategoryList().stream().filter(it -> it.getKind() instanceof Energy).findFirst();
    if(energyCategory.isEmpty()){
      energyCategory = FunctionStructure4FenixManager.categoryBuilder()
          .kind(FunctionStructure4FenixManager.energyBuilder().build().get())
          .type(FunctionStructure4FenixManager.typeBuilder().mctype("double").build().get())
          .build();
    }
    return energyCategory;
  }
}
