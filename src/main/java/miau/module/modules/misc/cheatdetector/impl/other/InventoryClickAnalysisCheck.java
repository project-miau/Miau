package miau.module.modules.misc.cheatdetector.impl.other;

import miau.event.impl.PacketEvent;
import miau.module.modules.misc.cheatdetector.Check;
import net.minecraft.entity.player.EntityPlayer;

public class InventoryClickAnalysisCheck extends Check {
  @Override
  public String getName() {
    return "InventoryClickAnalysisCheck";
  }

  @Override
  public void onUpdate(EntityPlayer player) {}

  @Override
  public void onPacket(PacketEvent event, EntityPlayer player) {}
}
