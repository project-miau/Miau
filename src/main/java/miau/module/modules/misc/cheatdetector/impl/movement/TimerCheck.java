package miau.module.modules.misc.cheatdetector.impl.movement;

import miau.module.modules.misc.cheatdetector.Check;
import net.minecraft.entity.player.EntityPlayer;

public class TimerCheck extends Check {

  public String getName() {
    return "TimerCheck";
  }

  private double distanceBuffer = 0;
  private long lastReset = 0;
  private double lastX, lastY, lastZ;
  private boolean hasLast = false;

  public void onUpdate(EntityPlayer player) {
    if (!hasLast) {
      lastX = player.posX;
      lastY = player.posY;
      lastZ = player.posZ;
      hasLast = true;
      return;
    }
    double dX = player.posX - lastX;
    double dZ = player.posZ - lastZ;
    distanceBuffer += Math.sqrt(dX * dX + dZ * dZ);

    long now = System.currentTimeMillis();
    if (now - lastReset > 1000) {
      if (distanceBuffer > 20.0 && !player.isRiding()) {
        flag(player, "Fast Motion/Timer (Dist/sec: " + String.format("%.1f", distanceBuffer) + ")");
      }
      distanceBuffer = 0;
      lastReset = now;
    }

    lastX = player.posX;
    lastY = player.posY;
    lastZ = player.posZ;
  }
}
