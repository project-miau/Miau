package miau.module.modules.misc.cheatdetector.impl.combat;

import miau.event.impl.PacketEvent;
import miau.module.modules.misc.cheatdetector.Check;
import net.minecraft.entity.player.EntityPlayer;

public class HeuristicsCheck extends Check {
  @Override
  public String getName() {
    return "HeuristicsCheck";
  }

  private float lastYaw = 0;

  @Override
  public void onUpdate(EntityPlayer player) {
    float yaw = player.rotationYawHead;
    float deltaYaw = Math.abs(yaw - lastYaw);

    if (player.isSwingInProgress) {
      if (deltaYaw > 180) {
        flag(player, "Aura Snap (DeltaYaw: " + String.format("%.1f", deltaYaw) + ")");
      }
    }
    lastYaw = yaw;
  }

  @Override
  public void onPacket(PacketEvent event, EntityPlayer player) {}
}
