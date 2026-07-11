package miau.module.modules.combat.killaura.rotation;

import miau.event.impl.UpdateEvent;
import miau.module.modules.combat.KillAura;
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

    if (this.killAura.getAttackData() == null || this.killAura.getAttackData().getEntity() == null) {
      return lastRots;
    }

    float[] rot =
        RotationUtil.getRotationsWithBackup(
            this.killAura.getAttackData().getEntity(),
            0.0F,
            0.0F,
            mc.thePlayer.rotationYaw,
            mc.thePlayer.rotationPitch,
            this.killAura.attackRange.getValue(),
            true,
            true);

    if (rot != null) {
      float[] smooth =
          RotationUtil.smoothRotation(
              mc.thePlayer.rotationYaw,
              mc.thePlayer.rotationPitch,
              rot[0],
              rot[1],
              30,
              10.0F);

      mc.thePlayer.rotationYaw = smooth[0];
      mc.thePlayer.rotationPitch = smooth[1];
      mc.thePlayer.rotationYawHead = smooth[0];
      mc.thePlayer.renderYawOffset = smooth[0];
      event.setPervRotation(smooth[0], 1);
      return new float[] {smooth[0], smooth[1]};
    }
    return lastRots;
  }
}
