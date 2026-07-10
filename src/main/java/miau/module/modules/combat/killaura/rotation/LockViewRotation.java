package miau.module.modules.combat.killaura.rotation;

import miau.event.impl.UpdateEvent;
import miau.module.modules.combat.KillAura;
import miau.util.math.RandomUtil;
import miau.util.player.RotationUtil;
import net.minecraft.client.Minecraft;

public class LockViewRotation extends RotationMode {
  private static final Minecraft mc = Minecraft.getMinecraft();

  public LockViewRotation(KillAura killAura) {
    super(killAura, "LOCK_VIEW");
  }

  @Override
  public float[] processRotations(
      float[] targetRots, float[] lastRots, double rotSpeed, UpdateEvent event) {
    float[] lockViewRots =
        RotationUtil.getRotationsToBox(
            this.killAura.getAttackData().getBox(),
            event.getYaw(),
            event.getPitch(),
            (float) this.killAura.angleStep.getValue() + RandomUtil.nextFloat(-5.0F, 5.0F),
            (float) this.killAura.smoothing.getValue() / 100.0F);

    if (lockViewRots != null) {
      mc.thePlayer.rotationYaw = lockViewRots[0];
      mc.thePlayer.rotationPitch = lockViewRots[1];
      mc.thePlayer.rotationYawHead = lockViewRots[0];
      mc.thePlayer.renderYawOffset = lockViewRots[0];
      event.setPervRotation(lockViewRots[0], 1);
      return new float[] {lockViewRots[0], lockViewRots[1]};
    }
    return lastRots;
  }
}
