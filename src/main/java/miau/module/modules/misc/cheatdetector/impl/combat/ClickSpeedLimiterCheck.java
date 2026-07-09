package miau.module.modules.misc.cheatdetector.impl.combat;

import miau.module.modules.misc.cheatdetector.Check;
import net.minecraft.entity.player.EntityPlayer;

public class ClickSpeedLimiterCheck extends Check {

  public String getName() {
    return "ClickSpeedLimiterCheck";
  }

  private int swings = 0;
  private long lastReset = 0;
  private boolean lastSwing = false;

  public void onUpdate(EntityPlayer player) {
    if (player.isSwingInProgress && !lastSwing) {
      swings++;
    }
    lastSwing = player.isSwingInProgress;

    long now = System.currentTimeMillis();
    if (now - lastReset > 1000) {
      if (swings > 22) {
        flag(player, "High CPS: " + swings);
      }
      swings = 0;
      lastReset = now;
    }
  }
}
