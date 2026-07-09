package miau.module.modules.misc.cheatdetector.impl.other;

import miau.event.impl.PacketEvent;
import miau.module.modules.misc.cheatdetector.Check;
import net.minecraft.entity.player.EntityPlayer;

public class ProtocolScannerCheck extends Check {
  @Override
  public String getName() {
    return "ProtocolScannerCheck";
  }

  @Override
  public void onUpdate(EntityPlayer player) {
    if (player.rotationPitch > 90 || player.rotationPitch < -90) {
      flag(player, "Impossible Pitch: " + player.rotationPitch);
    }
  }

  @Override
  public void onPacket(PacketEvent event, EntityPlayer player) {}
}
