package myau.property;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import myau.module.Module;

public class PropertyManager {
  public LinkedHashMap<Class<?>, ArrayList<Property<?>>> properties = new LinkedHashMap<>();

  public Property<?> getProperty(Module module, String string) {
    ArrayList<Property<?>> props = properties.get(module.getClass());
    if (props == null) return null;
    for (Property<?> property : props) {
      if (property.getName().replace("-", "").equalsIgnoreCase(string.replace("-", ""))) {
        return property;
      }
    }
    return null;
  }
}
