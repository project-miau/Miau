package miau.module.modules.movement.speeds;

import miau.event.impl.LivingUpdateEvent;
import miau.module.modules.movement.Speed;

public class LegitSpeed extends SpeedMode {
  public LegitSpeed(String name, Speed parent) {
    super(name, parent);
  }

  @Override
  public void onLivingUpdate(LivingUpdateEvent event) {}
}
