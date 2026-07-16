package miau.module.modules.player.scaffold;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.settings.GameSettings;
import net.minecraft.init.Blocks;
import net.minecraft.item.Item;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.util.*;

public class GrimScaffoldUtils {
  private static final ItemBlock placeholderBlock = new ItemBlock(Blocks.tnt);

  public static class BlockData {
    private final BlockPos position;
    private final EnumFacing facing;

    public BlockData(BlockPos position, EnumFacing facing) {
      this.position = position;
      this.facing = facing;
    }

    public EnumFacing getFacing() {
      return facing;
    }

    public BlockPos getPosition() {
      return position;
    }
  }

  public static boolean willFallNextTick(Minecraft mc, double precision, float currentTickYaw) {
    AxisAlignedBB predictedBB = getPredictedBoundingBox(mc, precision, currentTickYaw).offset(0, -0.5, 0);
    return mc.theWorld.getCollidingBoundingBoxes(mc.thePlayer, predictedBB).isEmpty();
  }

  public static AxisAlignedBB getPredictedBoundingBox(Minecraft mc, double precision, float currentTickYaw) {
    EntityPlayerSP player = mc.thePlayer;

    double motionX = player.motionX;
    double motionZ = player.motionZ;

    float moveForward = 0;
    float moveStrafe = 0;
    GameSettings gameSettings = mc.gameSettings;

    if (gameSettings.keyBindForward.isKeyDown()) {
      ++moveForward;
    }
    if (gameSettings.keyBindBack.isKeyDown()) {
      --moveForward;
    }
    if (gameSettings.keyBindLeft.isKeyDown()) {
      ++moveStrafe;
    }
    if (gameSettings.keyBindRight.isKeyDown()) {
      --moveStrafe;
    }

    motionX *= 0.98;
    motionZ *= 0.98;

    if (Math.abs(motionX) < 0.005) {
      motionX = 0.0;
    }
    if (Math.abs(motionZ) < 0.005) {
      motionZ = 0.0;
    }

    moveStrafe *= 0.98F;
    moveForward *= 0.98F;

    float f4 = 0.91F;
    if (player.onGround) {
      BlockPos below = new BlockPos(
          MathHelper.floor_double(player.posX),
          MathHelper.floor_double(player.getEntityBoundingBox().minY) - 1,
          MathHelper.floor_double(player.posZ));
      f4 = mc.theWorld.getBlockState(below).getBlock().slipperiness * 0.91F;
    }

    float f = 0.16277136F / (f4 * f4 * f4);
    float f5 = player.getAIMoveSpeed() * f;

    f = moveStrafe * moveStrafe + moveForward * moveForward;
    if (f >= 1.0E-4F) {
      f = MathHelper.sqrt_float(f);
      if (f < 1.0F) {
        f = 1.0F;
      }

      f = f5 / f;
      moveStrafe *= f;
      moveForward *= f;
      float f1 = MathHelper.sin(currentTickYaw * (float) Math.PI / 180.0F);
      float f2 = MathHelper.cos(currentTickYaw * (float) Math.PI / 180.0F);
      motionX += (double) (moveStrafe * f2 - moveForward * f1);
      motionZ += (double) (moveForward * f2 + moveStrafe * f1);
    }

    return player.getEntityBoundingBox().offset(motionX * precision, 0, motionZ * precision);
  }

  public static BlockData findBestPlacement(Minecraft mc, float currentTickYaw, float currentPitch, boolean keepYVal, float[] rots) {
    EntityPlayerSP player = mc.thePlayer;
    BlockPos playerPos = new BlockPos(player);
    BlockPos scanY = playerPos.down();

    BlockData best = null;
    double bestScore = Double.MAX_VALUE;

    AxisAlignedBB predicted = getPredictedBoundingBox(mc, 1.0, currentTickYaw);
    double targetX = (predicted.minX + predicted.maxX) * 0.5;
    double targetZ = (predicted.minZ + predicted.maxZ) * 0.5;
    double targetY = scanY.getY() + 0.5;

    double existingScore = Double.MAX_VALUE;

    boolean tower = !player.onGround && !keepYVal;
    int lowestLayer = tower ? -1 : 0;

    for (int layer = 0; layer >= lowestLayer; layer--) {
      BlockPos layerPos = scanY.add(0, layer, 0);
      for (int x = -4; x <= 4; x++) {
        for (int z = -4; z <= 4; z++) {
          BlockPos pos = layerPos.add(x, 0, z);
          IBlockState state = mc.theWorld.getBlockState(pos);

          if (state.getBlock() == Blocks.air) continue;
          if (!state.getBlock().isFullCube()) continue;

          double exDx = (pos.getX() + 0.5) - targetX;
          double exDz = (pos.getZ() + 0.5) - targetZ;
          double exDy = (pos.getY() + 0.5) - targetY;
          double exScore = exDx * exDx + exDz * exDz + exDy * exDy * 0.25;
          if (exScore < existingScore) {
            existingScore = exScore;
          }

          List<EnumFacing> facings = new ArrayList<>(Arrays.asList(EnumFacing.HORIZONTALS));
          if (tower) {
            facings.add(EnumFacing.UP);
          }

          for (EnumFacing facing : facings) {
            if (!placeholderBlock.canPlaceBlockOnSide(
                mc.theWorld, pos, facing, player, player.getHeldItem())) {
              continue;
            }

            BlockPos neighbor = pos.offset(facing);
            IBlockState neighborState = mc.theWorld.getBlockState(neighbor);

            if (neighborState.getBlock() != Blocks.air) continue;

            double nbCenterX = neighbor.getX() + 0.5;
            double nbCenterY = neighbor.getY() + 0.5;
            double nbCenterZ = neighbor.getZ() + 0.5;

            double dx = nbCenterX - targetX;
            double dz = nbCenterZ - targetZ;
            double dy = nbCenterY - targetY;
            double score = dx * dx + dz * dz + dy * dy * 0.25;

            if (score >= bestScore) continue;

            float[] resolvedRots = getRotationsForFace(mc, pos, facing, rots[1]);
            if (resolvedRots == null) {
              resolvedRots = getFreeRotationsForFace(mc, pos, facing, rots);
            }

            Vec3 eyeVec = player.getPositionEyes(1.0f);
            Vec3 lookDir = getVectorForRotation(resolvedRots[1], resolvedRots[0]);
            Vec3 traceEnd = eyeVec.addVector(
                lookDir.xCoord * 4.5,
                lookDir.yCoord * 4.5,
                lookDir.zCoord * 4.5);
            MovingObjectPosition hit =
                player.worldObj.rayTraceBlocks(eyeVec, traceEnd, false, false, true);

            if (hit == null || hit.typeOfHit != MovingObjectPosition.MovingObjectType.BLOCK) {
              continue;
            }
            if (!hit.getBlockPos().equals(pos)) continue;
            if (hit.sideHit != facing) continue;

            bestScore = score;
            best = new BlockData(pos, facing);
          }
        }
      }
    }

    if (best != null && existingScore <= bestScore) {
      return null;
    }

    return best;
  }

  public static float[] getRotationsForFace(Minecraft mc, BlockPos blockPos, EnumFacing facing, float currentPitch) {
    EntityPlayerSP player = mc.thePlayer;

    double eyeX = player.posX;
    double eyeY = player.posY + player.getEyeHeight();
    double eyeZ = player.posZ;

    float lockedYaw = player.rotationYaw + 180f;
    float yawRad = (float) Math.toRadians(lockedYaw);

    double hx = -Math.sin(yawRad);
    double hz = Math.cos(yawRad);

    double bx0 = blockPos.getX(), bx1 = bx0 + 1.0;
    double by0 = blockPos.getY(), by1 = by0 + 1.0;
    double bz0 = blockPos.getZ(), bz1 = bz0 + 1.0;

    float bestPitch = Float.MAX_VALUE;
    float bestDiff = Float.MAX_VALUE;

    switch (facing) {
      case UP: {
        float pitch = pitchToHitPoint(eyeX, eyeY, eyeZ, hx, hz, bx0 + 0.5, by1, bz0 + 0.5);
        if (!Float.isNaN(pitch)) {
          float diff = Math.abs(MathHelper.wrapAngleTo180_float(pitch - currentPitch));
          if (diff < bestDiff) {
            bestDiff = diff;
            bestPitch = pitch;
          }
        }
        for (double cx : new double[] {bx0 + 0.1, bx1 - 0.1}) {
          for (double cz : new double[] {bz0 + 0.1, bz1 - 0.1}) {
            float p = pitchToHitPoint(eyeX, eyeY, eyeZ, hx, hz, cx, by1, cz);
            if (!Float.isNaN(p)) {
              float diff = Math.abs(MathHelper.wrapAngleTo180_float(p - currentPitch));
              if (diff < bestDiff) {
                bestDiff = diff;
                bestPitch = p;
              }
            }
          }
        }
        break;
      }
      case DOWN: {
        float pitch = pitchToHitPoint(eyeX, eyeY, eyeZ, hx, hz, bx0 + 0.5, by0, bz0 + 0.5);
        if (!Float.isNaN(pitch)) {
          float diff = Math.abs(MathHelper.wrapAngleTo180_float(pitch - currentPitch));
          if (diff < bestDiff) {
            bestDiff = diff;
            bestPitch = pitch;
          }
        }
        break;
      }
      case NORTH: {
        float[] candidates = pitchesToHitZPlane(eyeX, eyeY, eyeZ, hx, hz, bz0, bx0, bx1, by0, by1);
        for (float p : candidates) {
          if (!Float.isNaN(p)) {
            float diff = Math.abs(MathHelper.wrapAngleTo180_float(p - currentPitch));
            if (diff < bestDiff) {
              bestDiff = diff;
              bestPitch = p;
            }
          }
        }
        break;
      }
      case SOUTH: {
        float[] candidates = pitchesToHitZPlane(eyeX, eyeY, eyeZ, hx, hz, bz1, bx0, bx1, by0, by1);
        for (float p : candidates) {
          if (!Float.isNaN(p)) {
            float diff = Math.abs(MathHelper.wrapAngleTo180_float(p - currentPitch));
            if (diff < bestDiff) {
              bestDiff = diff;
              bestPitch = p;
            }
          }
        }
        break;
      }
      case WEST: {
        float[] candidates = pitchesToHitXPlane(eyeX, eyeY, eyeZ, hx, hz, bx0, by0, by1, bz0, bz1);
        for (float p : candidates) {
          if (!Float.isNaN(p)) {
            float diff = Math.abs(MathHelper.wrapAngleTo180_float(p - currentPitch));
            if (diff < bestDiff) {
              bestDiff = diff;
              bestPitch = p;
            }
          }
        }
        break;
      }
      case EAST: {
        float[] candidates = pitchesToHitXPlane(eyeX, eyeY, eyeZ, hx, hz, bx1, by0, by1, bz0, bz1);
        for (float p : candidates) {
          if (!Float.isNaN(p)) {
            float diff = Math.abs(MathHelper.wrapAngleTo180_float(p - currentPitch));
            if (diff < bestDiff) {
              bestDiff = diff;
              bestPitch = p;
            }
          }
        }
        break;
      }
    }

    if (bestPitch == Float.MAX_VALUE) {
      return null;
    }

    bestPitch = MathHelper.clamp_float(bestPitch, -90f, 90f);

    float[] lastRots = new float[] {lockedYaw, currentPitch};
    float[] targetRots = new float[] {lockedYaw, bestPitch};
    float[] fixedRots = patchGCD(mc, lastRots, targetRots);
    fixedRots[0] = lockedYaw;
    return fixedRots;
  }

  public static float[] getFreeRotationsForFace(Minecraft mc, BlockPos blockPos, EnumFacing facing, float[] currentRots) {
    EntityPlayerSP player = mc.thePlayer;

    double eyeX = player.posX;
    double eyeY = player.posY + player.getEyeHeight();
    double eyeZ = player.posZ;

    double faceCX = blockPos.getX() + 0.5 + facing.getFrontOffsetX() * 0.5;
    double faceCY = blockPos.getY() + 0.5 + facing.getFrontOffsetY() * 0.5;
    double faceCZ = blockPos.getZ() + 0.5 + facing.getFrontOffsetZ() * 0.5;

    double dx = faceCX - eyeX;
    double dy = faceCY - eyeY;
    double dz = faceCZ - eyeZ;

    float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
    float pitch = (float) -Math.toDegrees(Math.atan2(dy, Math.sqrt(dx * dx + dz * dz)));

    pitch = MathHelper.clamp_float(pitch, -90f, 90f);

    float currentYaw = currentRots[0];
    float currentPitch = currentRots[1];

    float[] lastRots = new float[] {currentYaw, currentPitch};
    float[] targetRots = new float[] {yaw, pitch};
    return patchGCD(mc, lastRots, targetRots);
  }

  public static float pitchToHitPoint(
      double eyeX, double eyeY, double eyeZ,
      double hx, double hz,
      double targetX, double targetY, double targetZ) {
    double dx = targetX - eyeX;
    double dy = targetY - eyeY;
    double dz = targetZ - eyeZ;

    double tCosp;
    if (Math.abs(hx) > Math.abs(hz)) {
      if (Math.abs(hx) < 1e-6) return Float.NaN;
      tCosp = dx / hx;
    } else {
      if (Math.abs(hz) < 1e-6) return Float.NaN;
      tCosp = dz / hz;
    }

    if (tCosp <= 0) return Float.NaN;

    double tanPitch = -dy / tCosp;
    return (float) Math.toDegrees(Math.atan(tanPitch));
  }

  public static float[] pitchesToHitZPlane(
      double eyeX, double eyeY, double eyeZ,
      double hx, double hz,
      double faceZ,
      double xMin, double xMax,
      double yMin, double yMax) {
    if (Math.abs(hz) < 1e-6) return new float[0];

    double tCosp = (faceZ - eyeZ) / hz;
    if (tCosp <= 0) return new float[0];

    double[] sampleY = {
        yMin + 0.1,
        yMin + (yMax - yMin) * 0.2,
        yMin + (yMax - yMin) * 0.3,
        yMin + (yMax - yMin) * 0.4,
        (yMin + yMax) * 0.5,
        yMin + (yMax - yMin) * 0.6,
        yMin + (yMax - yMin) * 0.7,
        yMin + (yMax - yMin) * 0.8,
        yMin + (yMax - yMin) * 0.9,
        yMax - 0.1
    };

    float[] results = new float[sampleY.length];
    int count = 0;
    for (double sy : sampleY) {
      double dy = sy - eyeY;
      double hitX = eyeX + hx * tCosp;
      if (hitX < xMin || hitX > xMax) continue;

      double tanPitch = -dy / tCosp;
      results[count++] = (float) Math.toDegrees(Math.atan(tanPitch));
    }

    float[] trimmed = new float[count];
    System.arraycopy(results, 0, trimmed, 0, count);
    return trimmed;
  }

  public static float[] pitchesToHitXPlane(
      double eyeX, double eyeY, double eyeZ,
      double hx, double hz,
      double faceX,
      double yMin, double yMax,
      double zMin, double zMax) {
    if (Math.abs(hx) < 1e-6) return new float[0];

    double tCosp = (faceX - eyeX) / hx;
    if (tCosp <= 0) return new float[0];

    double[] sampleY = {
        yMin + 0.1,
        yMin + (yMax - yMin) * 0.2,
        yMin + (yMax - yMin) * 0.3,
        yMin + (yMax - yMin) * 0.4,
        (yMin + yMax) * 0.5,
        yMin + (yMax - yMin) * 0.6,
        yMin + (yMax - yMin) * 0.7,
        yMin + (yMax - yMin) * 0.8,
        yMin + (yMax - yMin) * 0.9,
        yMax - 0.1
    };

    float[] results = new float[sampleY.length];
    int count = 0;
    for (double sy : sampleY) {
      double dy = sy - eyeY;
      double hitZ = eyeZ + hz * tCosp;
      if (hitZ < zMin || hitZ > zMax) continue;

      double tanPitch = -dy / tCosp;
      results[count++] = (float) Math.toDegrees(Math.atan(tanPitch));
    }

    float[] trimmed = new float[count];
    System.arraycopy(results, 0, trimmed, 0, count);
    return trimmed;
  }

  public static float[] patchGCD(Minecraft mc, float[] prevRotation, float[] currentRotation) {
    float f = mc.gameSettings.mouseSensitivity * 0.6F + 0.2F;
    float gcd = f * f * f * 8.0F * 0.15F;
    final float deltaYaw = currentRotation[0] - prevRotation[0],
        deltaPitch = currentRotation[1] - prevRotation[1];
    final float yaw = prevRotation[0] + Math.round(deltaYaw / gcd) * gcd,
        pitch = prevRotation[1] + Math.round(deltaPitch / gcd) * gcd;

    return new float[] {yaw, pitch};
  }

  public static Vec3 getVectorForRotation(float pitch, float yaw) {
    float f = MathHelper.cos(-yaw * 0.017453292F - (float) Math.PI);
    float f1 = MathHelper.sin(-yaw * 0.017453292F - (float) Math.PI);
    float f2 = -MathHelper.cos(-pitch * 0.017453292F);
    float f3 = MathHelper.sin(-pitch * 0.017453292F);
    return new Vec3((double) (f1 * f2), (double) f3, (double) (f * f2));
  }

  public static Vec3 getNewVector(BlockPos pos, EnumFacing facing) {
    Vec3 vec3 = new Vec3(pos);

    double amount1 = 0.45 + Math.random() * 0.1;
    double amount2 = 0.45 + Math.random() * 0.1;

    if (facing == EnumFacing.UP) {
      vec3 = vec3.addVector(amount1, 1, amount2);
    } else if (facing == EnumFacing.DOWN) {
      vec3 = vec3.addVector(amount1, 0, amount2);
    } else if (facing == EnumFacing.EAST) {
      vec3 = vec3.addVector(1, amount1, amount2);
    } else if (facing == EnumFacing.WEST) {
      vec3 = vec3.addVector(0, amount1, amount2);
    } else if (facing == EnumFacing.NORTH) {
      vec3 = vec3.addVector(amount1, amount2, 0);
    } else if (facing == EnumFacing.SOUTH) {
      vec3 = vec3.addVector(amount1, amount2, 1);
    }

    return vec3;
  }

  public static Item keyBlock(Minecraft mc) {
    ItemStack currentItem = mc.thePlayer.inventory.getCurrentItem();
    if (currentItem == null || !(currentItem.getItem() instanceof ItemBlock)) {
      mc.thePlayer.inventory.currentItem = getBlockSlot(mc);
    }
    currentItem = mc.thePlayer.inventory.getCurrentItem();
    if (currentItem == null) {
      return null;
    }
    return currentItem.getItem();
  }

  public static int getBlockSlot(Minecraft mc) {
    for (int i = 0; i < 9; i++) {
      ItemStack itemStack = mc.thePlayer.inventory.mainInventory[i];
      if (itemStack != null && itemStack.getItem() instanceof ItemBlock && itemStack.stackSize > 0) {
        ItemBlock itemBlock = (ItemBlock) itemStack.getItem();
        if (isBlockValid(itemBlock.getBlock())) {
          return i;
        }
      }
    }
    return mc.thePlayer.inventory.currentItem;
  }

  public static boolean isBlockValid(Block block) {
    return (block.isFullBlock() || block == Blocks.glass)
        && block != Blocks.sand
        && block != Blocks.gravel
        && block != Blocks.dispenser
        && block != Blocks.command_block
        && block != Blocks.noteblock
        && block != Blocks.furnace
        && block != Blocks.crafting_table
        && block != Blocks.tnt
        && block != Blocks.dropper
        && block != Blocks.beacon;
  }

  public static MovingObjectPosition rayTracePost(Minecraft mc, float yaw, float pitch) {
    Vec3 vec3 = mc.thePlayer.getPositionEyes(1.0f);
    Vec3 vec31 = getVectorForRotation(pitch, yaw);
    Vec3 vec32 = vec3.addVector(vec31.xCoord * 4.5, vec31.yCoord * 4.5, vec31.zCoord * 4.5);
    return mc.thePlayer.worldObj.rayTraceBlocks(vec3, vec32, false, false, true);
  }
}
