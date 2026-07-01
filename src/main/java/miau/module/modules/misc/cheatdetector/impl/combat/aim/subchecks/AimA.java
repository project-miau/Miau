package miau.module.modules.misc.cheatdetector.impl.combat.aim.subchecks;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import miau.module.modules.misc.cheatdetector.Check;
import net.minecraft.entity.player.EntityPlayer;

/**
 * Rotation heuristic analysis: detects "robotized" aim patterns. Ported from Rain Anticheat's
 * KillauraCheck AimBasicCheck.
 *
 * <p>Counts how often yaw changes are simultaneously large and nearly identical — a signature of
 * aimbots that maintain consistent rotation deltas.
 */
public class AimA extends Check {
  private static final int WINDOW_SIZE = 10;
  private static final float QUANTUM = 1.40625F;

  private final Map<UUID, List<Float>> yawChangeWindows = new HashMap<>();
  private final Map<UUID, Integer> snapStreakMap = new HashMap<>();
  private final Map<UUID, Float> lastYawMap = new HashMap<>();
  private final Map<UUID, Float> lastPitchMap = new HashMap<>();
  private final Map<UUID, Long> lastSwingMap = new HashMap<>();

  @Override
  public String getName() {
    return "Aim A";
  }

  @Override
  public void onUpdate(EntityPlayer player) {
    UUID uuid = player.getUniqueID();
    long tick = player.getEntityWorld() == null ? 0L : player.getEntityWorld().getTotalWorldTime();

    // Track combat window
    if (player.isSwingInProgress) {
      lastSwingMap.put(uuid, tick);
    }

    long lastSwing = lastSwingMap.getOrDefault(uuid, Long.MIN_VALUE);
    if (lastSwing == Long.MIN_VALUE || tick - lastSwing > 70L) {
      return; // Out of combat window
    }

    float yaw = player.rotationYawHead;
    float pitch = player.rotationPitch;

    if (!lastYawMap.containsKey(uuid)) {
      lastYawMap.put(uuid, yaw);
      lastPitchMap.put(uuid, pitch);
      return;
    }

    float prevYaw = lastYawMap.get(uuid);
    float prevPitch = lastPitchMap.get(uuid);
    float yawChange = wrapDegrees(yaw - prevYaw);
    float pitchChange = wrapDegrees(pitch - prevPitch);

    lastYawMap.put(uuid, yaw);
    lastPitchMap.put(uuid, pitch);

    // Skip zero-delta events
    if (yawChange == 0 && pitchChange == 0) return;

    float absYawChange = Math.abs(yawChange);
    List<Float> window = yawChangeWindows.computeIfAbsent(uuid, k -> new ArrayList<>());
    window.add(absYawChange);

    if (window.size() >= WINDOW_SIZE) {
      analyzeWindow(player, uuid, window);
      window.clear();
    }
  }

  private void analyzeWindow(EntityPlayer player, UUID uuid, List<Float> window) {
    float yawChangeFirst = window.get(0);
    float oldYawChange = yawChangeFirst;
    int machineKnownMovement = 0;
    int constantRotations = 0;
    int robotizedAmount = 0;
    int bigSwingUp = 0;
    int bigSwingDown = 0;

    for (float yawChange : window) {
      float robotized = Math.abs(yawChange - yawChangeFirst);
      float diffBetweenYawChanges = yawChange - oldYawChange;

      if (robotized < QUANTUM * 1.5F && yawChange > QUANTUM * 2.0F) {
        ++robotizedAmount;
      }
      if (robotized < QUANTUM && yawChange > QUANTUM * 3.0F) {
        ++machineKnownMovement;
      }
      if (robotized < QUANTUM * 0.5F && yawChange > QUANTUM * 2.5F) {
        ++constantRotations;
      }
      if (diffBetweenYawChanges > 12.0F) {
        ++bigSwingUp;
      }
      if (diffBetweenYawChanges < -12.0F) {
        ++bigSwingDown;
      }
      oldYawChange = yawChange;
    }

    // Heuristic aim patterns
    if (machineKnownMovement > 8) {
      flag(player, "Robotized aim (sync pattern)");
    }
    if (constantRotations > 6) {
      flag(player, "Constant rotation pattern");
    }
    if (robotizedAmount > 8) {
      flag(player, "Synchronized aim movement");
    }

    // Snap pattern: big swings in both directions with persistence
    int snapStreak = snapStreakMap.getOrDefault(uuid, 0);
    if (bigSwingUp > 1 && bigSwingDown > 1 && bigSwingUp + bigSwingDown > 4) {
      snapStreak++;
      snapStreakMap.put(uuid, snapStreak);
      if (snapStreak > 2) {
        flag(player, "Sustained snap pattern");
      }
    } else {
      snapStreakMap.put(uuid, 0);
    }
  }

  private float wrapDegrees(float angle) {
    angle %= 360.0F;
    if (angle >= 180.0F) angle -= 360.0F;
    if (angle < -180.0F) angle += 360.0F;
    return angle;
  }

  @Override
  public void cleanup(Set<UUID> onlineUUIDs) {
    yawChangeWindows.keySet().removeIf(uuid -> !onlineUUIDs.contains(uuid));
    snapStreakMap.keySet().removeIf(uuid -> !onlineUUIDs.contains(uuid));
    lastYawMap.keySet().removeIf(uuid -> !onlineUUIDs.contains(uuid));
    lastPitchMap.keySet().removeIf(uuid -> !onlineUUIDs.contains(uuid));
    lastSwingMap.keySet().removeIf(uuid -> !onlineUUIDs.contains(uuid));
  }
}
