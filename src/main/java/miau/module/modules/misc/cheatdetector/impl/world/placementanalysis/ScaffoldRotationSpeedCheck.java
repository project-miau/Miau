package miau.module.modules.misc.cheatdetector.impl.world.placementanalysis;

import miau.module.modules.misc.cheatdetector.Check;
import net.minecraft.entity.player.EntityPlayer;

public class ScaffoldRotationSpeedCheck extends Check {

  public String getName() {
    return "ScaffoldRotationSpeedCheck";
  }

  private float lastYaw = 0;
  private float lastPitch = 0;

  public void onUpdate(EntityPlayer player) {
    if (player.inventory.getCurrentItem() != null
        && player.inventory.getCurrentItem().getItem() instanceof net.minecraft.item.ItemBlock) {
      float dYaw = Math.abs(player.rotationYawHead - lastYaw);
      float dPitch = Math.abs(player.rotationPitch - lastPitch);

      if (dYaw > 100 && dPitch > 40 && player.rotationPitch > 60) {
        flag(player, "Tower/Scaffold Rotation Snap");
      }
    }
    lastYaw = player.rotationYawHead;
    lastPitch = player.rotationPitch;
  }
}
