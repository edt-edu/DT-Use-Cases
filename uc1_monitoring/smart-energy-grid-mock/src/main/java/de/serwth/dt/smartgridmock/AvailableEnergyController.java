package de.serwth.dt.smartgridmock;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class AvailableEnergyController {

  private final SmartEnergyGridService service;

  public AvailableEnergyController(SmartEnergyGridService service) {
    this.service = service;
  }

  @GetMapping("/available-energy")
  public AvailableEnergyResponse availableEnergy() {
    return service.currentAvailableEnergy();
  }
}
