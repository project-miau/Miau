package miau.module.modules.misc.cheatdetector.impl.combat;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import miau.event.impl.PacketEvent;
import miau.module.modules.misc.cheatdetector.Check;
import net.minecraft.entity.player.EntityPlayer;

/**
 * Detects AutoBlock (auto-blocking while swinging a sword). Ported and improved from Rain
 * Anticheat's AutoBlockCheck. Watches for S0BPacketAnimation (swing packet) while the player has
 * blocking status active (isBlocking()).
 */
public class AutoBlockCheck extends Check {
  private static final int FAIL_TICKS = 10;

  private final Map<UUID, Integer> autoBlockTicks = new HashMap<>();

  @Override
  public String getName() {
    return "AutoBlock";
  }

  @Override
  public void onPacket(PacketEvent e, EntityPlayer player) {
    UUID uuid = player.getUniqueID();

    // Check for sword blocking while swinging
    if (player.isSwingInProgress && player.isBlocking()) {
      int ticks = autoBlockTicks.getOrDefault(uuid, 0) + 1;
      autoBlockTicks.put(uuid, ticks);

      if (ticks > FAIL_TICKS) {
        flag(player, "Blocking while swinging (" + ticks + " ticks)");
        autoBlockTicks.put(uuid, 0);
      }
    } else {
      autoBlockTicks.remove(uuid);
    }
  }

  @Override
  public void onUpdate(EntityPlayer player) {
    // Alternative check: if swing packet was sent while blocking
    // This catches cases where the swing is visible client-side
    UUID uuid = player.getUniqueID();

    if (player.swingProgressInt == 0 && player.isSwingInProgress && player.isBlocking()) {
      int ticks = autoBlockTicks.getOrDefault(uuid, 0) + 1;
      autoBlockTicks.put(uuid, ticks);

      if (ticks > FAIL_TICKS) {
        flag(player, "Blocking while swinging (client)");
        autoBlockTicks.put(uuid, 0);
      }
    } else if (!player.isBlocking()) {
      autoBlockTicks.remove(uuid);
    }
  }

  @Override
  public void cleanup(Set<UUID> onlineUUIDs) {
    autoBlockTicks.keySet().removeIf(uuid -> !onlineUUIDs.contains(uuid));
  }
}
