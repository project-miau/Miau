package miau.module.modules.combat.killaura.rotation;

import miau.event.impl.UpdateEvent;
import miau.module.modules.combat.KillAura;

public class LegitRotation extends RotationMode {

  public LegitRotation(KillAura killAura) {
    super(killAura, "LEGIT");
  }

  @Override
  public float[] processRotations(
      float[] targetRots, float[] lastRots, double rotSpeed, UpdateEvent event) {
    return lastRots;
  }
}
