package viz;

import functionstructure4fenix.DoubleStream;
import monitoringdt.Factory;
import monitoringdt.MonitoringDTManager;
import umlp.jsweet.extension.annotation.Component;

@Component
public class VisualizationPage extends VisualizationPageTOP {
  public DoubleStream getUsedEnergyStream() {
    for (Factory factory : MonitoringDTManager.getFactoryList()) {
      return factory.getEnergyUse().getDoubleStream();
    }
    return null;
  }
}
