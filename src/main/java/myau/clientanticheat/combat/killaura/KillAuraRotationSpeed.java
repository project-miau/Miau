package myau.clientanticheat.combat.killaura;

import java.util.HashMap;
import java.util.Map;
import myau.clientanticheat.CheckBuffer;
import myau.clientanticheat.ClientAntiCheatContext;
import myau.clientanticheat.PlayerCheckData;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.MathHelper;
import net.minecraft.util.Vec3;
import net.minecraft.world.World;

public class KillAuraRotationSpeed {
  private final Map<String, Long> lastAttackTicks = new HashMap<>();
  private final Map<String, CheckBuffer> rateBuffers = new HashMap<>();
  private final Map<String, CheckBuffer> aimBuffers = new HashMap<>();
  private final Map<String, CheckBuffer> linearAimBuffers = new HashMap<>();

  public void check(
      EntityPlayer player,
      World world,
      PlayerCheckData data,
      long currentTick,
      ClientAntiCheatContext context) {
    String name = player.getName();
    if (name == null || data == null || data.recentlyTeleported()) return;

    if (!data.startedSwinging()) {
      this.decay(name);
      return;
    }

    EntityPlayer target = this.nearestTarget(player, world, 6.0D);
    if (target == null) return;

    CheckBuffer rateBuffer = this.rateBuffers.computeIfAbsent(name, key -> new CheckBuffer());
    CheckBuffer aimBuffer = this.aimBuffers.computeIfAbsent(name, key -> new CheckBuffer());
    CheckBuffer linearAimBuffer =
        this.linearAimBuffers.computeIfAbsent(name, key -> new CheckBuffer());

    long lastAttack = this.lastAttackTicks.getOrDefault(name, currentTick - 20L);
    long delay = currentTick - lastAttack;
    this.lastAttackTicks.put(name, currentTick);
    if (delay > 0L && delay < 3L) {
      rateBuffer.flag(1.0D, 999.0D);
    } else {
      rateBuffer.decay(0.35D);
    }

    float yawError =
        Math.abs(MathHelper.wrapAngleTo180_float(this.yawTo(player, target) - player.rotationYaw));
    float pitchError = Math.abs(this.pitchTo(player, target) - player.rotationPitch);
    if (yawError > 35.0F || pitchError > 28.0F) {
      aimBuffer.flag(1.25D, 999.0D);
    } else {
      aimBuffer.decay(0.45D);
    }

    if (data.yawDelta > 0.0F && Math.abs(data.yawAcceleration) < 0.01F) {
      linearAimBuffer.flag(1.0D, 6.0D);
    } else {
      linearAimBuffer.decay(0.2D);
    }

    if (rateBuffer.get() > 4.0D && aimBuffer.get() > 2.0D) {
      context.receiveSignal(name, "KillAura (Aim)");
      rateBuffer.reset();
      aimBuffer.reset();
    }
    if (linearAimBuffer.get() > 4.0D) {
      context.receiveSignal(name, "KillAura (Robotic Aim)");
      linearAimBuffer.reset();
    }
  }

  public void decay(String name) {
    CheckBuffer rate = this.rateBuffers.get(name);
    if (rate != null) rate.decay(0.15D);
    CheckBuffer aim = this.aimBuffers.get(name);
    if (aim != null) aim.decay(0.15D);
    CheckBuffer linear = this.linearAimBuffers.get(name);
    if (linear != null) linear.decay(0.1D);
  }

  public void reset() {
    this.lastAttackTicks.clear();
    this.rateBuffers.clear();
    this.aimBuffers.clear();
    this.linearAimBuffers.clear();
  }

  private EntityPlayer nearestTarget(EntityPlayer player, World world, double maxDistance) {
    EntityPlayer nearest = null;
    double best = maxDistance * maxDistance;
    for (EntityPlayer target : world.playerEntities) {
      if (target == player || target.isDead || target.getName() == null) continue;
      double distance = player.getDistanceSqToEntity(target);
      if (distance < best) {
        best = distance;
        nearest = target;
      }
    }
    return nearest;
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
