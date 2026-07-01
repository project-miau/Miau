package miau.module.modules.misc.cheatdetector.impl.combat.aim.subchecks;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import miau.module.modules.misc.cheatdetector.Check;
import net.minecraft.entity.player.EntityPlayer;

/**
 * Silent aim snap/return detection — detects yaw bursts that start >20° away from every nearby
 * player and settle inside someone's hitbox bearing.
 *
 * <p>The idea: cheats leave the camera untouched but swap yaw inside outgoing packets. A large yaw
 * burst that lands precisely on a target after starting far off is a silent aim snap. The mirror
 * burst off the target right after is the return leg.
 */
public class AimB extends Check {
  private static final float BURST_STEP_MIN = 7.0F;
  private static final float BURST_QUIET = 2.5F;
  private static final int BURST_MAX_TICKS = 7;
  private static final float BURST_SUM_MIN = 20.0F;
  private static final float SNAP_PRE_ERROR_MIN = 20.0F;
  private static final int SNAP_MIN_HITS = 3;
  private static final long RETURN_PAIR_TICKS = 8L;
  private static final double TARGET_RANGE_SQ = 36.0D;

  private static class State {
    float lastYaw;
    boolean hasRotation;
    int burstTicks; // 0=idle, -1=invalidated, >0=in burst
    float burstSum;
    float burstDir;
    float preBurstYaw;
    int quietTicks;
    int snapHits;
    int snapMisses;
    long lastSnapHitTick = Long.MIN_VALUE;
    float lastPitch;
  }

  private final Map<UUID, State> states = new HashMap<>();

  @Override
  public String getName() {
    return "Aim B";
  }

  @Override
  public void onUpdate(EntityPlayer player) {
    UUID uuid = player.getUniqueID();
    State st = states.computeIfAbsent(uuid, k -> new State());

    float yaw = player.rotationYawHead;
    float pitch = player.rotationPitch;

    if (!st.hasRotation) {
      st.lastYaw = yaw;
      st.lastPitch = pitch;
      st.hasRotation = true;
      return;
    }

    float yawChange = wrapDegrees(yaw - st.lastYaw);
    st.lastYaw = yaw;
    st.lastPitch = pitch;

    // Teleport / lag guard
    double dx = player.posX - player.prevPosX;
    double dz = player.posZ - player.prevPosZ;
    if (dx * dx + dz * dz > 25.0D) {
      st.burstTicks = 0;
      st.snapHits = 0;
      st.snapMisses = 0;
      return;
    }

    // Burst machine
    float absYaw = Math.abs(yawChange);

    if (st.burstTicks > 0) {
      boolean sameDir = yawChange * st.burstDir >= 0.0F;
      if (absYaw < BURST_QUIET) {
        if (st.burstSum >= BURST_SUM_MIN) {
          evaluateBurst(player, st);
        }
        st.burstTicks = 0;
        st.quietTicks = 1;
      } else if (sameDir) {
        ++st.burstTicks;
        st.burstSum += absYaw;
        if (st.burstTicks > BURST_MAX_TICKS) {
          st.burstTicks = -1; // sustained turn
        }
      } else if (absYaw > BURST_STEP_MIN) {
        st.burstTicks = 1;
        st.burstSum = absYaw;
        st.burstDir = yawChange;
        st.preBurstYaw = yaw - yawChange;
        st.quietTicks = 0;
      } else {
        st.burstTicks = 0;
        st.quietTicks = 0;
      }
    } else if (st.burstTicks == -1) {
      if (absYaw < BURST_QUIET) {
        st.burstTicks = 0;
        st.quietTicks = 1;
      }
    } else {
      if (absYaw > BURST_STEP_MIN && st.quietTicks >= 2) {
        st.burstTicks = 1;
        st.burstSum = absYaw;
        st.burstDir = yawChange;
        st.preBurstYaw = yaw - yawChange;
        st.quietTicks = 0;
      } else if (absYaw < BURST_QUIET) {
        ++st.quietTicks;
      } else {
        st.quietTicks = 0;
      }
    }
  }

  private void evaluateBurst(EntityPlayer attacker, State st) {
    if (attacker.getEntityWorld() == null) return;

    // Find the nearest target
    EntityPlayer bestTarget = null;
    float bestErr = Float.MAX_VALUE;
    float bestPre = 0;

    for (EntityPlayer target : attacker.getEntityWorld().playerEntities) {
      if (target == attacker || target == mc.thePlayer) continue;
      if (target.isDead) continue;
      double dx = target.posX - attacker.posX;
      double dy = target.posY - attacker.posY;
      double dz = target.posZ - attacker.posZ;
      if (dx * dx + dy * dy + dz * dz > TARGET_RANGE_SQ) continue;

      float err = minInsideError(attacker, target, st.lastYaw);
      if (err < bestErr) {
        bestErr = err;
        float bearingNow = bearingTo(attacker, target.posX, target.posZ);
        bestPre = Math.abs(wrapDegrees(st.preBurstYaw - bearingNow));
      }
    }

    if (bestTarget == null && bestErr == Float.MAX_VALUE) return;

    if (bestErr <= 1.5F && bestPre > SNAP_PRE_ERROR_MIN) {
      ++st.snapHits;
      st.lastSnapHitTick =
          attacker.getEntityWorld() == null ? 0L : attacker.getEntityWorld().getTotalWorldTime();
      if (st.snapHits >= SNAP_MIN_HITS && st.snapHits > st.snapMisses) {
        flag(attacker, "Silent aim snap x" + st.snapHits);
      }
    } else if (bestPre > SNAP_PRE_ERROR_MIN && bestErr > 3.0F) {
      ++st.snapMisses;
    }
  }

  private float minInsideError(EntityPlayer attacker, EntityPlayer target, float yaw) {
    double dx = target.posX - attacker.posX;
    double dz = target.posZ - attacker.posZ;
    double horizDist = Math.sqrt(dx * dx + dz * dz);
    if (horizDist < 0.5D) return Float.MAX_VALUE;

    float bearing = (float) (Math.atan2(dz, dx) * 180.0D / Math.PI) - 90.0F;
    float err = Math.abs(wrapDegrees(yaw - bearing));
    float halfWidth = (float) Math.toDegrees(Math.atan2(0.4D, horizDist));
    return Math.max(0.0F, err - halfWidth);
  }

  private static float bearingTo(EntityPlayer attacker, double x, double z) {
    double dx = x - attacker.posX;
    double dz = z - attacker.posZ;
    return (float) (Math.atan2(dz, dx) * 180.0D / Math.PI) - 90.0F;
  }

  private float wrapDegrees(float angle) {
    angle %= 360.0F;
    if (angle >= 180.0F) angle -= 360.0F;
    if (angle < -180.0F) angle += 360.0F;
    return angle;
  }

  @Override
  public void cleanup(Set<UUID> onlineUUIDs) {
    states.keySet().removeIf(uuid -> !onlineUUIDs.contains(uuid));
  }
}
