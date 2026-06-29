package myau.module.modules.network;

import myau.component.PingSpoofComponent;
import myau.event.EventTarget;
import myau.event.impl.UpdateEvent;
import myau.event.types.EventType;
import myau.module.Module;
import myau.property.properties.BooleanProperty;
import myau.property.properties.IntProperty;
import myau.util.math.MathUtil;

public class PingSpoof extends Module {

  private final IntProperty delayMin = new IntProperty("delay-min", 1000, 50, 30000);
  private final IntProperty delayMax = new IntProperty("delay-max", 1500, 50, 30000);
  private final BooleanProperty teleports = new BooleanProperty("delay-teleports", false);
  private final BooleanProperty velocity = new BooleanProperty("delay-velocity", false);
  private final BooleanProperty entities = new BooleanProperty("delay-entities", false);

  public PingSpoof() {
    super("PingSpoof", false);
  }

  @Override
  public String[] getSuffix() {
    return new String[] {delayMin.getValue() + "-" + delayMax.getValue()};
  }

  @EventTarget
  public void onUpdate(UpdateEvent event) {
    if (event.getType() != EventType.PRE) return;
    if (!this.isEnabled()) return;

    int amount = (int) MathUtil.getRandom(delayMin.getValue(), delayMax.getValue());
    PingSpoofComponent.spoof(
        amount, true, velocity.getValue(), teleports.getValue(), entities.getValue());
  }
}
