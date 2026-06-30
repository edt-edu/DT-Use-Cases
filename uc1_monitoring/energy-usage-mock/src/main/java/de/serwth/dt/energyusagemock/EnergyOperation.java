package de.serwth.dt.energyusagemock;

public enum EnergyOperation {
  DRILLING("DrillingMachine", "I1DrillingMachine01"),
  MILLING("MillingMachine", "I1MillingMachine01"),
  HEATING("HeatingMachine", "I1HeatingMachine01"),
  GRINDING("GrindingMachine", "I1GrindingMachine01"),
  POLISHING("PolishingMachine", "I1PolishingMachine01");

  private final String machineKind;
  private final String machine;

  EnergyOperation(String machineKind, String machine) {
    this.machineKind = machineKind;
    this.machine = machine;
  }

  String machineKind() {
    return machineKind;
  }

  String machine() {
    return machine;
  }
}
