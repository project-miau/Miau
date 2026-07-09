package miau.module.modules.misc.cheatdetector.impl.world.placementanalysis;

import miau.event.impl.PacketEvent;
import miau.module.modules.misc.cheatdetector.Check;
import net.minecraft.entity.player.EntityPlayer;

public class ScaffoldJumpAndPlaceCheck extends Check {
  @Override
  public String getName() {
    return "ScaffoldJumpAndPlaceCheck";
  }

  private double lastY;

  @Override
  public void onUpdate(EntityPlayer player) {
    if (player.inventory.getCurrentItem() != null
        && player.inventory.getCurrentItem().getItem() instanceof net.minecraft.item.ItemBlock) {
      if (!player.onGround && player.posY > lastY && player.rotationPitch > 80) {
        double motionY = player.posY - lastY;
        if (Math.abs(motionY - 0.41999) < 0.001) {
          flag(player, "Tower Jump & Place");
        }
      }
    }
    lastY = player.posY;
  }

  @Override
  public void onPacket(PacketEvent event, EntityPlayer player) {}
}
