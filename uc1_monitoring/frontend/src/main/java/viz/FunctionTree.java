package viz;

import functionstructure4fenix.Function;
import functionstructure4fenix.FunctionStructure4FenixManager;
import umlp.jsweet.extension.annotation.Component;

import java.util.function.Consumer;

@Component
public class FunctionTree extends FunctionTreeTOP {

  @Override
  public Boolean hasSubfunctions(Function function) {
    return function != null && function.sizeSubFunctions() > 0;
  }

  @Override
  public Consumer<Void> selectAsset(Function function) {
    return unused -> FunctionStructure4FenixManager.getFunctionApp().setSelectedAsset(function);
  }
}
