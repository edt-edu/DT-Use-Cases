/* (c) https://github.com/MontiCore/monticore */
package umlp.jsweet.extension.umlp;

import com.google.common.collect.FluentIterable;
import de.se_rwth.commons.Splitters;
import org.jsweet.transpiler.ModuleImportDescriptor;
import org.jsweet.transpiler.extension.PrinterAdapter;
import org.jsweet.transpiler.model.CompilationUnitElement;
import org.jsweet.transpiler.model.ImportElement;

import javax.lang.model.element.PackageElement;
import javax.lang.model.element.TypeElement;
import java.util.HashSet;
import java.util.Set;
public class MonitoringDTImportAdapter extends UMLPDomainImportAdapter {
  public MonitoringDTImportAdapter(PrinterAdapter parentAdapter) {
    super(parentAdapter);
  }

  @Override
  protected void setDomainName() {
    addDomainName("functionstructure4fenix");
    addDomainName("monitoringdt");
  }
}
