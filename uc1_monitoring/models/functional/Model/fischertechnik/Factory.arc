package fischertechnik;
import factory.FischertechnikTypes.*;

import factory.manufacturer.production.machines.*;

component Factory {
  port in ProductConfig config,
       in GearBlank pieceIn;
  port out Gear finishedGear,
       out double energyUse;

  <<mqttNamespace="PLC/Island 1/DrillingMachine/I1DrillingMachine01/">>
  Drilling drilling;

  <<mqttNamespace="PLC/Island 1/MillingMachine/I1MillingMachine01/">>
  Milling milling;

  <<mqttNamespace="PLC/Island 1/HeatingMachine/I1HeatingMachine01/">>
  Heating heating;

  <<mqttNamespace="PLC/Island 1/GrindingMachine/I1GrindingMachine01/">>
  Grinding grinding;

  <<mqttNamespace="PLC/Island 1/PolishingMachine/I1PolishingMachine01/">>
  Polishing polishing;

  EnergySum energySum;

  config -> drilling.config;
  config -> milling.config;
  config -> heating.config;
  config -> grinding.config;
  config -> polishing.config;

  pieceIn -> drilling.pieceIn;
  drilling.pieceOut -> milling.pieceIn;
  milling.pieceOut -> heating.pieceIn;
  heating.pieceOut -> grinding.pieceIn;
  grinding.pieceOut -> polishing.pieceIn;
  polishing.pieceOut -> finishedGear;

  drilling.energyUse -> energySum.drillingEnergyUse;
  milling.energyUse -> energySum.millingEnergyUse;
  heating.energyUse -> energySum.heatingEnergyUse;
  grinding.energyUse -> energySum.grindingEnergyUse;
  polishing.energyUse -> energySum.polishingEnergyUse;
  energySum.energyUse -> energyUse;
}
