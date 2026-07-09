package miau.module.modules.misc.cheatdetector.impl.world;

import miau.module.modules.misc.cheatdetector.Check;
import net.minecraft.entity.player.EntityPlayer;

public class BreakSpeedLimiterCheck extends Check {

  public String getName() {
    return "BreakSpeedLimiterCheck";
  }

  private float lastYaw = 0;
  private float lastPitch = 0;
  private int snaps = 0;
  private long lastReset = 0;

  public void onUpdate(EntityPlayer player) {
    if (player.isSwingInProgress) {
      float deltaYaw = Math.abs(player.rotationYawHead - lastYaw);
      float deltaPitch = Math.abs(player.rotationPitch - lastPitch);
      if (deltaYaw > 40 || deltaPitch > 40) {
        snaps++;
      }
    }

    long now = System.currentTimeMillis();
    if (now - lastReset > 1000) {
      if (snaps > 15) {
        flag(player, "Nuker/FastBreak Snaps: " + snaps);
      }
      snaps = 0;
      lastReset = now;
    }

    lastYaw = player.rotationYawHead;
    lastPitch = player.rotationPitch;
  }
}
