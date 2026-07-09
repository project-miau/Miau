package miau.module.modules.misc.cheatdetector.impl.world.placementanalysis;

import miau.event.impl.PacketEvent;
import miau.module.modules.misc.cheatdetector.Check;
import net.minecraft.entity.player.EntityPlayer;

public class ScaffoldRotationSpeedCheck extends Check {
  @Override
  public String getName() {
    return "ScaffoldRotationSpeedCheck";
  }

  @Override
  public void onUpdate(EntityPlayer player) {}

  @Override
  public void onPacket(PacketEvent event, EntityPlayer player) {}
}
