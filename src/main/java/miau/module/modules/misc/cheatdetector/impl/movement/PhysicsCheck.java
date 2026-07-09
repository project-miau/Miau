package miau.module.modules.misc.cheatdetector.impl.movement;

import miau.event.impl.PacketEvent;
import miau.module.modules.misc.cheatdetector.Check;
import net.minecraft.entity.player.EntityPlayer;

public class PhysicsCheck extends Check {
  @Override
  public String getName() {
    return "PhysicsCheck";
  }

  private double lastX, lastY, lastZ;
  private boolean hasLast = false;

  @Override
  public void onUpdate(EntityPlayer player) {
    if (!hasLast) {
      lastX = player.posX;
      lastY = player.posY;
      lastZ = player.posZ;
      hasLast = true;
      return;
    }
    double dX = player.posX - lastX;
    double dY = player.posY - lastY;
    double dZ = player.posZ - lastZ;
    double distSq = dX * dX + dZ * dZ;

    if (distSq > 1.5 && !player.isRiding()) {
      flag(player, "High Speed (Dist: " + String.format("%.2f", Math.sqrt(distSq)) + ")");
    }

    if (!player.onGround && dY == 0 && Math.sqrt(distSq) > 0.1 && player.ticksExisted > 20) {
      flag(player, "Hover/Flight");
    }

    lastX = player.posX;
    lastY = player.posY;
    lastZ = player.posZ;
  }

  @Override
  public void onPacket(PacketEvent event, EntityPlayer player) {}
}
