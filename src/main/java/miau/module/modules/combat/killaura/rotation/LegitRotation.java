package miau.module.modules.combat.killaura.rotation;

import miau.event.impl.UpdateEvent;
import miau.module.modules.combat.KillAura;
import miau.util.player.RotationUtil;

public class LegitRotation extends RotationMode {

  public LegitRotation(KillAura killAura) {
    super(killAura, "LEGIT");
  }

  @Override
  public float[] processRotations(
      float[] targetRots, float[] lastRots, double rotSpeed, UpdateEvent event) {
    if (rotSpeed != 0) {
      int ravenSpeed = Math.min(30, (int) (rotSpeed / 6.0));
      float randomPct = (float) this.killAura.smoothing.getValue();
      return RotationUtil.smoothRotation(
          lastRots[0], lastRots[1], targetRots[0], targetRots[1], ravenSpeed, randomPct);
    } else {
      return lastRots;
    }
  }
}
