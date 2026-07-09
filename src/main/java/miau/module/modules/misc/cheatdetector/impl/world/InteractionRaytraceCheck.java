package miau.module.modules.misc.cheatdetector.impl.world;

import miau.module.modules.misc.cheatdetector.Check;
import net.minecraft.entity.player.EntityPlayer;

public class InteractionRaytraceCheck extends Check {

  public String getName() {
    return "InteractionRaytraceCheck";
  }

  private float lastPitch = 0;

  public void onUpdate(EntityPlayer player) {
    if (player.isSwingInProgress
        && player.inventory.getCurrentItem() != null
        && player.inventory.getCurrentItem().getItem() instanceof net.minecraft.item.ItemBlock) {
      if (player.rotationPitch > 85 && Math.abs(player.rotationPitch - lastPitch) > 20) {
        flag(player, "Interact Snap Downward");
      }
    }
    lastPitch = player.rotationPitch;
  }
}
