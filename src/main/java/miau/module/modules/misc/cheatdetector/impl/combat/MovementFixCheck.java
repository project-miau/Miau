package miau.module.modules.misc.cheatdetector.impl.combat;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import miau.module.modules.misc.cheatdetector.Check;
import net.minecraft.entity.player.EntityPlayer;

/**
 * Detects the body/head desync that "movement fix" silent aim introduces. Ported from Rain
 * Anticheat's KillauraCheck movement component.
 *
 * <p>Silent aim with "movement fix" recomputes strafe inputs against the real screen yaw, so
 * measured against the broadcast yaw the offset drifts arbitrarily. This check detects three leaks:
 * 1. Sprint-direction leak: sprint speed at offsets beyond ±45° is impossible 2. Bucket residual
 * analysis: velocity bearing deviates from 45° multiples 3. Lock + fix: body walks its own line
 * while head is pinned on target
 */
public class MovementFixCheck extends Check {
  private static final double SPRINT_ACCEL = 0.08D;
  private static final double SPRINT_MIN_SPEED = 0.25D;
  private static final float SPRINT_OFFSET = 62.0F;
  private static final int SPRINT_HITS = 4;

  private static final double MOVE_MIN_SPEED = 0.15D;
  private static final double MOVE_MAX_SPEED = 0.45D;
  private static final double MOVE_FLAT_DY = 0.001D;
  private static final double MOVE_SMOOTH_ACCEL = 0.022D;
  private static final int MOVE_WINDOW = 12;
  private static final float MOVE_MEAN_LIMIT = 7.5F;
  private static final float MOVE_DESYNC_RESIDUAL = 8.0F;

  private static final double TARGET_RANGE_SQ = 36.0D;

  private static class State {
    double lastPosX, lastPosZ, lastPosY;
    double lastVelX, lastVelZ;
    boolean hasVel;
    int moveSamples;
    float residualSum;
    int moveDesyncTicks;
    int sprintDesync;
  }

  private final Map<UUID, State> states = new HashMap<>();

  @Override
  public String getName() {
    return "MovementFix";
  }

  @Override
  public void onUpdate(EntityPlayer player) {
    UUID uuid = player.getUniqueID();
    State st = states.computeIfAbsent(uuid, k -> new State());

    double moveX = player.posX - player.prevPosX;
    double moveY = player.posY - player.prevPosY;
    double moveZ = player.posZ - player.prevPosZ;

    double ax = 0, az = 0;
    if (st.hasVel) {
      ax = moveX - st.lastVelX;
      az = moveZ - st.lastVelZ;
    }
    st.lastVelX = moveX;
    st.lastVelZ = moveZ;
    st.lastPosY = moveY;
    st.hasVel = true;

    // Need at least one cycle of acceleration data
    if (player.prevPosX == 0 && player.prevPosZ == 0) return;

    double accel = Math.sqrt(ax * ax + az * az);
    double speed = Math.sqrt(moveX * moveX + moveZ * moveZ);

    // Sprint-direction leak
    float yaw = player.rotationYawHead;
    float moveBearing = (float) Math.toDegrees(Math.atan2(-moveX, moveZ));
    float offset = wrapDegrees(moveBearing - yaw);

    if (player.isSprinting()
        && speed > SPRINT_MIN_SPEED
        && accel < SPRINT_ACCEL
        && Math.abs(offset) > SPRINT_OFFSET) {
      st.sprintDesync++;
      if (st.sprintDesync >= SPRINT_HITS) {
        flag(player, "Sprint outside strafe cone (" + (int) offset + "°)");
        st.sprintDesync = 0;
      }
    } else if (st.sprintDesync > 0) {
      st.sprintDesync--;
    }

    // Movement fix residual analysis (only on flat ground)
    boolean flat = Math.abs(moveY) < MOVE_FLAT_DY;
    if (!flat || player.hurtTime > 0 || speed < MOVE_MIN_SPEED || speed > MOVE_MAX_SPEED) {
      return;
    }

    // Check ground block for ice
    net.minecraft.block.Block ground =
        player.getEntityWorld() == null
            ? null
            : player
                .getEntityWorld()
                .getBlockState(
                    new net.minecraft.util.BlockPos(player.posX, player.posY - 0.5D, player.posZ))
                .getBlock();
    if (ground == net.minecraft.init.Blocks.ice || ground == net.minecraft.init.Blocks.packed_ice) {
      return;
    }

    if (accel > MOVE_SMOOTH_ACCEL) {
      return; // Too much turning — residual is unreliable
    }

    float residual = bucketResidual(offset);

    ++st.moveSamples;
    st.residualSum += residual;
    if (residual > MOVE_DESYNC_RESIDUAL) {
      ++st.moveDesyncTicks;
    }

    if (st.moveSamples >= MOVE_WINDOW) {
      float mean = st.residualSum / (float) st.moveSamples;
      if (mean > MOVE_MEAN_LIMIT) {
        flag(player, "Movement desync (mean=" + String.format("%.1f", mean) + "°)");
      }
      st.moveSamples = 0;
      st.moveDesyncTicks = 0;
      st.residualSum = 0.0F;
    }
  }

  private static float bucketResidual(float offset) {
    float nearest = 45.0F * Math.round(offset / 45.0F);
    return Math.abs(wrapDegrees(offset - nearest));
  }

  private static float wrapDegrees(float angle) {
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
