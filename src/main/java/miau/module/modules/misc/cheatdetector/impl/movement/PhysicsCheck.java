package miau.module.modules.misc.cheatdetector.impl.movement;

import miau.event.impl.PacketEvent;
import miau.module.modules.misc.cheatdetector.Check;
import net.minecraft.entity.player.EntityPlayer;

public class PhysicsCheck extends Check {
  @Override
  public String getName() {
    return "PhysicsCheck";
  }

  @Override
  public void onUpdate(EntityPlayer player) {}

  @Override
  public void onPacket(PacketEvent event, EntityPlayer player) {}
}
