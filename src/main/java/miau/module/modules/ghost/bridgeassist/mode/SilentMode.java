package miau.module.modules.ghost.bridgeassist.mode;

import java.util.Arrays;
import java.util.List;
import miau.event.impl.MoveInputEvent;
import miau.event.impl.UpdateEvent;
import miau.event.types.EventType;
import miau.module.modules.ghost.BridgeAssist;
import miau.property.Property;
import miau.property.properties.BooleanProperty;
import miau.property.properties.FloatProperty;
import miau.property.properties.ModeProperty;
import miau.util.player.RotationUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.util.BlockPos;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.MathHelper;

public class SilentMode {
  private static final Minecraft mc = Minecraft.getMinecraft();
  private final BridgeAssist parent;

  private static final float GODBRIDGE_PITCH = 75.6f;
  private static final float MOONWALK_PITCH = 79.6f;
  private static final float BREEZILY_PITCH = 79.9f;

  private boolean active;
  private boolean isOnRightSide;
  private float lastSentYaw;
  private float lastSentPitch;

  public final FloatProperty rotSpeed;
  public final FloatProperty pitchSpeed;
  public final ModeProperty bridgeType;
  public final BooleanProperty onlySneak;

  public SilentMode(BridgeAssist parent) {
    this.parent = parent;
    this.rotSpeed =
        new FloatProperty(
            "rot-speed", 30f, 5f, 180f, () -> parent.mode.getModeString().equals("Silent"));
    this.pitchSpeed =
        new FloatProperty(
            "pitch-speed", 20f, 5f, 90f, () -> parent.mode.getModeString().equals("Silent"));
    this.bridgeType =
        new ModeProperty(
            "bridge-type",
            0,
            new String[] {"GodBridge", "Moonwalk", "Breezily"},
            () -> parent.mode.getModeString().equals("Silent"));
    this.onlySneak =
        new BooleanProperty("only-sneak", true, () -> parent.mode.getModeString().equals("Silent"));
  }

  public List<Property<?>> getProperties() {
    return Arrays.asList(rotSpeed, pitchSpeed, bridgeType, onlySneak);
  }

  public void onDisabled() {
    this.active = false;
  }

  public void onMoveInput(MoveInputEvent event) {}

  public void onUpdate(UpdateEvent e) {
    if (e.getType() != EventType.PRE) return;
    if (mc.thePlayer == null || mc.theWorld == null) return;
    if (!mc.thePlayer.onGround) {
      active = false;
      return;
    }
    if (onlySneak.getValue() && !mc.thePlayer.isSneaking()) {
      active = false;
      return;
    }

    if (!isPlayerOverAir()) {
      active = false;
      return;
    }

    float targetPitch = getTargetPitch();
    float targetYaw = calculateTargetYaw();

    if (!active) {
      lastSentYaw = e.getYaw();
      lastSentPitch = e.getPitch();
      active = true;
    }

    float yawDelta = MathHelper.wrapAngleTo180_float(targetYaw - lastSentYaw);
    float pitchDelta = targetPitch - lastSentPitch;

    float maxYawStep = rotSpeed.getValue();
    float maxPitchStep = pitchSpeed.getValue();

    float steppedYaw =
        RotationUtil.quantizeAngle(lastSentYaw + RotationUtil.clampAngle(yawDelta, maxYawStep));
    float steppedPitch =
        RotationUtil.quantizeAngle(
            MathHelper.clamp_float(
                lastSentPitch + RotationUtil.clampAngle(pitchDelta, maxPitchStep), -90f, 90f));

    lastSentYaw = steppedYaw;
    lastSentPitch = steppedPitch;

    e.setRotation(steppedYaw, steppedPitch, 2);
  }

  private float calculateTargetYaw() {
    float forward = mc.thePlayer.movementInput.moveForward;
    float strafe = mc.thePlayer.movementInput.moveStrafe;

    if (forward == 0 && strafe == 0) {
      float axisMovement = (float) Math.floor(mc.thePlayer.rotationYaw / 90.0f) * 90.0f;
      return RotationUtil.quantizeAngle(axisMovement + 45.0f);
    }

    float direction = getMovementDirection(forward, strafe, mc.thePlayer.rotationYaw) + 180.0f;
    float movingYaw = Math.round(direction / 45.0f) * 45.0f;
    boolean isMovingStraight = (movingYaw % 90.0f) == 0.0f;

    if (!isMovingStraight || !bridgeType.getModeString().equals("GodBridge")) {
      return RotationUtil.quantizeAngle(movingYaw);
    }

    if (mc.thePlayer.onGround) {
      isOnRightSide =
          Math.floor(mc.thePlayer.posX + Math.cos(Math.toRadians(movingYaw)) * 0.5)
                  != Math.floor(mc.thePlayer.posX)
              || Math.floor(mc.thePlayer.posZ + Math.sin(Math.toRadians(movingYaw)) * 0.5)
                  != Math.floor(mc.thePlayer.posZ);

      EnumFacing facing = EnumFacing.fromAngle(movingYaw);
      BlockPos posInDirection =
          new BlockPos(
              mc.thePlayer.posX + facing.getFrontOffsetX() * 0.6,
              mc.thePlayer.posY,
              mc.thePlayer.posZ + facing.getFrontOffsetZ() * 0.6);

      BlockPos currentPos = new BlockPos(mc.thePlayer.posX, mc.thePlayer.posY, mc.thePlayer.posZ);
      boolean isLeaningOff = mc.theWorld.isAirBlock(currentPos.down());
      boolean nextIsAir = mc.theWorld.isAirBlock(posInDirection.down());

      if (isLeaningOff && nextIsAir) {
        isOnRightSide = !isOnRightSide;
      }
    }

    float finalYaw = movingYaw + (isOnRightSide ? 45.0f : -45.0f);
    return RotationUtil.quantizeAngle(finalYaw);
  }

  private float getTargetPitch() {
    switch (bridgeType.getModeString()) {
      case "Moonwalk":
        return MOONWALK_PITCH;
      case "Breezily":
        return BREEZILY_PITCH;
      default:
        return GODBRIDGE_PITCH;
    }
  }

  private static boolean isPlayerOverAir() {
    double x = mc.thePlayer.posX;
    double y = mc.thePlayer.posY - 1.0;
    double z = mc.thePlayer.posZ;
    BlockPos p =
        new BlockPos(
            MathHelper.floor_double(x), MathHelper.floor_double(y), MathHelper.floor_double(z));
    return mc.theWorld.isAirBlock(p);
  }

  private static float getMovementDirection(float forward, float strafe, float yaw) {
    if (forward == 0 && strafe == 0) return yaw;
    boolean reversed = forward < 0.0f;
    float strafingYaw = 90.0f * (forward > 0.0f ? 0.5f : reversed ? -0.5f : 1.0f);
    if (reversed) yaw += 180.0f;
    if (strafe > 0.0f) yaw -= strafingYaw;
    else if (strafe < 0.0f) yaw += strafingYaw;
    return yaw;
  }
}
