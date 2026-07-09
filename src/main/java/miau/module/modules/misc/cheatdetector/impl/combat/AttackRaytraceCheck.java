package miau.module.modules.misc.cheatdetector.impl.combat;

import miau.event.impl.PacketEvent;
import miau.module.modules.misc.cheatdetector.Check;
import net.minecraft.entity.player.EntityPlayer;

public class AttackRaytraceCheck extends Check {
  @Override
  public String getName() {
    return "AttackRaytraceCheck";
  }

  @Override
  public void onUpdate(EntityPlayer player) {
    if (player.isSwingInProgress) {
      for (net.minecraft.entity.Entity entity : mc.theWorld.loadedEntityList) {
        if (entity != player && entity instanceof net.minecraft.entity.EntityLivingBase) {
          net.minecraft.entity.EntityLivingBase target =
              (net.minecraft.entity.EntityLivingBase) entity;
          if (target.hurtTime > 0
              && player.getDistanceToEntity(target) > 5.0) { // Leniency for latency
            flag(
                player,
                "Reach/Hitbox (Dist: "
                    + String.format("%.2f", player.getDistanceToEntity(target))
                    + ")");
          }
        }
      }
    }
  }

  @Override
  public void onPacket(PacketEvent event, EntityPlayer player) {}
}
