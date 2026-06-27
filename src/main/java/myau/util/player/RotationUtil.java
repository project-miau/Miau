package myau.util.player;

import java.util.List;
import java.util.Random;
import myau.mixin.IAccessorEntity;
import myau.mixin.IAccessorMinecraft;
import myau.util.animation.*;
import myau.util.client.*;
import myau.util.math.*;
import myau.util.misc.*;
import myau.util.network.*;
import myau.util.render.*;
import myau.util.time.*;
import myau.util.world.*;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;
import net.minecraft.entity.projectile.EntityEgg;
import net.minecraft.util.*;

public class RotationUtil {
  private static final Minecraft mc = Minecraft.getMinecraft();

  // Gothaj scaffold rotation state
  public static float serverYaw;
  public static float serverPitch;
  public static boolean customRots;

  public static float wrapAngleDiff(float angle, float target) {
    return target + MathHelper.wrapAngleTo180_float(angle - target);
  }

  public static float clampAngle(float angle, float maxAngle) {
    maxAngle = Math.max(0.0f, Math.min(180.0f, maxAngle));
    if (angle > maxAngle) {
      angle = maxAngle;
    } else if (angle < -maxAngle) {
      angle = -maxAngle;
    }
    return angle;
  }

  public static float smoothAngle(float angle, float smoothFactor) {
    return angle
        * (0.5f
            + 0.5f
                * (1.0f
                    - Math.max(
                        0.0f, Math.min(1.0f, smoothFactor + RandomUtil.nextFloat(-0.1f, 0.1f)))));
  }

  public static float quantizeAngle(float angle) {
    return (float) ((double) angle - (double) angle % (double) 0.0096f);
  }

  public static float[] getRotationsToBox(
      AxisAlignedBB boundingBox, float yaw, float pitch, float maxAngle, float smoothFactor) {
    Vec3 eyePos = RotationUtil.mc.thePlayer.getPositionEyes(1.0f);
    double minTargetY = boundingBox.minY + 0.05 * (boundingBox.maxY - boundingBox.minY);
    double maxTargetY = boundingBox.minY + 0.75 * (boundingBox.maxY - boundingBox.minY);
    double deltaX = (boundingBox.minX + boundingBox.maxX) / 2.0 - eyePos.xCoord;
    double deltaY =
        eyePos.yCoord >= maxTargetY
            ? maxTargetY - eyePos.yCoord
            : (eyePos.yCoord <= minTargetY ? minTargetY - eyePos.yCoord : 0.0);
    double deltaZ = (boundingBox.minZ + boundingBox.maxZ) / 2.0 - eyePos.zCoord;
    return RotationUtil.getRotations(deltaX, deltaY, deltaZ, yaw, pitch, maxAngle, smoothFactor);
  }

  public static float[] getRotationsTo(
      double targetX, double targetY, double targetZ, float currentYaw, float currentPitch) {
    return RotationUtil.getRotations(
        targetX, targetY, targetZ, currentYaw, currentPitch, 180.0f, 0.0f);
  }

  public static float[] getRotations(
      double targetX,
      double targetY,
      double targetZ,
      float currentYaw,
      float currentPitch,
      float maxAngle,
      float smoothFactor) {
    double horizontalDistance = Math.sqrt(targetX * targetX + targetZ * targetZ);
    float yawDelta =
        MathHelper.wrapAngleTo180_float(
            (float) (Math.atan2(targetZ, targetX) * 180.0 / Math.PI) - 90.0f - currentYaw);
    float pitchDelta =
        MathHelper.wrapAngleTo180_float(
            (float) (-Math.atan2(targetY, horizontalDistance) * 180.0 / Math.PI) - currentPitch);
    yawDelta =
        Math.abs(yawDelta) <= 1.0f
            ? 0.0f
            : RotationUtil.smoothAngle(RotationUtil.clampAngle(yawDelta, maxAngle), smoothFactor);
    pitchDelta =
        Math.abs(pitchDelta) <= 1.0f
            ? 0.0f
            : RotationUtil.smoothAngle(RotationUtil.clampAngle(pitchDelta, maxAngle), smoothFactor);
    return new float[] {
      RotationUtil.quantizeAngle(currentYaw + yawDelta),
      RotationUtil.quantizeAngle(currentPitch + pitchDelta)
    };
  }

  public static Vec3 clampVecToBox(Vec3 vector, AxisAlignedBB boundingBox) {
    double[] coords = new double[] {vector.xCoord, vector.yCoord, vector.zCoord};
    double[] minCoords = new double[] {boundingBox.minX, boundingBox.minY, boundingBox.minZ};
    double[] maxCoords = new double[] {boundingBox.maxX, boundingBox.maxY, boundingBox.maxZ};
    for (int i = 0; i < 3; ++i) {
      if (coords[i] > maxCoords[i]) {
        coords[i] = maxCoords[i];
        continue;
      }
      if (!(coords[i] < minCoords[i])) continue;
      coords[i] = minCoords[i];
    }
    return new Vec3(coords[0], coords[1], coords[2]);
  }

  public static double distanceToEntity(Entity entity) {
    float borderSize = entity.getCollisionBorderSize();
    AxisAlignedBB boundingBox =
        entity.getEntityBoundingBox().expand(borderSize, borderSize, borderSize);
    return RotationUtil.distanceToBox(boundingBox);
  }

  public static double distanceToBox(Entity entity, Vec3 point) {
    float borderSize = entity.getCollisionBorderSize();
    return RotationUtil.clampVecToBox(
        entity.getEntityBoundingBox().expand(borderSize, borderSize, borderSize), point);
  }

  public static double distanceToBox(AxisAlignedBB boundingBox) {
    return RotationUtil.clampVecToBox(boundingBox, RotationUtil.mc.thePlayer.getPositionEyes(1.0f));
  }

  public static double clampVecToBox(AxisAlignedBB boundingBox, Vec3 point) {
    if (boundingBox.isVecInside(point)) {
      return 0.0;
    }
    Vec3 clampedPoint = RotationUtil.clampVecToBox(point, boundingBox);
    double deltaX = clampedPoint.xCoord - point.xCoord;
    double deltaY = clampedPoint.yCoord - point.yCoord;
    double deltaZ = clampedPoint.zCoord - point.zCoord;
    return Math.sqrt(deltaX * deltaX + deltaY * deltaY + deltaZ * deltaZ);
  }

  public static float angleToEntity(Entity entity) {
    Vec3 eyePos = RotationUtil.mc.thePlayer.getPositionEyes(1.0f);
    float borderSize = entity.getCollisionBorderSize();
    AxisAlignedBB boundingBox =
        entity.getEntityBoundingBox().expand(borderSize, borderSize, borderSize);
    if (boundingBox.isVecInside(eyePos)) {
      return 0.0f;
    }
    double deltaX = entity.posX - eyePos.xCoord;
    double deltaZ = entity.posZ - eyePos.zCoord;
    return Math.abs(
            MathHelper.wrapAngleTo180_float(
                (float) (Math.atan2(deltaZ, deltaX) * 180.0 / Math.PI)
                    - 90.0f
                    - RotationUtil.mc.thePlayer.rotationYaw))
        * 2.0f;
  }

  public static float getYawBetween(double x1, double z1, double x2, double z2) {
    return MathHelper.wrapAngleTo180_float(
        (float) (Math.atan2(z2 - z1, x2 - x1) * 180.0 / Math.PI)
            - 90.0f
            - RotationUtil.mc.thePlayer.rotationYaw);
  }

  public static MovingObjectPosition rayTrace(
      float yaw, float pitch, double distance, float partialTicks) {
    Vec3 eyePos = RotationUtil.mc.thePlayer.getPositionEyes(partialTicks);
    Vec3 lookVec =
        ((IAccessorEntity) RotationUtil.mc.thePlayer).callGetVectorForRotation(pitch, yaw);
    Vec3 targetPos =
        eyePos.addVector(
            lookVec.xCoord * distance, lookVec.yCoord * distance, lookVec.zCoord * distance);
    return RotationUtil.mc.theWorld.rayTraceBlocks(eyePos, targetPos);
  }

  public static MovingObjectPosition rayTrace(Entity entity) {
    Vec3 eyePos = RotationUtil.mc.thePlayer.getPositionEyes(1.0f);
    float borderSize = entity.getCollisionBorderSize();
    Vec3 targetPos =
        RotationUtil.clampVecToBox(
            eyePos, entity.getEntityBoundingBox().expand(borderSize, borderSize, borderSize));
    return RotationUtil.mc.theWorld.rayTraceBlocks(eyePos, targetPos);
  }

  private static float randomAngle = 0.0f;
  private static float offsetX = 0.0f;
  private static float offsetY = 0.0f;

  public static float[] calculate(Vec3 to) {
    Vec3 from = mc.thePlayer.getPositionEyes(1.0f);
    double diffX = to.xCoord - from.xCoord;
    double diffY = to.yCoord - from.yCoord;
    double diffZ = to.zCoord - from.zCoord;
    double distance = Math.hypot(diffX, diffZ);
    float yaw = (float) (MathHelper.atan2(diffZ, diffX) * 180.0 / Math.PI) - 90.0F;
    float pitch = (float) (-(MathHelper.atan2(diffY, distance) * 180.0 / Math.PI));
    return new float[] {
      MathHelper.wrapAngleTo180_float(yaw), MathHelper.wrapAngleTo180_float(pitch)
    };
  }

  public static float[] calculate(Entity entity) {
    double yOffset =
        Math.max(
            0,
            Math.min(
                mc.thePlayer.posY - entity.posY + mc.thePlayer.getEyeHeight(),
                (entity.getEntityBoundingBox().maxY - entity.getEntityBoundingBox().minY) * 0.9));
    return calculate(new Vec3(entity.posX, entity.posY + yOffset, entity.posZ));
  }

  public static float[] calculate(Entity entity, boolean adaptive, double range) {
    float[] normalRotations = calculate(entity);
    if (!adaptive || rayCastHit(normalRotations, range, entity)) {
      return normalRotations;
    }

    AxisAlignedBB boundingBox = entity.getEntityBoundingBox();
    double width = boundingBox.maxX - boundingBox.minX;
    double height = boundingBox.maxY - boundingBox.minY;
    double depth = boundingBox.maxZ - boundingBox.minZ;

    for (double yPercent = 1.0; yPercent >= 0.0; yPercent -= 0.25 + Math.random() * 0.1) {
      for (double xPercent = 1.0; xPercent >= -0.5; xPercent -= 0.5) {
        for (double zPercent = 1.0; zPercent >= -0.5; zPercent -= 0.5) {
          Vec3 targetPoint =
              new Vec3(
                  boundingBox.minX + width * xPercent,
                  boundingBox.minY + height * yPercent,
                  boundingBox.minZ + depth * zPercent);
          float[] adaptiveRotations = calculate(targetPoint);
          if (rayCastHit(adaptiveRotations, range, entity)) {
            return adaptiveRotations;
          }
        }
      }
    }

    return normalRotations;
  }

  public static boolean rayCastHit(float[] rotations, double range, Entity target) {
    MovingObjectPosition mop = RayCastUtil.rayCast(rotations[0], rotations[1], range, 0.0f, target);
    return mop != null && mop.typeOfHit == MovingObjectPosition.MovingObjectType.ENTITY;
  }

  public static float[] applySensitivityPatch(
      float yaw, float pitch, float prevYaw, float prevPitch) {
    float mouseSensitivity =
        (float)
            (mc.gameSettings.mouseSensitivity * (1.0 + Math.random() / 10000000.0) * 0.6F + 0.2F);
    double multiplier = mouseSensitivity * mouseSensitivity * mouseSensitivity * 8.0F * 0.15D;
    float fixedYaw = prevYaw + (float) (Math.round((yaw - prevYaw) / multiplier) * multiplier);
    float fixedPitch =
        prevPitch + (float) (Math.round((pitch - prevPitch) / multiplier) * multiplier);
    return new float[] {fixedYaw, MathHelper.clamp_float(fixedPitch, -90.0F, 90.0F)};
  }

  public static float[] smooth(
      float[] lastRotation,
      float[] targetRotation,
      double speed,
      Entity targetEntity,
      double range) {
    float targetYaw = targetRotation[0];
    float targetPitch = targetRotation[1];
    float lastYaw = lastRotation[0];
    float lastPitch = lastRotation[1];

    if (targetEntity != null
        && (Math.abs(targetYaw - lastYaw) > 5 || Math.abs(targetPitch - lastPitch) > 5)) {
      double driftSpeed = (Math.random() * Math.random() * Math.random()) * 20.0;
      randomAngle +=
          (float)
              ((20.0
                      + (Math.random() - 0.5)
                          * (Math.random() * Math.random() * Math.random() * 360.0))
                  * (mc.thePlayer.ticksExisted / 10 % 2 == 0 ? -1 : 1));

      offsetX += -MathHelper.sin((float) Math.toRadians(randomAngle)) * driftSpeed;
      offsetY += MathHelper.cos((float) Math.toRadians(randomAngle)) * driftSpeed;

      targetYaw += offsetX;
      targetPitch += offsetY;

      if (!rayCastHit(new float[] {targetYaw, targetPitch}, range, targetEntity)) {
        randomAngle =
            (float)
                    Math.toDegrees(
                        Math.atan2(targetRotation[0] - targetYaw, targetPitch - targetRotation[1]))
                - 180.0F;
        targetYaw -= offsetX;
        targetPitch -= offsetY;

        offsetX += -MathHelper.sin((float) Math.toRadians(randomAngle)) * driftSpeed;
        offsetY += MathHelper.cos((float) Math.toRadians(randomAngle)) * driftSpeed;

        targetYaw += offsetX;
        targetPitch += offsetY;
      }

      if (!rayCastHit(new float[] {targetYaw, targetPitch}, range, targetEntity)) {
        offsetX = 0;
        offsetY = 0;
        targetYaw = (float) (targetRotation[0] + Math.random() * 2);
        targetPitch = (float) (targetRotation[1] + Math.random() * 2);
      }
    }

    if (speed != 0) {
      double deltaYaw = MathHelper.wrapAngleTo180_float(targetYaw - lastYaw);
      double deltaPitch = (targetPitch - lastPitch);
      double distance = Math.sqrt(deltaYaw * deltaYaw + deltaPitch * deltaPitch);

      if (distance > 0.001) {
        double distributionYaw = Math.abs(deltaYaw / distance);
        double distributionPitch = Math.abs(deltaPitch / distance);

        double maxYaw = speed * distributionYaw;
        double maxPitch = speed * distributionPitch;

        float moveYaw = (float) Math.max(Math.min(deltaYaw, maxYaw), -maxYaw);
        float movePitch = (float) Math.max(Math.min(deltaPitch, maxPitch), -maxPitch);

        float yaw = lastYaw + moveYaw;
        float pitch = lastPitch + movePitch;

        for (int i = 1; i <= (int) (Minecraft.getDebugFPS() / 20.0F + Math.random() * 10); ++i) {
          if (Math.abs(moveYaw) + Math.abs(movePitch) > 0.0001) {
            yaw += (Math.random() - 0.5) / 1000.0;
            pitch -= Math.random() / 200.0;
          }

          float[] fixedRotations = applySensitivityPatch(yaw, pitch, lastYaw, lastPitch);
          yaw = fixedRotations[0];
          pitch = Math.max(-90.0F, Math.min(90.0F, fixedRotations[1]));
        }
        return new float[] {yaw, pitch};
      }
    }
    return targetRotation;
  }

  // ===== Gothaj Scaffold Utility Methods =====

  public static float updateRotation(float current, float intended, float factor) {
    float var4 = MathHelper.wrapAngleTo180_float(intended - current);
    if (var4 > factor) {
      var4 = factor;
    }
    if (var4 < -factor) {
      var4 = -factor;
    }
    return current + var4;
  }

  public static float[] getFixedRotation(float[] rotations, float[] lastRotations) {
    float yaw = rotations[0];
    float pitch = rotations[1];
    float lastYaw = lastRotations[0];
    float lastPitch = lastRotations[1];
    float f = mc.gameSettings.mouseSensitivity * 0.6F + 0.2F;
    float f1 = f * f * f * 8.0F;
    float deltaYaw = yaw - lastYaw;
    float deltaPitch = pitch - lastPitch;
    float fixedDeltaYaw = deltaYaw - deltaYaw % f1;
    float fixedDeltaPitch = deltaPitch - deltaPitch % f1;
    float fixedYaw = lastYaw + fixedDeltaYaw;
    float fixedPitch = lastPitch + fixedDeltaPitch;
    return new float[] {fixedYaw, fixedPitch};
  }

  public static boolean lookingAtBlock(
      BlockPos blockPos, float yaw, float pitch, EnumFacing enumFacing, boolean strict) {
    double reach = (double) mc.playerController.getBlockReachDistance();
    Vec3 eyePos =
        mc.thePlayer.getPositionEyes(((IAccessorMinecraft) mc).getTimer().renderPartialTicks);
    Vec3 lookVec = ((IAccessorEntity) mc.thePlayer).callGetVectorForRotation(pitch, yaw);
    Vec3 targetVec =
        eyePos.addVector(lookVec.xCoord * reach, lookVec.yCoord * reach, lookVec.zCoord * reach);
    MovingObjectPosition movingObjectPosition = mc.theWorld.rayTraceBlocks(eyePos, targetVec);
    if (movingObjectPosition == null) {
      return false;
    } else {
      Vec3 hitVec = movingObjectPosition.hitVec;
      return hitVec == null
          ? false
          : movingObjectPosition.getBlockPos().equals(blockPos)
              && (!strict
                  || movingObjectPosition.sideHit == enumFacing
                      && movingObjectPosition.sideHit != null);
    }
  }

  public static float getYawBasedPitch(
      BlockPos blockPos, EnumFacing facing, float currentYaw, float lastPitch, int maxPitch) {
    float increment = (float) (Math.random() / 20.0) + 0.05F;
    for (float i = (float) maxPitch; i > 45.0F; i -= increment) {
      MovingObjectPosition ray =
          rayCast(
              1.0F,
              new float[] {currentYaw, i},
              (double) mc.playerController.getBlockReachDistance(),
              2.0);
      if (ray == null || ray.getBlockPos() == null || ray.sideHit == null) {
        return lastPitch;
      }
      if (ray.getBlockPos().equals(blockPos) && ray.sideHit == facing) {
        return i;
      }
    }
    return lastPitch;
  }

  public static MovingObjectPosition rayCast(
      float partialTicks, float[] rots, double range, double hitBoxExpand) {
    MovingObjectPosition objectMouseOver = null;
    Entity entity = mc.getRenderViewEntity();
    if (entity != null && mc.theWorld != null) {
      mc.mcProfiler.startSection("pick");
      mc.pointedEntity = null;
      double d0 = range;
      Vec3 eyeVec = entity.getPositionEyes(partialTicks);
      Vec3 lookVec2 = ((IAccessorEntity) entity).callGetVectorForRotation(rots[1], rots[0]);
      Vec3 traceVec =
          eyeVec.addVector(
              lookVec2.xCoord * range, lookVec2.yCoord * range, lookVec2.zCoord * range);
      objectMouseOver = mc.theWorld.rayTraceBlocks(eyeVec, traceVec);
      double d2 = range;
      Vec3 vec3 = entity.getPositionEyes(partialTicks);
      boolean flag = false;
      if (mc.playerController.extendedReach()) {
        d0 = 6.0;
        d2 = 6.0;
      } else if (range > 3.0) {
        flag = true;
      }
      if (objectMouseOver != null) {
        d2 = objectMouseOver.hitVec.distanceTo(vec3);
      }
      Vec3 vec4 = entity.getLook(partialTicks);
      Vec3 vec5 = vec3.addVector(vec4.xCoord * d0, vec4.yCoord * d0, vec4.zCoord * d0);
      Entity pointedEntity = null;
      Vec3 vec6 = null;
      List list =
          mc.theWorld.getEntitiesInAABBexcluding(
              entity,
              entity
                  .getEntityBoundingBox()
                  .addCoord(vec4.xCoord * d0, vec4.yCoord * d0, vec4.zCoord * d0)
                  .expand(1.0, 1.0, 1.0),
              com.google.common.base.Predicates.and(
                  new com.google.common.base.Predicate[] {
                    net.minecraft.util.EntitySelectors.NOT_SPECTATING
                  }));
      double d3 = d2;
      for (int i = 0; i < list.size(); i++) {
        Entity entity2 = (Entity) list.get(i);
        float f2 = (float) ((double) entity2.getCollisionBorderSize() + hitBoxExpand);
        AxisAlignedBB axisalignedbb =
            entity2.getEntityBoundingBox().expand((double) f2, (double) f2, (double) f2);
        MovingObjectPosition movingobjectposition = axisalignedbb.calculateIntercept(vec3, vec5);
        if (axisalignedbb.isVecInside(vec3)) {
          if (d3 >= 0.0) {
            pointedEntity = entity2;
            vec6 = movingobjectposition == null ? vec3 : movingobjectposition.hitVec;
            d3 = 0.0;
          }
        } else if (movingobjectposition != null) {
          double d4 = vec3.distanceTo(movingobjectposition.hitVec);
          if (d4 < d3 || d3 == 0.0) {
            if (entity2 != entity.ridingEntity) {
              pointedEntity = entity2;
              vec6 = movingobjectposition.hitVec;
              d3 = d4;
            } else if (d3 == 0.0) {
              pointedEntity = entity2;
              vec6 = movingobjectposition.hitVec;
            }
          }
        }
      }
      if (pointedEntity != null && flag && vec3.distanceTo(vec6) > range) {
        pointedEntity = null;
        objectMouseOver =
            new MovingObjectPosition(
                MovingObjectPosition.MovingObjectType.MISS, vec6, null, new BlockPos(vec6));
      }
      if (pointedEntity != null && (d3 < d2 || objectMouseOver == null)) {
        objectMouseOver = new MovingObjectPosition(pointedEntity, vec6);
      }
    }
    return objectMouseOver;
  }

  public static float[] getDirectionToBlock(double x, double y, double z, EnumFacing enumfacing) {
    EntityEgg face = new EntityEgg(mc.theWorld);
    face.posX = x + 0.5;
    face.posY = y + 0.5;
    face.posZ = z + 0.5;
    face.posX = face.posX + (double) enumfacing.getDirectionVec().getX() * 0.5;
    face.posY = face.posY + (double) enumfacing.getDirectionVec().getY() * 0.5;
    face.posZ = face.posZ + (double) enumfacing.getDirectionVec().getZ() * 0.5;
    return getRotationFromPosition(face.posX, face.posY, face.posZ);
  }

  public static float[] getRotationFromPosition(double x, double y, double z) {
    double xDiff = x - Minecraft.getMinecraft().thePlayer.posX;
    double zDiff = z - Minecraft.getMinecraft().thePlayer.posZ;
    double yDiff = y - Minecraft.getMinecraft().thePlayer.posY - 1.2;
    double dist = (double) MathHelper.sqrt_double(xDiff * xDiff + zDiff * zDiff);
    float yaw = (float) (Math.atan2(zDiff, xDiff) * 180.0 / Math.PI) - 90.0F;
    float pitch = (float) (-(Math.atan2(yDiff, dist) * 180.0 / Math.PI));
    return new float[] {yaw, pitch};
  }

  public static Vec3 getVec3(BlockPos pos, EnumFacing face) {
    double x = (double) pos.getX() + 0.5;
    double y = (double) pos.getY() + 0.5;
    double z = (double) pos.getZ() + 0.5;
    x += (double) face.getFrontOffsetX() / 2.0;
    z += (double) face.getFrontOffsetZ() / 2.0;
    y += (double) face.getFrontOffsetY() / 2.0;
    if (face != EnumFacing.UP && face != EnumFacing.DOWN) {
      y += new Random().nextDouble() / 2.0 - 0.25;
    } else {
      x += new Random().nextDouble() / 2.0 - 0.25;
      z += new Random().nextDouble() / 2.0 - 0.25;
    }
    if (face == EnumFacing.WEST || face == EnumFacing.EAST) {
      z += new Random().nextDouble() / 2.0 - 0.25;
    }
    if (face == EnumFacing.SOUTH || face == EnumFacing.NORTH) {
      x += new Random().nextDouble() / 2.0 - 0.25;
    }
    return new Vec3(x, y, z);
  }
}
