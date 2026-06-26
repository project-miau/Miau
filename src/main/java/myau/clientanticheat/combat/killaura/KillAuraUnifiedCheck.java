package myau.clientanticheat.combat.killaura;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import myau.clientanticheat.ClientAntiCheatContext;
import myau.clientanticheat.PlayerCheckData;
import myau.clientanticheat.PlayerEligibility;
import net.minecraft.block.Block;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemBucketMilk;
import net.minecraft.item.ItemFood;
import net.minecraft.item.ItemPotion;
import net.minecraft.item.ItemStack;
import net.minecraft.util.BlockPos;
import net.minecraft.util.MathHelper;

/**
 * Unified KillAura detection — ports Rain Anticheat's advanced detection components with shared VL
 * accumulation economy.
 *
 * <p>Components: - burst machine: silent aim snap/return patterns - track component: lock-on
 * tracking while strafing - movement fix desync: body/head desync + sprint leak - consume:
 * attacking while eating/drinking
 *
 * <p>All components feed one shared VL pool. When the pool exceeds VL_LIMIT, a signal is sent.
 */
public class KillAuraUnifiedCheck {

  // ── Constants ──────────────────────────────────────────────────────────

  private static final float QUANTUM = 1.40625F;
  private static final long COMBAT_WINDOW_TICKS = 70L;
  private static final long SESSION_RESET_TICKS = 140L;

  // Shared VL economy (mirrors Rain)
  private static final float VL_LIMIT = 400.0F;
  private static final float VL_FADE_PER_TICK = 0.5F;

  // Burst machine (silent snap)
  private static final float BURST_STEP_MIN = 7.0F;
  private static final float BURST_QUIET = 2.5F;
  private static final int BURST_MAX_TICKS = 7;
  private static final float BURST_SUM_MIN = 20.0F;
  private static final float SNAP_PRE_ERROR_MIN = 20.0F;
  private static final int SNAP_MIN_HITS = 3;
  private static final float RETURN_PAIR_TICKS = 8L;
  private static final float SNAP_VL = 90.0F;
  private static final float RETURN_VL = 55.0F;

  // Silent track
  private static final int TRACK_WINDOW = 24;
  private static final float TRACK_RATIO = 0.85F;
  private static final float TRACK_LOS_MIN = 2.5F;
  private static final float TRACK_LOS_MAX = 45.0F;
  private static final double TRACK_MIN_DIST = 2.2D;
  private static final float TRACK_VL = 80.0F;

  // Movement fix desync
  private static final double MOVE_MIN_SPEED = 0.15D;
  private static final double MOVE_MAX_SPEED = 0.45D;
  private static final double MOVE_FLAT_DY = 0.001D;
  private static final double MOVE_SMOOTH_ACCEL = 0.022D;
  private static final int MOVE_WINDOW = 12;
  private static final float MOVE_MEAN_LIMIT = 7.5F;
  private static final float MOVE_DESYNC_RESIDUAL = 8.0F;
  private static final float LOCK_RESIDUAL = 13.0F;
  private static final int LOCK_HITS = 3;
  private static final double SPRINT_ACCEL = 0.08D;
  private static final double SPRINT_MIN_SPEED = 0.25D;
  private static final float SPRINT_OFFSET = 62.0F;
  private static final int SPRINT_HITS = 4;
  private static final int MOVE_DECAY_TICKS = 40;
  private static final float MOVE_VL = 70.0F;
  private static final float MOVE_LOCK_VL = 85.0F;
  private static final float MOVE_SPRINT_VL = 85.0F;

  // Consume
  private static final int EAT_TIMEOUT = 33;
  private static final int MIN_USE_TIME = 6;
  private static final int CONSUME_FAIL_VL = 8;

  // Geometry
  private static final double TARGET_RANGE_SQ = 36.0D;
  private static final double HITBOX_HALF_WIDTH = 0.4D;
  private static final int TRAIL_LEN = 5;

  // ── State ──────────────────────────────────────────────────────────────

  private final Map<UUID, State> states = new HashMap<>();
  private final Map<UUID, Trail> trails = new HashMap<>();

  // ── Internal state ─────────────────────────────────────────────────────

  private static final class State {
    // rotation stream
    float lastYaw, lastPitch;
    boolean hasRotation;
    long lastSwingTick = Long.MIN_VALUE;

    // shared VL economy (Rain-style)
    float aimVl;

    // Burst machine
    int burstTicks;
    float burstSum, burstDir, preBurstYaw;
    int quietTicks, snapHits, snapMisses;
    long lastSnapHitTick = Long.MIN_VALUE;

    // Track
    UUID lastTargetId;
    float lastBearing = Float.NaN;
    int trackSamples, trackTicks;

    // Movement fix
    double lastVelX, lastVelZ, lastMoveY;
    boolean hasVel;
    int moveSamples, moveDesyncTicks, lockDesync, sprintDesync, moveTickCounter;
    float residualSum;

    // Consume
    int useItemTicks;
    long lastEatTick;
    int consumeVl;
  }

  private static final class Trail {
    final double[] x = new double[TRAIL_LEN];
    final double[] z = new double[TRAIL_LEN];
    long lastTick = Long.MIN_VALUE;
    int size;

    void push(double px, double pz, long tick) {
      if (tick == lastTick && size > 0) return;
      System.arraycopy(x, 0, x, 1, TRAIL_LEN - 1);
      System.arraycopy(z, 0, z, 1, TRAIL_LEN - 1);
      x[0] = px;
      z[0] = pz;
      lastTick = tick;
      if (size < TRAIL_LEN) ++size;
    }
  }

  // ── Public check ───────────────────────────────────────────────────────

  public void check(
      EntityPlayer player, PlayerCheckData data, long tick, ClientAntiCheatContext context) {
    if (player == null || data == null) return;
    String name = player.getName();
    if (name == null) return;

    UUID uuid = player.getUniqueID();
    if (uuid == null) return;

    if (!PlayerEligibility.shouldCheckPlayer(player)) {
      forgetPlayer(uuid);
      return;
    }

    State st = states.computeIfAbsent(uuid, k -> new State());

    // Update trails
    Minecraft mc = Minecraft.getMinecraft();
    trail(uuid).push(player.posX, player.posZ, tick);
    if (mc.thePlayer != null) {
      trail(mc.thePlayer.getUniqueID()).push(mc.thePlayer.posX, mc.thePlayer.posZ, tick);
    }

    if (player.isRiding()) return;

    // ── Consume component ────────────────────────────────────────────
    consumeCheck(player, st, tick, name, context);

    if (player.isSwingInProgress) {
      st.lastSwingTick = tick;
    }

    float yaw = player.rotationYaw;
    float pitch = player.rotationPitch;
    if (!st.hasRotation) {
      st.lastYaw = yaw;
      st.lastPitch = pitch;
      st.hasRotation = true;
      return;
    }
    float yawChange = MathHelper.wrapAngleTo180_float(yaw - st.lastYaw);
    st.lastYaw = yaw;
    st.lastPitch = pitch;

    // Teleport/lag guard
    double moveX = player.posX - player.lastTickPosX;
    double moveY = player.posY - player.lastTickPosY;
    double moveZ = player.posZ - player.lastTickPosZ;
    if (moveX * moveX + moveZ * moveZ > 25.0D) {
      resetBurst(st);
      st.lastBearing = Float.NaN;
      st.lastTargetId = null;
      st.hasVel = false;
      st.moveSamples = 0;
      st.moveDesyncTicks = 0;
      st.residualSum = 0.0F;
      return;
    }

    // Gate: only analyze during combat windows
    if (st.lastSwingTick == Long.MIN_VALUE
        || tick < st.lastSwingTick
        || tick - st.lastSwingTick > COMBAT_WINDOW_TICKS) {
      if (st.lastSwingTick != Long.MIN_VALUE && tick - st.lastSwingTick > SESSION_RESET_TICKS) {
        resetSession(st);
      }
      return;
    }

    // Geometry + advanced components share one target scan
    List<EntityPlayer> targets = targetsNear(mc, player, tick);

    // ── Burst machine (silent snap/return) ──────────────────────────
    burstMachine(player, st, tick, yawChange, yaw, targets);

    // ── Track component (lock-on) ───────────────────────────────────
    trackComponent(player, st, yaw, targets);

    // ── Movement fix desync ─────────────────────────────────────────
    movementComponent(mc, player, st, moveX, moveY, moveZ, yaw, targets);

    // ── Shared VL economy ───────────────────────────────────────────
    if (st.aimVl > VL_LIMIT) {
      context.receiveSignal(name, "KillAura", "vl", (int) (st.aimVl / 10.0F));
      st.aimVl = 360.0F;
    }
    if (st.aimVl > 0.0F) {
      st.aimVl = Math.max(0.0F, st.aimVl - VL_FADE_PER_TICK);
    }
  }

  // ── Shared VL helpers ─────────────────────────────────────────────────

  /** Add VL to this player's shared pool. */
  private void addVl(
      State st, float vl, ClientAntiCheatContext context, String name, String detail) {
    st.aimVl += vl;
  }

  // ── Burst machine ─────────────────────────────────────────────────────

  private void burstMachine(
      EntityPlayer player,
      State st,
      long tick,
      float yawChange,
      float currentYaw,
      List<EntityPlayer> targets) {
    float absYaw = Math.abs(yawChange);

    if (st.burstTicks > 0) {
      boolean sameDir = yawChange * st.burstDir >= 0.0F;
      if (absYaw < BURST_QUIET) {
        if (st.burstSum >= BURST_SUM_MIN) {
          evaluateBurst(player, st, tick, targets, currentYaw);
        }
        resetBurst(st);
        st.quietTicks = 1;
      } else if (sameDir) {
        ++st.burstTicks;
        st.burstSum += absYaw;
        if (st.burstTicks > BURST_MAX_TICKS) {
          st.burstTicks = -1;
        }
      } else if (absYaw > BURST_STEP_MIN) {
        st.burstTicks = 1;
        st.burstSum = absYaw;
        st.burstDir = yawChange;
        st.preBurstYaw = currentYaw - yawChange;
        st.quietTicks = 0;
      } else {
        resetBurst(st);
        st.quietTicks = 0;
      }
    } else if (st.burstTicks == -1) {
      if (absYaw < BURST_QUIET) {
        resetBurst(st);
        st.quietTicks = 1;
      }
    } else {
      if (absYaw > BURST_STEP_MIN && st.quietTicks >= 2) {
        st.burstTicks = 1;
        st.burstSum = absYaw;
        st.burstDir = yawChange;
        st.preBurstYaw = currentYaw - yawChange;
        st.quietTicks = 0;
      } else if (absYaw < BURST_QUIET) {
        ++st.quietTicks;
      } else {
        st.quietTicks = 0;
      }
    }
  }

  private void evaluateBurst(
      EntityPlayer player, State st, long tick, List<EntityPlayer> targets, float currentYaw) {
    if (targets.isEmpty()) return;

    float bestErr = Float.MAX_VALUE;
    float bestPre = 0.0F;
    float bestPreInside = Float.MAX_VALUE;

    for (EntityPlayer target : targets) {
      Trail trail = trail(target.getUniqueID());
      float err = minInsideError(player, trail, currentYaw);
      if (err < bestErr) {
        bestErr = err;
        float bearingNow = bearingTo(player, trail.x[0], trail.z[0]);
        bestPre = Math.abs(MathHelper.wrapAngleTo180_float(st.preBurstYaw - bearingNow));
      }
      bestPreInside = Math.min(bestPreInside, minInsideError(player, trail, st.preBurstYaw));
    }

    if (bestErr <= QUANTUM && bestPre > SNAP_PRE_ERROR_MIN) {
      ++st.snapHits;
      st.lastSnapHitTick = tick;
      if (st.snapHits >= SNAP_MIN_HITS && st.snapHits > st.snapMisses) {
        addVl(st, SNAP_VL, null, null, "silent(snap)");
        st.snapHits = 0;
      }
    } else if (bestPreInside <= QUANTUM && bestErr > SNAP_PRE_ERROR_MIN * 0.75F) {
      if (st.lastSnapHitTick != Long.MIN_VALUE && tick - st.lastSnapHitTick <= RETURN_PAIR_TICKS) {
        addVl(st, RETURN_VL, null, null, "silent(return)");
      }
    } else if (bestPre > SNAP_PRE_ERROR_MIN && bestErr > QUANTUM * 2.0F) {
      ++st.snapMisses;
    }
  }

  // ── Track component ───────────────────────────────────────────────────

  private void trackComponent(
      EntityPlayer player, State st, float yaw, List<EntityPlayer> targets) {
    if (targets.isEmpty()) {
      st.lastTargetId = null;
      st.lastBearing = Float.NaN;
      return;
    }

    EntityPlayer target = targets.get(0);
    double bestDistSq = player.getDistanceSqToEntity(target);
    for (int i = 1; i < targets.size(); i++) {
      double dsq = player.getDistanceSqToEntity(targets.get(i));
      if (dsq < bestDistSq) {
        bestDistSq = dsq;
        target = targets.get(i);
      }
    }

    UUID targetId = target.getUniqueID();
    Trail trail = trail(targetId);
    float bearingNow = bearingTo(player, trail.x[0], trail.z[0]);

    if (targetId.equals(st.lastTargetId) && !Float.isNaN(st.lastBearing)) {
      float losDelta = Math.abs(MathHelper.wrapAngleTo180_float(bearingNow - st.lastBearing));
      double dx = target.posX - player.posX;
      double dz = target.posZ - player.posZ;
      double horizDist = Math.sqrt(dx * dx + dz * dz);

      if (losDelta > TRACK_LOS_MIN && losDelta < TRACK_LOS_MAX && horizDist >= TRACK_MIN_DIST) {
        ++st.trackSamples;
        if (minInsideError(player, trail, yaw) <= QUANTUM * 0.5F) {
          ++st.trackTicks;
        }

        if (st.trackSamples >= TRACK_WINDOW) {
          if ((float) st.trackTicks >= TRACK_RATIO * (float) st.trackSamples) {
            addVl(st, TRACK_VL, null, null, "silent(track)");
          }
          st.trackSamples = 0;
          st.trackTicks = 0;
        }
      }
    }
    st.lastTargetId = targetId;
    st.lastBearing = bearingNow;
  }

  // ── Movement fix desync ───────────────────────────────────────────────

  private void movementComponent(
      Minecraft mc,
      EntityPlayer player,
      State st,
      double moveX,
      double moveY,
      double moveZ,
      float yaw,
      List<EntityPlayer> targets) {
    if (++st.moveTickCounter >= MOVE_DECAY_TICKS) {
      st.moveTickCounter = 0;
      st.lockDesync = Math.max(0, st.lockDesync - 1);
      st.sprintDesync = Math.max(0, st.sprintDesync - 1);
    }

    boolean flat =
        st.hasVel && Math.abs(moveY) < MOVE_FLAT_DY && Math.abs(st.lastMoveY) < MOVE_FLAT_DY;
    boolean haveAccel = st.hasVel;
    double ax = moveX - st.lastVelX;
    double az = moveZ - st.lastVelZ;
    st.lastVelX = moveX;
    st.lastVelZ = moveZ;
    st.lastMoveY = moveY;
    st.hasVel = true;
    if (!haveAccel) return;

    double accel = Math.sqrt(ax * ax + az * az);
    double speed = Math.sqrt(moveX * moveX + moveZ * moveZ);

    if (!flat || player.hurtTime > 0 || speed < MOVE_MIN_SPEED || speed > MOVE_MAX_SPEED) {
      return;
    }
    Block ground =
        mc.theWorld
            .getBlockState(new BlockPos(player.posX, player.posY - 0.5D, player.posZ))
            .getBlock();
    if (ground == Blocks.ice || ground == Blocks.packed_ice) {
      return;
    }

    float moveBearing = (float) Math.toDegrees(Math.atan2(-moveX, moveZ));
    float offset = MathHelper.wrapAngleTo180_float(moveBearing - yaw);
    float residual = bucketResidual(offset);

    // Sprint leak
    if (player.isSprinting()
        && speed > SPRINT_MIN_SPEED
        && accel < SPRINT_ACCEL
        && Math.abs(offset) > SPRINT_OFFSET) {
      ++st.sprintDesync;
      if (st.sprintDesync >= SPRINT_HITS) {
        addVl(st, MOVE_SPRINT_VL, null, null, "movement(sprint)");
        st.sprintDesync -= SPRINT_HITS;
      }
    }

    if (accel > MOVE_SMOOTH_ACCEL) return;

    // Lock desync
    if (residual > LOCK_RESIDUAL) {
      for (EntityPlayer target : targets) {
        if (minInsideError(player, trail(target.getUniqueID()), yaw) <= QUANTUM) {
          ++st.lockDesync;
          if (st.lockDesync >= LOCK_HITS) {
            addVl(st, MOVE_LOCK_VL, null, null, "movement(lock)");
            st.lockDesync -= LOCK_HITS;
          }
          break;
        }
      }
    }

    // Windowed mean residual
    ++st.moveSamples;
    st.residualSum += residual;
    if (residual > MOVE_DESYNC_RESIDUAL) ++st.moveDesyncTicks;

    if (st.moveSamples >= MOVE_WINDOW) {
      float mean = st.residualSum / (float) st.moveSamples;
      if (mean > MOVE_MEAN_LIMIT) {
        addVl(st, MOVE_VL, null, null, "movement(fix)");
      }
      st.moveSamples = 0;
      st.moveDesyncTicks = 0;
      st.residualSum = 0.0F;
    }
  }

  // ── Consume component ─────────────────────────────────────────────────

  private void consumeCheck(
      EntityPlayer player, State st, long tick, String name, ClientAntiCheatContext context) {
    ItemStack held = player.getHeldItem();
    boolean using = player.isUsingItem();
    boolean consumable = held != null && isConsumable(held.getItem());
    boolean attacking = player.swingProgressInt > 0;

    if (using && consumable) {
      ++st.useItemTicks;
    } else {
      if (st.useItemTicks > 0) {
        st.lastEatTick = tick;
      }
      st.useItemTicks = 0;
    }

    long sinceEat = tick - st.lastEatTick;
    if (attacking && st.useItemTicks > MIN_USE_TIME && sinceEat < EAT_TIMEOUT && consumable) {
      ++st.consumeVl;
      if (st.consumeVl >= CONSUME_FAIL_VL) {
        addVl(st, 50.0F, context, name, "consume");
        st.consumeVl = 0;
      }
    } else if (st.consumeVl > 0) {
      --st.consumeVl;
    }
  }

  // ── Helpers ───────────────────────────────────────────────────────────

  private float minInsideError(EntityPlayer attacker, Trail trail, float yaw) {
    float best = Float.MAX_VALUE;
    for (int i = 0; i < trail.size; ++i) {
      double dx = trail.x[i] - attacker.posX;
      double dz = trail.z[i] - attacker.posZ;
      double horizDist = Math.sqrt(dx * dx + dz * dz);
      if (horizDist < 0.5D) continue;
      float bearing = (float) (Math.atan2(dz, dx) * 180.0D / Math.PI) - 90.0F;
      float err = Math.abs(MathHelper.wrapAngleTo180_float(yaw - bearing));
      float halfWidth = (float) Math.toDegrees(Math.atan2(HITBOX_HALF_WIDTH, horizDist));
      best = Math.min(best, Math.max(0.0F, err - halfWidth));
    }
    return best;
  }

  private static float bearingTo(EntityPlayer attacker, double x, double z) {
    double dx = x - attacker.posX;
    double dz = z - attacker.posZ;
    return (float) (Math.atan2(dz, dx) * 180.0D / Math.PI) - 90.0F;
  }

  private static float bucketResidual(float offset) {
    float nearest = 45.0F * Math.round(offset / 45.0F);
    return Math.abs(MathHelper.wrapAngleTo180_float(offset - nearest));
  }

  private List<EntityPlayer> targetsNear(Minecraft mc, EntityPlayer attacker, long tick) {
    List<EntityPlayer> out = new ArrayList<>();
    if (mc.theWorld == null) return out;
    for (EntityPlayer p : mc.theWorld.playerEntities) {
      if (!PlayerEligibility.shouldUseAsTarget(p, attacker)) continue;
      double dx = p.posX - attacker.posX;
      double dy = p.posY - attacker.posY;
      double dz = p.posZ - attacker.posZ;
      if (dx * dx + dy * dy + dz * dz > TARGET_RANGE_SQ) continue;
      trail(p.getUniqueID()).push(p.posX, p.posZ, tick);
      out.add(p);
    }
    return out;
  }

  private boolean isConsumable(net.minecraft.item.Item item) {
    return item instanceof ItemFood || item instanceof ItemPotion || item instanceof ItemBucketMilk;
  }

  private Trail trail(UUID uuid) {
    return trails.computeIfAbsent(uuid, k -> new Trail());
  }

  private void resetBurst(State st) {
    st.burstTicks = 0;
    st.burstSum = 0.0F;
    st.burstDir = 0.0F;
  }

  private void resetSession(State st) {
    resetBurst(st);
    st.quietTicks = 0;
    st.snapHits = 0;
    st.snapMisses = 0;
    st.lastSnapHitTick = Long.MIN_VALUE;
    st.trackSamples = 0;
    st.trackTicks = 0;
    st.lastTargetId = null;
    st.lastBearing = Float.NaN;
    st.hasVel = false;
    st.moveSamples = 0;
    st.moveDesyncTicks = 0;
    st.residualSum = 0.0F;
    st.lockDesync = 0;
    st.sprintDesync = 0;
    st.moveTickCounter = 0;
    st.consumeVl = 0;
  }

  public void forgetPlayer(UUID uuid) {
    if (uuid == null) return;
    states.remove(uuid);
    trails.remove(uuid);
  }

  public void reset() {
    states.clear();
    trails.clear();
  }
}
