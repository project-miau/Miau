package miau.module.modules.misc.cheatdetector.impl.world.placementanalysis;

import miau.event.impl.PacketEvent;
import miau.module.modules.misc.cheatdetector.Check;
import net.minecraft.entity.player.EntityPlayer;

public class ScaffoldJumpAndPlaceCheck extends Check {
  @Override
  public String getName() {
    return "ScaffoldJumpAndPlaceCheck";
  }

  @Override
  public void onUpdate(EntityPlayer player) {}

  @Override
  public void onPacket(PacketEvent event, EntityPlayer player) {}
}
