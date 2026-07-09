package miau.module.modules.misc.cheatdetector.impl.world;

import miau.event.impl.PacketEvent;
import miau.module.modules.misc.cheatdetector.Check;
import net.minecraft.entity.player.EntityPlayer;

public class BreakSpeedLimiterCheck extends Check {
  @Override
  public String getName() {
    return "BreakSpeedLimiterCheck";
  }

  @Override
  public void onUpdate(EntityPlayer player) {}

  @Override
  public void onPacket(PacketEvent event, EntityPlayer player) {}
}
