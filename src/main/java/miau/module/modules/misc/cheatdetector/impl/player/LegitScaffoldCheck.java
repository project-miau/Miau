package miau.module.modules.misc.cheatdetector.impl.player;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import miau.event.impl.PacketEvent;
import miau.module.modules.misc.cheatdetector.Check;
import net.minecraft.block.Block;
import net.minecraft.block.BlockAir;
import net.minecraft.entity.DataWatcher;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemBlock;
import net.minecraft.network.play.server.S14PacketEntity;
import net.minecraft.network.play.server.S1CPacketEntityMetadata;
import net.minecraft.util.BlockPos;

/**
 * Merged LegitScaffold detection. Combines: - Miau's packet-level sneak detection (S14 + S1C
 * metadata) - Rain Anticheat's crouch rhythm analysis (quick crouch 1-2 ticks + swing timing) -
 * Yaw/pitch snap detection from old Miau Scaffold subchecks
 */
public class LegitScaffoldCheck extends Check {
  // Miau's packet-based sneak tracking
  private final Map<UUID, Boolean> shouldSneakMap = new HashMap<>();
  private final Map<UUID, Integer> sneakTicksMap = new HashMap<>();
  private final Map<UUID, Integer> bufferMap = new HashMap<>();

  // Rain's crouch rhythm analysis
  private final Map<UUID, Long> lastCrouchStart = new HashMap<>();
  private final Map<UUID, Long> lastCrouchEnd = new HashMap<>();
  private final Map<UUID, Boolean> wasSneaking = new HashMap<>();
  private final Map<UUID, Long> lastSwingTick = new HashMap<>();
  private final Map<UUID, List<Integer>> crouchDurations = new HashMap<>();
  private final Map<UUID, Integer> violationLevels = new HashMap<>();

  // Yaw/pitch snap tracking
  private final Map<UUID, Float> cachedYawMap = new HashMap<>();
  private final Map<UUID, Integer> yawSnapCountMap = new HashMap<>();
  private final Map<UUID, Float> cachedPitchMap = new HashMap<>();
  private final Map<UUID, Double> pitchSnapBufferMap = new HashMap<>();

  @Override
  public String getName() {
    return "Legit scaffold";
  }

  @Override
  public void onPacket(PacketEvent e, EntityPlayer player) {
    UUID uuid = player.getUniqueID();
    boolean shouldSneak = shouldSneakMap.getOrDefault(uuid, false);
    int sneakTicks = sneakTicksMap.getOrDefault(uuid, 0);
    int buffer = bufferMap.getOrDefault(uuid, 0);

    if (e.getPacket() instanceof S14PacketEntity) {
      S14PacketEntity s14 = (S14PacketEntity) e.getPacket();
      if (s14.getEntity(mc.theWorld) != null
          && s14.getEntity(mc.theWorld).getEntityId() == player.getEntityId()) {
        if (s14.getOnGround()) {
          final Block block =
              mc.theWorld
                  .getBlockState(new BlockPos(player.posX, player.posY - 1, player.posZ))
                  .getBlock();

          if (block instanceof BlockAir) {
            shouldSneak = true;
            sneakTicks++;
          } else {
            shouldSneak = false;
            sneakTicks = 0;
          }
        } else {
          shouldSneak = false;
          sneakTicks = 0;
        }
      }
    }

    if (e.getPacket() instanceof S1CPacketEntityMetadata) {
      S1CPacketEntityMetadata wrapper = (S1CPacketEntityMetadata) e.getPacket();
      if (wrapper.func_149376_c() != null && wrapper.getEntityId() == player.getEntityId()) {
        for (final DataWatcher.WatchableObject object : wrapper.func_149376_c()) {
          if (object.getObject() instanceof Byte && (byte) object.getObject() == 2) {
            if (shouldSneak && sneakTicks <= 2) {
              buffer = Math.min(10000, buffer + 1);

              if (buffer > 10) {
                flag(player, "Fast sneak on edge");
                shouldSneak = false;
                sneakTicks = 0;
                buffer = 0;
              }
            } else {
              buffer = Math.max(0, buffer - 5);
            }
          }
        }
      }
    }

    shouldSneakMap.put(uuid, shouldSneak);
    sneakTicksMap.put(uuid, sneakTicks);
    bufferMap.put(uuid, buffer);
  }

  @Override
  public void onUpdate(EntityPlayer player) {
    UUID uuid = player.getUniqueID();
    long tick = player.getEntityWorld() == null ? 0L : player.getEntityWorld().getTotalWorldTime();

    // === Rain's crouch rhythm analysis ===
    boolean currSneak = player.isSneaking();
    boolean prevSneak = wasSneaking.getOrDefault(uuid, false);

    if (currSneak && !prevSneak) {
      lastCrouchStart.put(uuid, tick);
    } else if (!currSneak && prevSneak) {
      lastCrouchEnd.put(uuid, tick);
      long start = lastCrouchStart.getOrDefault(uuid, tick - 1L);
      int duration = (int) (tick - start);
      List<Integer> durations = crouchDurations.computeIfAbsent(uuid, k -> new ArrayList<>());
      durations.add(0, duration);
      if (durations.size() > 5) {
        durations.remove(5);
      }
    }
    wasSneaking.put(uuid, currSneak);

    // Track swings
    if (player.isSwingInProgress) {
      lastSwingTick.put(uuid, tick);
    }

    // Check for scaffold pattern: looking down + holding blocks + on ground
    boolean holdingBlock =
        player.getHeldItem() != null && player.getHeldItem().getItem() instanceof ItemBlock;

    if (player.rotationPitch >= 60.0F && holdingBlock && player.onGround) {
      long end = lastCrouchEnd.getOrDefault(uuid, 0L);
      long swing = lastSwingTick.getOrDefault(uuid, Long.MIN_VALUE);
      int crouchDuration = (int) (end - lastCrouchStart.getOrDefault(uuid, end - 1L));
      boolean quickCrouch = crouchDuration >= 1 && crouchDuration <= 2;
      boolean swingTiming = swing >= end && swing <= end + 1L;
      List<Integer> durations = crouchDurations.getOrDefault(uuid, new ArrayList<>());
      boolean consistent =
          durations.size() >= 3
              && durations.get(0) <= 2
              && durations.get(1) <= 2
              && durations.get(2) <= 2;

      if (quickCrouch && swingTiming && consistent) {
        int vl = violationLevels.getOrDefault(uuid, 0) + 1;
        violationLevels.put(uuid, vl);
        flag(player, "Crouch-bridge rhythm x" + vl);
      }
    }

    // === Yaw snap detection (from old ScaffoldA) ===
    boolean bridgingCheck =
        player.rotationPitch > 70
            && player.getHeldItem() != null
            && player.getHeldItem().getItem() instanceof ItemBlock;

    int yawSnapCount = yawSnapCountMap.getOrDefault(uuid, 0);
    float cachedYaw = cachedYawMap.getOrDefault(uuid, 0.0f);

    if (bridgingCheck && Math.abs(player.rotationYawHead - cachedYaw) > 45) {
      yawSnapCount++;
      if (yawSnapCount > 2) {
        flag(player, "Suspicious yaw snap while bridging");
        yawSnapCount = 0;
      }
    } else if (!bridgingCheck) {
      yawSnapCount = 0;
    }
    cachedYawMap.put(uuid, player.rotationYawHead);
    yawSnapCountMap.put(uuid, yawSnapCount);

    // === Pitch snap detection (from old ScaffoldB) ===
    double pitchBuffer = pitchSnapBufferMap.getOrDefault(uuid, 0.0);
    float cachedPitch = cachedPitchMap.getOrDefault(uuid, 0.0f);

    if (bridgingCheck && player.isSwingInProgress && player.onGround) {
      double pitchDelta = Math.abs(player.rotationPitch - cachedPitch);
      if (pitchDelta > 2) {
        pitchBuffer += 1.4;
        if (pitchBuffer > 4) {
          flag(player, "Suspicious pitch snap while bridging");
          pitchBuffer = 0;
        }
      } else {
        pitchBuffer = Math.max(0, pitchBuffer - 0.8);
      }
    }
    cachedPitchMap.put(uuid, player.rotationPitch);
    pitchSnapBufferMap.put(uuid, pitchBuffer);
  }

  @Override
  public void cleanup(java.util.Set<UUID> onlineUUIDs) {
    shouldSneakMap.keySet().removeIf(uuid -> !onlineUUIDs.contains(uuid));
    sneakTicksMap.keySet().removeIf(uuid -> !onlineUUIDs.contains(uuid));
    bufferMap.keySet().removeIf(uuid -> !onlineUUIDs.contains(uuid));
    lastCrouchStart.keySet().removeIf(uuid -> !onlineUUIDs.contains(uuid));
    lastCrouchEnd.keySet().removeIf(uuid -> !onlineUUIDs.contains(uuid));
    wasSneaking.keySet().removeIf(uuid -> !onlineUUIDs.contains(uuid));
    lastSwingTick.keySet().removeIf(uuid -> !onlineUUIDs.contains(uuid));
    crouchDurations.keySet().removeIf(uuid -> !onlineUUIDs.contains(uuid));
    violationLevels.keySet().removeIf(uuid -> !onlineUUIDs.contains(uuid));
    cachedYawMap.keySet().removeIf(uuid -> !onlineUUIDs.contains(uuid));
    yawSnapCountMap.keySet().removeIf(uuid -> !onlineUUIDs.contains(uuid));
    cachedPitchMap.keySet().removeIf(uuid -> !onlineUUIDs.contains(uuid));
    pitchSnapBufferMap.keySet().removeIf(uuid -> !onlineUUIDs.contains(uuid));
  }
}
