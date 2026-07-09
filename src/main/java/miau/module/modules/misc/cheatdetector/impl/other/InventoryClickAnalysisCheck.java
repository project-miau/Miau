package miau.module.modules.misc.cheatdetector.impl.other;

import miau.event.impl.PacketEvent;
import miau.module.modules.misc.cheatdetector.Check;
import net.minecraft.entity.player.EntityPlayer;

public class InventoryClickAnalysisCheck extends Check {
  @Override
  public String getName() {
    return "InventoryClickAnalysisCheck";
  }

  private int lastSlot = -1;
  private int slotChanges = 0;
  private long lastReset = 0;

  @Override
  public void onUpdate(EntityPlayer player) {
    if (player.inventory.currentItem != lastSlot) {
      slotChanges++;
      lastSlot = player.inventory.currentItem;
    }

    long now = System.currentTimeMillis();
    if (now - lastReset > 1000) {
      if (slotChanges > 10) {
        flag(player, "Fast Inventory/Scroll (Changes/sec: " + slotChanges + ")");
      }
      slotChanges = 0;
      lastReset = now;
    }

    if (player.rotationPitch > 80
        && (player.posX != player.lastTickPosX || player.posZ != player.lastTickPosZ)) {
      if (slotChanges > 2) {
        flag(player, "Inventory Walk Heuristic");
      }
    }
  }

  @Override
  public void onPacket(PacketEvent event, EntityPlayer player) {}
}
