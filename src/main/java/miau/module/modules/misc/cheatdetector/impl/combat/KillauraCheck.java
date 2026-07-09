package miau.module.modules.misc.cheatdetector.impl.combat;

import miau.module.modules.misc.cheatdetector.Check;
import net.minecraft.entity.player.EntityPlayer;

public class KillauraCheck extends Check {
  private float lastYaw = 0;

  public String getName() {
    return "Killaura";
  }

  public void onUpdate(EntityPlayer player) {
    float yaw = player.rotationYawHead;
    float deltaYaw = Math.abs(yaw - lastYaw);

    if (player.isSwingInProgress && deltaYaw > 180) {
      flag(player, "Aura Snap");
    }
    lastYaw = yaw;
  }
}
