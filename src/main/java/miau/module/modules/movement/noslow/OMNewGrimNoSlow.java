package miau.module.modules.movement.noslow;

import miau.event.impl.UpdateEvent;
import miau.module.modules.movement.NoSlow;

public class OMNewGrimNoSlow extends NoSlowMode {
  private int ticks = 0;

  public OMNewGrimNoSlow(String name, NoSlow parent) {
    super(name, parent);
  }

  @Override
  public void onEnable() {
    ticks = 0;
  }

  @Override
  public void onDisable() {
    ticks = 0;
  }

  public boolean shouldCancelSlowdown() {
    if (!this.getParent().isAnyActive()) {
      ticks = 0;
      return false;
    }
    ticks++;
    if (ticks >= 2) {
      ticks = 0;
      return true;
    }
    return false;
  }

  @Override
  public void onUpdate(UpdateEvent event) {
  }
}
