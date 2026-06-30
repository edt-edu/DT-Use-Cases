package monitoringdt.service;

import functionstructure4fenix.DoubleStream;
import functionstructure4fenix.DoubleStreamObserver;
import functionstructure4fenix.DoubleValue;
import functionstructure4fenix.FunctionStructure4FenixManager;
import monitoringdt.EnergyShadow;
import monitoringdt.Factory;
import monitoringdt.MonitoringDTManager;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
public class ShadowAggregatorService {

  public ShadowAggregatorService() {
    initEnergyDifferenceObserver();
    initEnergySumObserver();
  }

  protected void initEnergyDifferenceObserver() {
    EnergyShadow es = MonitoringDTManager.getEnergyShadow();
    Factory factory = MonitoringDTManager.getFactoryList().getFirst();

    // available energy changed
    es.getAvailableEnergy().addObserver(
        new DoubleStreamObserver() {
          @Override
          public void notifyAddDoubleValue(DoubleStream doubleStream, long arg, int indexInList) {
            DoubleValue available = doubleStream.getDoubleValue(indexInList);

            double used = 0;
            DoubleStream energyUse = factory.getEnergyUse().getDoubleStream();
            if(energyUse.sizeValues() != 0){
              used = energyUse.getDoubleValueList().getLast().getContent();
            }

            calculateAnSaveEnergyDiff(available.getTimestamp(), available.getContent(), used);
          }
        }
    );

    // used energy use changed
    factory.getEnergyUse().getDoubleStream().addObserver(
        new DoubleStreamObserver() {
          @Override
          public void notifyAddDoubleValue(DoubleStream doubleStream, long arg, int indexInList) {
            DoubleValue used = doubleStream.getDoubleValue(indexInList);

            double available = 0;
            DoubleStream availableEnergy = es.getAvailableEnergy();
            if(availableEnergy.sizeValues() != 0){
              available = availableEnergy.getDoubleValueList().getLast().getContent();
            }

            calculateAnSaveEnergyDiff(used.getTimestamp(), available, used.getContent());
          }
        }
    );
  }

  protected void calculateAnSaveEnergyDiff(LocalDateTime timestamp, double available, double used){
    EnergyShadow es = MonitoringDTManager.getEnergyShadow();
    es.getEnergyDifference().addDoubleValue(
        FunctionStructure4FenixManager.doubleValueBuilder()
            .timestamp(timestamp)
            .content(available - used)
            .build().get()
    );
  }

  protected void initEnergySumObserver() {
    Factory factory = MonitoringDTManager.getFactoryList().getFirst();

    DoubleStreamObserver recalcEnergySumOnChange = new DoubleStreamObserver() {
      @Override
      public void notifyAddDoubleValue(DoubleStream doubleStream, long arg, int indexInList) {
        recalcAndSaveEnergySum();
      }
    };

    factory.getDrilling().getEnergyUse().getDoubleStream().addObserver(recalcEnergySumOnChange);
    factory.getMilling().getEnergyUse().getDoubleStream().addObserver(recalcEnergySumOnChange);
    factory.getHeating().getEnergyUse().getDoubleStream().addObserver(recalcEnergySumOnChange);
    factory.getGrinding().getEnergyUse().getDoubleStream().addObserver(recalcEnergySumOnChange);
    factory.getPolishing().getEnergyUse().getDoubleStream().addObserver(recalcEnergySumOnChange);
  }

  protected void recalcAndSaveEnergySum(){
    Factory factory = MonitoringDTManager.getFactoryList().getFirst();

    DoubleStream ds = factory.getDrilling().getEnergyUse().getDoubleStream();
    DoubleStream ms = factory.getMilling().getEnergyUse().getDoubleStream();
    DoubleStream hs = factory.getHeating().getEnergyUse().getDoubleStream();
    DoubleStream gs = factory.getGrinding().getEnergyUse().getDoubleStream();
    DoubleStream ps = factory.getPolishing().getEnergyUse().getDoubleStream();

    double sum = 0;

    double drillingEnergy = ds.sizeValues() == 0 ? 0 : ds.getDoubleValueList().getLast().getContent();
    double millingEnergy = ms.sizeValues() == 0 ? 0 : ms.getDoubleValueList().getLast().getContent();
    double heatingEnergy = hs.sizeValues() == 0 ? 0 : hs.getDoubleValueList().getLast().getContent();
    double grindingEnergy = gs.sizeValues() == 0 ? 0 : gs.getDoubleValueList().getLast().getContent();
    double polishingEnergy = ps.sizeValues() == 0 ? 0 : ps.getDoubleValueList().getLast().getContent();


    System.out.println("drillingEnergy: " + drillingEnergy);
    System.out.println("millingEnergy: " + millingEnergy);
    System.out.println("heatingEnergy: " + heatingEnergy);
    System.out.println("grindingEnergy: " + grindingEnergy);
    System.out.println("polishingEnergy: " + polishingEnergy);

    sum += drillingEnergy;
    sum += millingEnergy;
    sum += heatingEnergy;
    sum += grindingEnergy;
    sum += polishingEnergy;

    System.out.println("sum: " + sum);

    factory.getEnergyUse().getDoubleStream().addDoubleValue(
        FunctionStructure4FenixManager.doubleValueBuilder()
            .content(sum)
            .timestamp(LocalDateTime.now())
            .build()
            .get()
    );
  }
}
