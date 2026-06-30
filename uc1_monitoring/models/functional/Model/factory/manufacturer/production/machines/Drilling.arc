/* (c) https://github.com/MontiCore/monticore */
package factory.manufacturer.production.machines;
import factory.FischertechnikTypes.*;

component Drilling {
  port in GearBlank pieceIn,
       in ProductConfig config;
  port out Gear pieceOut,
       <<mqtt="events/emitted/energy_measurement">>
       out double energyUse;

  ProductConfig lastProductConfig = ProductConfig.ProductConfig();

	<<delayed>> automaton {
		initial state Start;
		Start -> Start config / { lastProductConfig = config; }
		Start -> Start pieceIn / {
		      pieceOut = Gear.Gear(
		                              pieceIn.diameter,
		                              pieceIn.width,
		                              lastProductConfig.drillHoleDiameter,
		                              0,
		                              false,
		                              false,
		                              false
                              );
          energyUse = 1.0;
    }
	}

}
