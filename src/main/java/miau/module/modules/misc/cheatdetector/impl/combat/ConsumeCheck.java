package miau.module.modules.misc.cheatdetector.impl.combat;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import miau.event.impl.PacketEvent;
import miau.module.modules.misc.cheatdetector.Check;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemBucketMilk;
import net.minecraft.item.ItemFood;
import net.minecraft.item.ItemPotion;
import net.minecraft.item.ItemStack;
import net.minecraft.network.play.server.S0BPacketAnimation;

/**
 * Detects players attacking/swinging while consuming items (food/potions/milk). Replaces and
 * improves upon the old InvalidInteract check. Ported from Rain Anticheat's KillauraCheck consume
 * component.
 *
 * <p>Cheat signature: killaura that attacks while eating/drinking. Legit players cannot swing while
 * consuming items.
 */
public class ConsumeCheck extends Check {
  private static final int EAT_TIMEOUT = 33;
  private static final int MIN_USE_TIME = 6;
  private static final int CONSUME_FAIL_VL = 8;

  private final Map<UUID, Integer> useItemTicks = new HashMap<>();
  private final Map<UUID, Long> lastEatTick = new HashMap<>();
  private final Map<UUID, Integer> consumeVlMap = new HashMap<>();

  @Override
  public String getName() {
    return "Consume";
  }

  @Override
  public void onPacket(PacketEvent e, EntityPlayer player) {
    UUID uuid = player.getUniqueID();

    if (!(e.getPacket() instanceof S0BPacketAnimation)) return;
    S0BPacketAnimation anim = (S0BPacketAnimation) e.getPacket();
    if (anim.getEntityID() != player.getEntityId()) return;

    // Get current state
    boolean isUsingItem = player.isUsingItem();
    ItemStack heldItem = player.getHeldItem();
    boolean isConsumable = heldItem != null && isConsumable(heldItem.getItem());

    int useTime = useItemTicks.getOrDefault(uuid, 0);
    long lastEat = lastEatTick.getOrDefault(uuid, 0L);
    long tick = player.getEntityWorld() == null ? 0L : player.getEntityWorld().getTotalWorldTime();
    long sinceLastEat = tick - lastEat;

    // Check if swinging while consuming
    if (isUsingItem && isConsumable && useTime > MIN_USE_TIME && sinceLastEat < EAT_TIMEOUT) {
      int vl = consumeVlMap.getOrDefault(uuid, 0) + 1;
      consumeVlMap.put(uuid, vl);

      if (vl >= CONSUME_FAIL_VL) {
        flag(
            player,
            "Swinging while consuming ("
                + heldItem.getItem().getItemStackDisplayName(heldItem)
                + ")");
        consumeVlMap.put(uuid, 0);
      }
    }
  }

  @Override
  public void onUpdate(EntityPlayer player) {
    UUID uuid = player.getUniqueID();
    ItemStack heldItem = player.getHeldItem();
    boolean isUsingItem = player.isUsingItem();
    boolean isConsumable = heldItem != null && isConsumable(heldItem.getItem());

    int useTime = useItemTicks.getOrDefault(uuid, 0);

    if (isUsingItem && isConsumable) {
      useItemTicks.put(uuid, useTime + 1);
    } else {
      if (useTime > 0) {
        // Just stopped consuming, record the tick
        lastEatTick.put(
            uuid,
            player.getEntityWorld() == null ? 0L : player.getEntityWorld().getTotalWorldTime());
      }
      useItemTicks.remove(uuid);
    }
  }

  @Override
  public void cleanup(Set<UUID> onlineUUIDs) {
    useItemTicks.keySet().removeIf(uuid -> !onlineUUIDs.contains(uuid));
    lastEatTick.keySet().removeIf(uuid -> !onlineUUIDs.contains(uuid));
    consumeVlMap.keySet().removeIf(uuid -> !onlineUUIDs.contains(uuid));
  }

  private boolean isConsumable(Item item) {
    return item instanceof ItemFood || item instanceof ItemPotion || item instanceof ItemBucketMilk;
  }
}
