package miau.module.modules.misc.cheatdetector.impl.world.placementanalysis;

import miau.event.impl.PacketEvent;
import miau.module.modules.misc.cheatdetector.Check;
import net.minecraft.entity.player.EntityPlayer;

public class ScaffoldAngleSnapCheck extends Check {
  @Override
  public String getName() {
    return "ScaffoldAngleSnapCheck";
  }

  @Override
  public void onUpdate(EntityPlayer player) {
    if (player.inventory.getCurrentItem() != null
        && player.inventory.getCurrentItem().getItem() instanceof net.minecraft.item.ItemBlock) {
      float yaw = player.rotationYawHead % 360;
      if (yaw < 0) yaw += 360;

      if (player.rotationPitch > 70) {
        float rem = yaw % 45;
        if (rem == 0.0f || rem == 45.0f) {
          flag(player, "Angle Snap (Exact 45-deg yaw: " + yaw + ")");
        }
      }
    }
  }

  @Override
  public void onPacket(PacketEvent event, EntityPlayer player) {}
}
