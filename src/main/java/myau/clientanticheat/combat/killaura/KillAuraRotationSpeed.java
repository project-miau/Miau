package myau.clientanticheat.combat.killaura;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Map;
import java.util.Queue;
import myau.clientanticheat.CheckBuffer;
import myau.clientanticheat.ClientAntiCheatContext;
import myau.clientanticheat.PlayerCheckData;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.MathHelper;
import net.minecraft.util.Vec3;

/**
 * KillAura extra checks — attack rate, wide-angle silent aim, and hit accuracy. Flick/pre-attack
 * patterns removed (handled better by KillAuraUnifiedCheck's burstMachine).
 */
public class KillAuraRotationSpeed {
  private final Map<String, Long> lastAttackTicks = new HashMap<>();
  private final Map<String, CheckBuffer> rateBuffers = new HashMap<>();
  private final Map<String, CheckBuffer> aimBuffers = new HashMap<>();
  private final Map<String, CheckBuffer> accuracyBuffers = new HashMap<>();
  private final Map<String, Queue<Double>> hitAccuracySamples = new HashMap<>();

  private static final int ACCURACY_SAMPLE_SIZE = 25;
  private static final double HIGH_ACCURACY_THRESHOLD = 0.95D;

  public void check(
      EntityPlayer player, PlayerCheckData data, long currentTick, ClientAntiCheatContext context) {
    String name = player.getName();
    if (name == null || data == null || data.recentlyTeleported()) return;

    if (!data.startedSwinging()) {
      this.decay(name);
      return;
    }

    if (data.recentlyHurt()) {
      this.decay(name);
      return;
    }

    EntityPlayer target = data.nearestTarget;
    if (target == null || data.nearestTargetDistance > 6.0D) {
      this.decay(name);
      return;
    }

    CheckBuffer rateBuffer = this.rateBuffers.computeIfAbsent(name, k -> new CheckBuffer());
    CheckBuffer aimBuffer = this.aimBuffers.computeIfAbsent(name, k -> new CheckBuffer());
    CheckBuffer accuracyBuffer = this.accuracyBuffers.computeIfAbsent(name, k -> new CheckBuffer());

    long lastAttack = this.lastAttackTicks.getOrDefault(name, currentTick - 20L);
    long delay = currentTick - lastAttack;
    this.lastAttackTicks.put(name, currentTick);

    // ── Attack speed (rate) ────────────────────────────────────────
    if (delay > 0L && delay < 3L) {
      rateBuffer.flag(1.0D, 999.0D);
    } else {
      rateBuffer.decay(0.4D);
    }

    // ── Wide-angle silent aim ──────────────────────────────────────
    float yawError =
        Math.abs(MathHelper.wrapAngleTo180_float(this.yawTo(player, target) - player.rotationYaw));
    float pitchError = Math.abs(this.pitchTo(player, target) - player.rotationPitch);
    if (yawError > 45.0F || pitchError > 35.0F) {
      aimBuffer.flag(1.25D, 999.0D);
    } else {
      aimBuffer.decay(0.5D);
    }

    // ── Hit accuracy ───────────────────────────────────────────────
    Queue<Double> accuracySamples =
        this.hitAccuracySamples.computeIfAbsent(name, k -> new ArrayDeque<>());
    double totalAngleError = yawError + pitchError;
    double accuracy = Math.max(0.0, 1.0 - (totalAngleError / 90.0));
    accuracySamples.add(accuracy);

    if (accuracySamples.size() >= ACCURACY_SAMPLE_SIZE) {
      double avgAccuracy = 0.0;
      for (double sample : accuracySamples) {
        avgAccuracy += sample;
      }
      avgAccuracy /= accuracySamples.size();

      boolean highAccuracy = avgAccuracy > HIGH_ACCURACY_THRESHOLD;

      double varianceSum = 0.0;
      for (double sample : accuracySamples) {
        varianceSum += (sample - avgAccuracy) * (sample - avgAccuracy);
      }
      double variance = varianceSum / accuracySamples.size();
      boolean lowVariance = variance < 0.002D;

      if (highAccuracy || (lowVariance && avgAccuracy > 0.85D)) {
        accuracyBuffer.flag(highAccuracy ? 1.5D : 1.0D, 999.0D);
      } else {
        accuracyBuffer.decay(0.25D);
      }
      accuracySamples.clear();
    }

    // ── Fire signals ───────────────────────────────────────────────
    if (rateBuffer.get() > 4.0D && aimBuffer.get() > 2.0D) {
      context.receiveSignal(name, "KillAura", "aim rate", (int) rateBuffer.get());
      rateBuffer.reset();
      aimBuffer.reset();
    }
    if (accuracyBuffer.get() > 3.0D) {
      context.receiveSignal(name, "KillAura", "accuracy", (int) accuracyBuffer.get());
      accuracyBuffer.reset();
    }
  }

  public void decay(String name) {
    CheckBuffer rate = this.rateBuffers.get(name);
    if (rate != null) rate.decay(0.2D);
    CheckBuffer aim = this.aimBuffers.get(name);
    if (aim != null) aim.decay(0.15D);
    CheckBuffer accuracy = this.accuracyBuffers.get(name);
    if (accuracy != null) accuracy.decay(0.1D);
  }

  public void reset() {
    this.lastAttackTicks.clear();
    this.rateBuffers.clear();
    this.aimBuffers.clear();
    this.accuracyBuffers.clear();
    this.hitAccuracySamples.clear();
  }

  private float yawTo(EntityPlayer player, EntityPlayer target) {
    double dx = target.posX - player.posX;
    double dz = target.posZ - player.posZ;
    return (float) (Math.atan2(dz, dx) * 180.0D / Math.PI) - 90.0F;
  }

  private float pitchTo(EntityPlayer player, EntityPlayer target) {
    Vec3 eyes = new Vec3(player.posX, player.posY + player.getEyeHeight(), player.posZ);
    Vec3 targetEyes = new Vec3(target.posX, target.posY + target.getEyeHeight(), target.posZ);
    double dx = targetEyes.xCoord - eyes.xCoord;
    double dy = targetEyes.yCoord - eyes.yCoord;
    double dz = targetEyes.zCoord - eyes.zCoord;
    double horizontal = Math.sqrt(dx * dx + dz * dz);
    return (float) -(Math.atan2(dy, horizontal) * 180.0D / Math.PI);
  }
}
