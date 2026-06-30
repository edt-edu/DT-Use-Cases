package fischertechnik;

component EnergySum {
  port in double drillingEnergyUse,
       in double millingEnergyUse,
       in double heatingEnergyUse,
       in double grindingEnergyUse,
       in double polishingEnergyUse;
  port out double energyUse;

  double latestDrillingEnergyUse = 0.0;
  double latestMillingEnergyUse = 0.0;
  double latestHeatingEnergyUse = 0.0;
  double latestGrindingEnergyUse = 0.0;
  double latestPolishingEnergyUse = 0.0;

  <<delayed>> automaton {
    initial state Start;

    Start -> Start drillingEnergyUse / {
      latestDrillingEnergyUse = drillingEnergyUse;
      energyUse = latestDrillingEnergyUse + latestMillingEnergyUse + latestHeatingEnergyUse + latestGrindingEnergyUse + latestPolishingEnergyUse;
    }
    Start -> Start millingEnergyUse / {
      latestMillingEnergyUse = millingEnergyUse;
      energyUse = latestDrillingEnergyUse + latestMillingEnergyUse + latestHeatingEnergyUse + latestGrindingEnergyUse + latestPolishingEnergyUse;
    }
    Start -> Start heatingEnergyUse / {
      latestHeatingEnergyUse = heatingEnergyUse;
      energyUse = latestDrillingEnergyUse + latestMillingEnergyUse + latestHeatingEnergyUse + latestGrindingEnergyUse + latestPolishingEnergyUse;
    }
    Start -> Start grindingEnergyUse / {
      latestGrindingEnergyUse = grindingEnergyUse;
      energyUse = latestDrillingEnergyUse + latestMillingEnergyUse + latestHeatingEnergyUse + latestGrindingEnergyUse + latestPolishingEnergyUse;
    }
    Start -> Start polishingEnergyUse / {
      latestPolishingEnergyUse = polishingEnergyUse;
      energyUse = latestDrillingEnergyUse + latestMillingEnergyUse + latestHeatingEnergyUse + latestGrindingEnergyUse + latestPolishingEnergyUse;
    }
  }
}
