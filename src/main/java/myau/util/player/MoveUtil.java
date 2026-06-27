package myau.util.player;

import myau.Myau;
import myau.management.RotationState;
import myau.module.modules.combat.TargetStrafe;
import myau.util.animation.*;
import myau.util.client.*;
import myau.util.math.*;
import myau.util.misc.*;
import myau.util.network.*;
import myau.util.render.*;
import myau.util.time.*;
import myau.util.world.*;
import net.minecraft.client.Minecraft;
import net.minecraft.potion.Potion;
import net.minecraft.util.BlockPos;
import net.minecraft.util.MathHelper;
import org.lwjgl.input.Keyboard;

public class MoveUtil {
  private static final Minecraft mc = Minecraft.getMinecraft();

  public static boolean isForwardPressed() {
    if (MoveUtil.mc.gameSettings.keyBindForward.isKeyDown()
        != MoveUtil.mc.gameSettings.keyBindBack.isKeyDown()) return true;
    return MoveUtil.mc.gameSettings.keyBindLeft.isKeyDown()
        != MoveUtil.mc.gameSettings.keyBindRight.isKeyDown();
  }

  public static boolean isMoving() {
    return mc.thePlayer != null
        && (mc.thePlayer.movementInput.moveForward != 0.0f
            || mc.thePlayer.movementInput.moveStrafe != 0.0f);
  }

  // Gothaj: keybind-based isMoving
  public static boolean isMovingKeybinds() {
    return Minecraft.getMinecraft().gameSettings.keyBindForward.isKeyDown()
        || Minecraft.getMinecraft().gameSettings.keyBindBack.isKeyDown()
        || Minecraft.getMinecraft().gameSettings.keyBindRight.isKeyDown()
        || Minecraft.getMinecraft().gameSettings.keyBindLeft.isKeyDown();
  }

  public static int getForwardValue() {
    int forwardValue = 0;
    if (MoveUtil.mc.gameSettings.keyBindForward.isKeyDown()) {
      ++forwardValue;
    }
    if (MoveUtil.mc.gameSettings.keyBindBack.isKeyDown()) {
      --forwardValue;
    }
    return forwardValue;
  }

  public static int getLeftValue() {
    int leftValue = 0;
    if (MoveUtil.mc.gameSettings.keyBindLeft.isKeyDown()) {
      ++leftValue;
    }
    if (MoveUtil.mc.gameSettings.keyBindRight.isKeyDown()) {
      --leftValue;
    }
    return leftValue;
  }

  public static float getMoveYaw() {
    return MoveUtil.adjustYaw(
        RotationState.isActived()
            ? RotationState.getSmoothedYaw()
            : MoveUtil.mc.thePlayer.rotationYaw,
        MoveUtil.mc.thePlayer.movementInput.moveForward,
        MoveUtil.mc.thePlayer.movementInput.moveStrafe);
  }

  public static float adjustYaw(float yaw, float forward, float strafe) {
    TargetStrafe targetStrafe = (TargetStrafe) Myau.moduleManager.modules.get(TargetStrafe.class);
    if (targetStrafe.isEnabled()) {
      if (!Float.isNaN(targetStrafe.getTargetYaw())) {
        return targetStrafe.getTargetYaw();
      }
    }
    if (forward < 0.0f) {
      yaw += 180.0f;
    }
    if (strafe != 0.0f) {
      float multiplier = forward == 0.0f ? 1.0f : 0.5f * Math.signum(forward);
      yaw += -90.0f * multiplier * Math.signum(strafe);
    }
    return MathHelper.wrapAngleTo180_float(yaw);
  }

  public static float getDirectionYaw() {
    if (MoveUtil.getSpeed() == 0.0) {
      return MathHelper.wrapAngleTo180_float(MoveUtil.mc.thePlayer.rotationYaw);
    }
    return MathHelper.wrapAngleTo180_float(
        (float)
                Math.toDegrees(
                    Math.atan2(MoveUtil.mc.thePlayer.motionZ, MoveUtil.mc.thePlayer.motionX))
            - 90.0f);
  }

  public static double getBaseMoveSpeed() {
    double baseSpeed = 0.28015;
    if (MoveUtil.getSpeedTime() > 0) {
      baseSpeed = 0.28015 * (1.0 + 0.15 * (double) MoveUtil.getSpeedLevel());
    }
    return baseSpeed;
  }

  // Gothaj base move speed (used in scaffold's tower mode)
  public static double getBaseMoveSpeedGothaj() {
    double baseSpeed = 0.2873;
    if (mc.thePlayer.isPotionActive(Potion.moveSpeed)) {
      baseSpeed *=
          1.0
              + 0.2
                  * (double)
                      (mc.thePlayer.getActivePotionEffect(Potion.moveSpeed).getAmplifier() + 1);
    }
    return baseSpeed;
  }

  public static double getBaseJumpHigh(int speedLevel) {
    double jumpHeight = 0.452;
    if (speedLevel == 1) {
      jumpHeight = 0.49720000000000003;
    } else if (speedLevel >= 2) {
      jumpHeight *= 1.2;
    }
    return jumpHeight;
  }

  public static double getJumpMotion() {
    int speedLevel = 0;
    if (MoveUtil.getSpeedTime() > 0) {
      speedLevel = MoveUtil.getSpeedLevel();
    }
    return MoveUtil.getBaseJumpHigh(speedLevel);
  }

  public static double getSpeed() {
    return MoveUtil.getSpeed(MoveUtil.mc.thePlayer.motionX, MoveUtil.mc.thePlayer.motionZ);
  }

  public static double getSpeed(double motionX, double motionZ) {
    return Math.hypot(motionX, motionZ);
  }

  public static void setSpeed(double speed) {
    MoveUtil.setSpeed(speed, MoveUtil.getDirectionYaw());
  }

  public static void setSpeed(double speed, float yaw) {
    MoveUtil.mc.thePlayer.motionX = -Math.sin(Math.toRadians(yaw)) * speed;
    MoveUtil.mc.thePlayer.motionZ = Math.cos(Math.toRadians(yaw)) * speed;
  }

  public static void addSpeed(double speed, float yaw) {
    MoveUtil.mc.thePlayer.motionX += -Math.sin(Math.toRadians(yaw)) * speed;
    MoveUtil.mc.thePlayer.motionZ += Math.cos(Math.toRadians(yaw)) * speed;
  }

  public static int getSpeedLevel() {
    int speedLevel = 0;
    if (MoveUtil.mc.thePlayer.isPotionActive(Potion.moveSpeed)) {
      speedLevel =
          (MoveUtil.mc.thePlayer.getActivePotionEffect(Potion.moveSpeed).getAmplifier() + 1);
    }
    return speedLevel;
  }

  public static int getSpeedTime() {
    if (MoveUtil.mc.thePlayer.isPotionActive(Potion.moveSpeed)) {
      return MoveUtil.mc.thePlayer.getActivePotionEffect(Potion.moveSpeed).getDuration();
    }
    return 0;
  }

  public static float getAllowedHorizontalDistance() {
    float slipperiness =
        MoveUtil.mc
                .thePlayer
                .worldObj
                .getBlockState(
                    new BlockPos(
                        MathHelper.floor_double(MoveUtil.mc.thePlayer.posX),
                        MathHelper.floor_double(MoveUtil.mc.thePlayer.getEntityBoundingBox().minY)
                            - 1,
                        MathHelper.floor_double(MoveUtil.mc.thePlayer.posZ)))
                .getBlock()
                .slipperiness
            * 0.91f;
    return MoveUtil.mc.thePlayer.getAIMoveSpeed()
        * (0.16277136f / (slipperiness * slipperiness * slipperiness));
  }

  public static double[] predictMovement() {
    float strafeInput = (float) MoveUtil.getLeftValue() * 0.98f;
    float forwardInput = (float) MoveUtil.getForwardValue() * 0.98f;
    float inputMagnitude = strafeInput * strafeInput + forwardInput * forwardInput;
    if (inputMagnitude >= 1.0E-4f) {
      inputMagnitude = MathHelper.sqrt_float(inputMagnitude);
      if (inputMagnitude < 1.0f) {
        inputMagnitude = 1.0f;
      }
      inputMagnitude = MoveUtil.getAllowedHorizontalDistance() / inputMagnitude;
      float sinYaw = MathHelper.sin(MoveUtil.mc.thePlayer.rotationYaw * (float) Math.PI / 180.0f);
      float cosYaw = MathHelper.cos(MoveUtil.mc.thePlayer.rotationYaw * (float) Math.PI / 180.0f);
      strafeInput *= inputMagnitude;
      forwardInput *= inputMagnitude;
      return new double[] {
        strafeInput * cosYaw - forwardInput * sinYaw, forwardInput * cosYaw + strafeInput * sinYaw
      };
    }
    return new double[] {0.0, 0.0};
  }

  public static void fixStrafe(float targetYaw) {
    float angle =
        MathHelper.wrapAngleTo180_float(
            MoveUtil.adjustYaw(
                    MoveUtil.mc.thePlayer.rotationYaw,
                    MoveUtil.getForwardValue(),
                    MoveUtil.getLeftValue())
                - targetYaw
                + 22.5f);
    switch ((int) (angle + 180.0f) / 45 % 8) {
      case 0:
        {
          MoveUtil.mc.thePlayer.movementInput.moveForward = -1.0f;
          MoveUtil.mc.thePlayer.movementInput.moveStrafe = 0.0f;
          break;
        }
      case 1:
        {
          MoveUtil.mc.thePlayer.movementInput.moveForward = -1.0f;
          MoveUtil.mc.thePlayer.movementInput.moveStrafe = 1.0f;
          break;
        }
      case 2:
        {
          MoveUtil.mc.thePlayer.movementInput.moveForward = 0.0f;
          MoveUtil.mc.thePlayer.movementInput.moveStrafe = 1.0f;
          break;
        }
      case 3:
        {
          MoveUtil.mc.thePlayer.movementInput.moveForward = 1.0f;
          MoveUtil.mc.thePlayer.movementInput.moveStrafe = 1.0f;
          break;
        }
      case 4:
        {
          MoveUtil.mc.thePlayer.movementInput.moveForward = 1.0f;
          MoveUtil.mc.thePlayer.movementInput.moveStrafe = 0.0f;
          break;
        }
      case 5:
        {
          MoveUtil.mc.thePlayer.movementInput.moveForward = 1.0f;
          MoveUtil.mc.thePlayer.movementInput.moveStrafe = -1.0f;
          break;
        }
      case 6:
        {
          MoveUtil.mc.thePlayer.movementInput.moveForward = 0.0f;
          MoveUtil.mc.thePlayer.movementInput.moveStrafe = -1.0f;
          break;
        }
      case 7:
        {
          MoveUtil.mc.thePlayer.movementInput.moveForward = -1.0f;
          MoveUtil.mc.thePlayer.movementInput.moveStrafe = -1.0f;
          break;
        }
    }
    if (MoveUtil.mc.thePlayer.movementInput.sneak) {
      MoveUtil.mc.thePlayer.movementInput.moveForward *= 0.3f;
      MoveUtil.mc.thePlayer.movementInput.moveStrafe *= 0.3f;
    }
  }

  public static void fixMovement(final float yaw) {
    final float forward = mc.thePlayer.movementInput.moveForward;
    final float strafe = mc.thePlayer.movementInput.moveStrafe;
    if (forward == 0 && strafe == 0) return;

    final double angle =
        MathHelper.wrapAngleTo180_double(
            Math.toDegrees(direction(mc.thePlayer.rotationYaw, forward, strafe)));
    float closestForward = 0, closestStrafe = 0;
    double closestDifference = Double.MAX_VALUE;

    for (float predictedForward = -1F; predictedForward <= 1F; predictedForward += 1F) {
      for (float predictedStrafe = -1F; predictedStrafe <= 1F; predictedStrafe += 1F) {
        if (predictedStrafe == 0 && predictedForward == 0) continue;

        final double predictedAngle =
            MathHelper.wrapAngleTo180_double(
                Math.toDegrees(direction(yaw, predictedForward, predictedStrafe)));
        final double difference = wrappedDifference(angle, predictedAngle);

        if (difference < closestDifference) {
          closestDifference = difference;
          closestForward = predictedForward;
          closestStrafe = predictedStrafe;
        }
      }
    }

    mc.thePlayer.movementInput.moveForward = closestForward;
    mc.thePlayer.movementInput.moveStrafe = closestStrafe;
    if (mc.thePlayer.movementInput.sneak) {
      mc.thePlayer.movementInput.moveForward *= 0.3f;
      mc.thePlayer.movementInput.moveStrafe *= 0.3f;
    }
  }

  public static double direction(
      float rotationYaw, final double moveForward, final double moveStrafing) {
    if (moveForward < 0F) rotationYaw += 180F;
    float forward = 1F;
    if (moveForward < 0F) forward = -0.5F;
    else if (moveForward > 0F) forward = 0.5F;
    if (moveStrafing > 0F) rotationYaw -= 90F * forward;
    if (moveStrafing < 0F) rotationYaw += 90F * forward;
    return Math.toRadians(rotationYaw);
  }

  public static double wrappedDifference(double number1, double number2) {
    return Math.min(
        Math.abs(number1 - number2),
        Math.min(
            Math.abs(number1 - 360) - Math.abs(number2 - 0),
            Math.abs(number2 - 360) - Math.abs(number1 - 0)));
  }

  // ===== Gothaj MovementUtils methods =====

  public static boolean isOnGround(double height) {
    return !mc.theWorld
        .getCollidingBoundingBoxes(
            mc.thePlayer, mc.thePlayer.getEntityBoundingBox().offset(0.0, -height, 0.0))
        .isEmpty();
  }

  public static boolean isGoingDiagonally() {
    return Math.abs(mc.thePlayer.motionX) > 0.04 && Math.abs(mc.thePlayer.motionZ) > 0.04;
  }

  public static double getDirection(float yaw) {
    float rotationYaw = yaw;
    if (mc.thePlayer.moveForward < 0.0F) {
      rotationYaw = yaw + 180.0F;
    }
    float forward = 1.0F;
    if (mc.thePlayer.moveForward < 0.0F) {
      forward = -0.5F;
    } else if (mc.thePlayer.moveForward > 0.0F) {
      forward = 0.5F;
    }
    if (mc.thePlayer.moveStrafing > 0.0F) {
      rotationYaw -= 90.0F * forward;
    }
    if (mc.thePlayer.moveStrafing < 0.0F) {
      rotationYaw += 90.0F * forward;
    }
    return Math.toRadians((double) rotationYaw);
  }

  public static double getDirectionKeybinds(float yaw) {
    float rotationYaw = yaw;
    if (!Keyboard.isKeyDown(mc.gameSettings.keyBindForward.getKeyCode())
        && Keyboard.isKeyDown(mc.gameSettings.keyBindBack.getKeyCode())) {
      rotationYaw = yaw + 180.0F;
    }
    float forward = 1.0F;
    if (!Keyboard.isKeyDown(mc.gameSettings.keyBindForward.getKeyCode())
        && Keyboard.isKeyDown(mc.gameSettings.keyBindBack.getKeyCode())) {
      forward = -0.5F;
    } else if (Keyboard.isKeyDown(mc.gameSettings.keyBindForward.getKeyCode())
        && !Keyboard.isKeyDown(mc.gameSettings.keyBindBack.getKeyCode())) {
      forward = 0.5F;
    }
    if (Keyboard.isKeyDown(mc.gameSettings.keyBindLeft.getKeyCode())
        && !Keyboard.isKeyDown(mc.gameSettings.keyBindRight.getKeyCode())) {
      rotationYaw -= 90.0F * forward;
    }
    if (!Keyboard.isKeyDown(mc.gameSettings.keyBindLeft.getKeyCode())
        && Keyboard.isKeyDown(mc.gameSettings.keyBindRight.getKeyCode())) {
      rotationYaw += 90.0F * forward;
    }
    return Math.toRadians((double) rotationYaw);
  }

  public static void strafe(float speed) {
    double yaw = getDirection(mc.thePlayer.rotationYaw);
    mc.thePlayer.motionX = -Math.sin((double) ((float) yaw)) * (double) speed;
    mc.thePlayer.motionZ = Math.cos((double) ((float) yaw)) * (double) speed;
  }

  // Gothaj silentMoveFix adapted for Miau's StrafeEvent
  public static void silentMoveFix(myau.event.impl.StrafeEvent event) {
    int dif =
        (int)
            ((MathHelper.wrapAngleTo180_float(
                        mc.thePlayer.rotationYaw - RotationUtil.serverYaw - 23.5F - 135.0F)
                    + 180.0F)
                / 45.0F);
    float yaw = RotationUtil.serverYaw;
    float strafe = event.getStrafe();
    float forward = event.getForward();
    float friction = event.getFriction();
    float calcForward = 0.0F;
    float calcStrafe = 0.0F;
    switch (dif) {
      case 0:
        calcForward = forward;
        calcStrafe = strafe;
        break;
      case 1:
        calcForward += forward;
        calcStrafe -= forward;
        calcForward += strafe;
        calcStrafe += strafe;
        break;
      case 2:
        calcForward = strafe;
        calcStrafe = -forward;
        break;
      case 3:
        calcForward -= forward;
        calcStrafe -= forward;
        calcForward += strafe;
        calcStrafe -= strafe;
        break;
      case 4:
        calcForward = -forward;
        calcStrafe = -strafe;
        break;
      case 5:
        calcForward -= forward;
        calcStrafe += forward;
        calcForward -= strafe;
        calcStrafe -= strafe;
        break;
      case 6:
        calcForward = -strafe;
        calcStrafe = forward;
        break;
      case 7:
        calcForward += forward;
        calcStrafe += forward;
        calcForward -= strafe;
        calcStrafe += strafe;
    }
    if (calcForward > 1.0F
        || calcForward < 0.9F && calcForward > 0.3F
        || calcForward < -1.0F
        || calcForward > -0.9F && calcForward < -0.3F) {
      calcForward *= 0.5F;
    }
    if (calcStrafe > 1.0F
        || calcStrafe < 0.9F && calcStrafe > 0.3F
        || calcStrafe < -1.0F
        || calcStrafe > -0.9F && calcStrafe < -0.3F) {
      calcStrafe *= 0.5F;
    }
    float d = calcStrafe * calcStrafe + calcForward * calcForward;
    if (d >= 1.0E-4F) {
      d = MathHelper.sqrt_float(d);
      if (d < 1.0F) {
        d = 1.0F;
      }
      d = friction / d;
      calcStrafe *= d;
      calcForward *= d;
      float yawSin = MathHelper.sin((float) ((double) yaw * Math.PI / 180.0));
      float yawCos = MathHelper.cos((float) ((double) yaw * Math.PI / 180.0));
      mc.thePlayer.motionX += (double) (calcStrafe * yawCos - calcForward * yawSin);
      mc.thePlayer.motionZ += (double) (calcForward * yawCos + calcStrafe * yawSin);
    }
  }
}
