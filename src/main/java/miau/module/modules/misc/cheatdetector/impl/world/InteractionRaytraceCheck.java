package miau.module.modules.misc.cheatdetector.impl.world;

import miau.event.impl.PacketEvent;
import miau.module.modules.misc.cheatdetector.Check;
import net.minecraft.entity.player.EntityPlayer;

public class InteractionRaytraceCheck extends Check {
  @Override
  public String getName() {
    return "InteractionRaytraceCheck";
  }

  @Override
  public void onUpdate(EntityPlayer player) {}

  @Override
  public void onPacket(PacketEvent event, EntityPlayer player) {}
}
