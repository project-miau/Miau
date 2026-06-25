package myau.clientanticheat.combat.reach;

import java.util.HashMap;
import java.util.Map;
import myau.clientanticheat.CheckBuffer;
import myau.clientanticheat.ClientAntiCheatContext;
import myau.clientanticheat.PlayerCheckData;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.Vec3;
import net.minecraft.world.World;

public class HitboxRaytraceCheck {
  private static final double BASE_REACH = 3.05D;
  private final Map<String, CheckBuffer> reachBuffers = new HashMap<>();

  public void check(
      EntityPlayer player, World world, PlayerCheckData data, ClientAntiCheatContext context) {
    if (data == null || data.recentlyTeleported() || !data.startedSwinging()) return;
    String name = player.getName();
    if (name == null) return;

    double nearest = Double.MAX_VALUE;
    Vec3 eyes = new Vec3(player.posX, player.posY + player.getEyeHeight(), player.posZ);
    Vec3 look = player.getLook(1.0F);
    Vec3 reachVec = eyes.addVector(look.xCoord * 6.0D, look.yCoord * 6.0D, look.zCoord * 6.0D);
    boolean hasRaytraceHit = false;

    for (EntityPlayer target : world.playerEntities) {
      if (target == player || target.isDead || target.getName() == null) continue;

      PlayerCheckData targetData = context.getPlayerData(target);
      if (targetData != null && !targetData.history.isEmpty()) {
        for (AxisAlignedBB box : targetData.history) {
          AxisAlignedBB expandedBox = box.expand(0.4D, 0.4D, 0.4D);
          double distance = distanceToBox(eyes, expandedBox);
          if (distance < nearest) nearest = distance;
          if (expandedBox.calculateIntercept(eyes, reachVec) != null) hasRaytraceHit = true;
        }
      } else {
        AxisAlignedBB box = target.getEntityBoundingBox().expand(0.4D, 0.4D, 0.4D);
        double distance = distanceToBox(eyes, box);
        if (distance < nearest) nearest = distance;
        if (box.calculateIntercept(eyes, reachVec) != null) hasRaytraceHit = true;
      }
    }
    if (nearest == Double.MAX_VALUE || nearest > 6.0D) return;

    CheckBuffer buffer = this.reachBuffers.computeIfAbsent(name, key -> new CheckBuffer());

    double baseAllowed = 3.6D;
    double movementTolerance = (data.horizontalDelta + data.lastHorizontalDelta) * 1.5D;
    double allowed = baseAllowed + movementTolerance + (data.recentlyHurt() ? 0.3D : 0.0D);

    if (nearest > allowed) {
      double over = nearest - allowed;
      if (buffer.flag(1.0D + Math.min(2.0D, over * 2.0D), 4.5D)) {
        context.receiveSignal(name, "Reach (Distance)");
        buffer.reset();
      }
    } else if (!hasRaytraceHit && nearest > 2.0D) {
      if (buffer.flag(0.5D, 4.0D)) {
        context.receiveSignal(name, "Reach (Hitbox)");
        buffer.reset();
      }
    } else {
      buffer.decay(0.35D);
    }
  }

  private double distanceToBox(Vec3 point, AxisAlignedBB box) {
    double x = clamp(point.xCoord, box.minX, box.maxX);
    double y = clamp(point.yCoord, box.minY, box.maxY);
    double z = clamp(point.zCoord, box.minZ, box.maxZ);
    double dx = point.xCoord - x;
    double dy = point.yCoord - y;
    double dz = point.zCoord - z;
    return Math.sqrt(dx * dx + dy * dy + dz * dz);
  }

  private double clamp(double value, double min, double max) {
    return Math.max(min, Math.min(max, value));
  }

  public void reset() {
    this.reachBuffers.clear();
  }
}
