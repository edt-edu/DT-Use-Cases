/* (c) https://github.com/MontiCore/monticore */
package factory.manufacturer.production.machines;
import factory.FischertechnikTypes.*;

component Polishing {
  port in Gear pieceIn,
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
		                              pieceIn.drillHoleDiameter,
		                              pieceIn.numberOfTeeth,
		                              pieceIn.hardened,
		                              pieceIn.ground,
		                              true
                              );
          energyUse = 1.0;
    }
	}

}
