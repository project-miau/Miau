package miau.module.modules.player;

import java.awt.*;
import java.util.ArrayList;
import java.util.Comparator;
import miau.Miau;
import miau.enums.BlinkModules;
import miau.event.EventTarget;
import miau.event.impl.*;
import miau.event.types.EventType;
import miau.event.types.Priority;
import miau.management.RotationState;
import miau.mixin.IAccessorMinecraft;
import miau.module.Module;
import miau.module.modules.movement.LongJump;
import miau.module.modules.render.PostProcessing;
import miau.property.properties.BooleanProperty;
import miau.property.properties.FloatProperty;
import miau.property.properties.IntProperty;
import miau.property.properties.ModeProperty;
import miau.property.properties.PercentProperty;
import miau.util.client.KeyBindUtil;
import miau.util.font.FontRepository;
import miau.util.math.RandomUtil;
import miau.util.player.*;
import miau.util.shader.RoundedUtils;
import miau.util.world.BlockUtil;
import net.minecraft.block.Block;
import net.minecraft.block.BlockAir;
import net.minecraft.client.Minecraft;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.item.Item;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.potion.Potion;
import net.minecraft.util.*;
import net.minecraft.util.MovingObjectPosition.MovingObjectType;
import net.minecraft.world.WorldSettings.GameType;
import org.lwjgl.input.Keyboard;

public class Scaffold extends Module {
  private static final Minecraft mc = Minecraft.getMinecraft();
  private static final int ROTATION_SNAP = 6;
  private static final int ROTATION_BETA = 7;
  private static final double[] placeOffsets =
      new double[] {
        0.03125, 0.09375, 0.15625, 0.21875, 0.28125, 0.34375, 0.40625, 0.46875, 0.53125, 0.59375,
        0.65625, 0.71875, 0.78125, 0.84375, 0.90625, 0.96875
      };
  private int rotationTick = 0;
  private int lastSlot = -1;
  private int blockCount = -1;
  private float animationProgress = 0f;
  private long lastFrame = System.currentTimeMillis();
  private float yaw = -180.0F;
  private float pitch = 0.0F;
  private boolean canRotate = false;
  private int towerTick = 0;
  private int towerDelay = 0;
  private int stage = 0;
  private int startY = 256;
  private boolean shouldKeepY = false;
  private boolean towering = false;
  private EnumFacing targetFacing = null;
  private int safeStuckTicks = 0;
  private int safeStuckDelayTicks = 0;
  private double safePrevMotionY = 0.0;
  private double savedMotionX;
  private double savedMotionY;
  private double savedMotionZ;
  private boolean safeStuckActive = false;
  private boolean snapRotating = false;
  private boolean placedThisTick = false;
  private int betaAirTicks = 0;
  private int betaGroundTicks = 0;
  private int betaPlaceCooldown = 0;
  private float lastSnapPlaceYaw = Float.NaN;
  private float lastSnapPlacePitch = Float.NaN;
  private float lastBetaSentYaw = Float.NaN;
  private float lastBetaSentPitch = Float.NaN;
  private long lastBetaPitchQuotient = 0L;
  private int betaPlaceTicks = 999;
  public final ModeProperty rotationMode =new ModeProperty("rotations",2,new String[] {"NONE","Normal","Backwards","Sideways","Godbridge","Smooth","Snap","Beta"});
  public final FloatProperty tellystartrotationminspeed =new FloatProperty("telly-start-rotation-min-speed",90.0F,1.0F,180.0F,() -> this.keepY.getValue() == 3 || this.keepY.getValue() == 4);
  public final FloatProperty tellystartrotationmaxspeed =new FloatProperty("telly-start-rotation-max-speed",95.0F,1.0F,180.0F,() -> this.keepY.getValue() == 3 || this.keepY.getValue() == 4);
  public final FloatProperty tellynormalrotationminspeed =new FloatProperty("telly-normal-rotation-min-speed",30.0F,1.0F,180.0F,() -> this.keepY.getValue() == 3 || this.keepY.getValue() == 4);
  public final FloatProperty tellynormalrotationmaxspeed =new FloatProperty("telly-normal-rotation-max-speed",35.0F,1.0F,180.0F,() -> this.keepY.getValue() == 3 || this.keepY.getValue() == 4);
  public final ModeProperty moveFix =new ModeProperty("move-fix", 1, new String[] {"NONE", "SILENT"});
  public final ModeProperty rayCast =new ModeProperty("ray-cast",2,new String[] {"Off","Normal","Strict"});
  public final ModeProperty sprintMode =new ModeProperty("sprint", 0, new String[] {"NONE", "VANILLA"});
  public final PercentProperty groundMotion = new PercentProperty("ground-motion", 100);
  public final PercentProperty airMotion = new PercentProperty("air-motion", 100);
  public final PercentProperty speedMotion = new PercentProperty("speed-motion", 100);
  public final ModeProperty tower =new ModeProperty("tower", 0, new String[] {"NONE", "VANILLA", "EXTRA", "TELLY"});
  public final BooleanProperty hypixeltower = new BooleanProperty("hypixeltower", false, () -> this.tower.getValue() == 3);
  public final BooleanProperty safe = new BooleanProperty("safe", false, () -> this.tower.getValue() == 3);
  public final IntProperty safeStuckDelayTicksProperty = new IntProperty("safe-delay-ticks", 1, 1, 3, () -> this.tower.getValue() == 3 && this.safe.getValue());
  public final ModeProperty keepY =new ModeProperty("keep-y", 0, new String[] {"NONE", "VANILLA", "EXTRA", "TELLY", "EXTRATELLY"});
  public final BooleanProperty keepYonPress = new BooleanProperty("keep-y-on-press", false, () -> this.keepY.getValue() != 0);
  public final BooleanProperty tellyRightClick = new BooleanProperty("telly-on-right-click",false,() -> this.keepY.getValue() == 3 || this.keepY.getValue() == 4);
  public final BooleanProperty disableWhileJumpActive = new BooleanProperty("no-keep-y-on-jump-potion", false, () -> this.keepY.getValue() != 0);
  public final BooleanProperty blockCounter = new BooleanProperty("block-counter", true);
  public final FloatProperty rotationSpeed = new FloatProperty("rotation-speed", 5.0F, 10.0F, 0.0F, 10.0F);
  public final FloatProperty placeDelay = new FloatProperty("place-delay", 0.0F, 0.0F, 0.0F, 5.0F);
  public final BooleanProperty multiplace = new BooleanProperty("multi-place", true);
  public final BooleanProperty safeWalk = new BooleanProperty("safe-walk", true);
  public final BooleanProperty sneak = new BooleanProperty("sneak", false);
  public final FloatProperty startSneaking = new FloatProperty("start-sneaking", 0.0F, 0.0F, 0.0F, 5.0F, () -> this.sneak.getValue());
  public final FloatProperty stopSneaking = new FloatProperty("stop-sneaking", 0.0F, 0.0F, 0.0F, 5.0F, () -> this.sneak.getValue());
  public final IntProperty sneakEvery = new IntProperty("sneak-every", 1, 1, 10, () -> this.sneak.getValue());
  public final FloatProperty sneakingSpeed = new FloatProperty("sneaking-speed", 0.2F, 0.2F, 1.0F, () -> this.sneak.getValue());
  private int sneakingTicks = -1;
  private int placements = 0;
  private int pause = 0;
  private int slow = 0;
  private float forward = 0;
  private float strafe = 0;
  private int ticksOnAir = 0;

  private boolean shouldStopSprint() {
    if (this.isBetaMode() && !this.isBetaTellyMode()) {
      return true;
    }
    if (this.isTowering()) {
      return false;
    } else {
      boolean stage =
          this.keepY.getValue() == 1 || this.keepY.getValue() == 2 || this.keepY.getValue() == 4;
      return (!stage || this.stage <= 0) && this.sprintMode.getValue() == 0;
    }
  }

  private boolean canPlace() {
    BedNuker bedNuker = (BedNuker) Miau.moduleManager.modules.get(BedNuker.class);
    if (bedNuker.isEnabled() && bedNuker.isReady()) {
      return false;
    } else {
      LongJump longJump = (LongJump) Miau.moduleManager.modules.get(LongJump.class);
      return !longJump.isEnabled() || !longJump.isAutoMode() || longJump.isJumping();
    }
  }

  private boolean isBetaMode() {
    return this.rotationMode.getValue() == ROTATION_BETA;
  }

  private boolean isBetaTellyMode() {
    return this.isBetaMode()
        && (this.keepY.getValue() == 3 || this.keepY.getValue() == 4)
        && (!this.tellyRightClick.getValue() || this.isRightClickHeld());
  }

  private boolean isRightClickHeld() {
    return mc.gameSettings != null && mc.gameSettings.keyBindUseItem.isKeyDown();
  }

  private void updateBetaState() {
    if (!this.isBetaMode() || mc.thePlayer == null) {
      this.betaAirTicks = 0;
      this.betaGroundTicks = 0;
      this.betaPlaceCooldown = 0;
      return;
    }

    if (mc.thePlayer.onGround) {
      this.betaGroundTicks++;
      this.betaAirTicks = 0;
    } else {
      this.betaAirTicks++;
      this.betaGroundTicks = 0;
    }

    if (this.betaPlaceCooldown > 0) {
      this.betaPlaceCooldown--;
    }
  }

  private void quietBetaMovement() {
    if (!this.isBetaMode() || this.isBetaTellyMode() || mc.thePlayer == null) {
      return;
    }

    mc.thePlayer.setSprinting(false);
    if (mc.gameSettings != null) {
      KeyBindUtil.setKeyBindState(mc.gameSettings.keyBindSprint.getKeyCode(), false);
    }
  }

  private boolean canBetaPlaceNow() {
    if (!this.isBetaMode()) {
      return true;
    }
    if (mc.thePlayer == null || this.placedThisTick || this.betaPlaceCooldown > 0) {
      return false;
    }
    if ((!this.isBetaTellyMode() && mc.thePlayer.isSprinting())
        || mc.thePlayer.isCollidedHorizontally
        || mc.thePlayer.hurtTime > 0) {
      return false;
    }
    if (mc.thePlayer.onGround) {
      return Math.abs(mc.thePlayer.motionY) < 1.0E-4 && this.betaGroundTicks > 0;
    }
    return this.betaAirTicks > 1;
  }

  private EnumFacing getBestFacing(BlockPos blockPos1, BlockPos blockPos3) {
    double offset = 0.0;
    EnumFacing enumFacing = null;
    for (EnumFacing facing : EnumFacing.VALUES) {
      if (facing != EnumFacing.DOWN) {
        BlockPos pos = blockPos1.offset(facing);
        if (pos.getY() <= blockPos3.getY()) {
          double distance =
              pos.distanceSqToCenter(
                  (double) blockPos3.getX() + 0.5,
                  (double) blockPos3.getY() + 0.5,
                  (double) blockPos3.getZ() + 0.5);
          if (enumFacing == null
              || distance < offset
              || distance == offset && facing == EnumFacing.UP) {
            offset = distance;
            enumFacing = facing;
          }
        }
      }
    }
    return enumFacing;
  }

  private BlockData getBlockData() {
    int startY = MathHelper.floor_double(mc.thePlayer.posY);
    BlockPos targetPos =
        new BlockPos(
            MathHelper.floor_double(mc.thePlayer.posX),
            (this.stage != 0 && !this.shouldKeepY ? Math.min(startY, this.startY) : startY) - 1,
            MathHelper.floor_double(mc.thePlayer.posZ));
    if (!BlockUtil.isReplaceable(targetPos)) {
      return null;
    } else {
      ArrayList<BlockPos> positions = new ArrayList<>();
      for (int x = -4; x <= 4; x++) {
        for (int y = -4; y <= 0; y++) {
          for (int z = -4; z <= 4; z++) {
            BlockPos pos = targetPos.add(x, y, z);
            if (!BlockUtil.isReplaceable(pos)
                && !BlockUtil.isInteractable(pos)
                && !(mc.thePlayer.getDistance(
                        (double) pos.getX() + 0.5,
                        (double) pos.getY() + 0.5,
                        (double) pos.getZ() + 0.5)
                    > (double) mc.playerController.getBlockReachDistance())
                && (this.stage == 0 || this.shouldKeepY || pos.getY() < this.startY)) {
              for (EnumFacing facing : EnumFacing.VALUES) {
                if (facing != EnumFacing.DOWN) {
                  BlockPos blockPos = pos.offset(facing);
                  if (BlockUtil.isReplaceable(blockPos)) {
                    positions.add(pos);
                  }
                }
              }
            }
          }
        }
      }
      if (positions.isEmpty()) {
        return null;
      } else {
        positions.sort(
            Comparator.comparingDouble(
                o ->
                    o.distanceSqToCenter(
                        (double) targetPos.getX() + 0.5,
                        (double) targetPos.getY() + 0.5,
                        (double) targetPos.getZ() + 0.5)));
        BlockPos blockPos = positions.get(0);
        EnumFacing facing = this.getBestFacing(blockPos, targetPos);
        return facing == null ? null : new BlockData(blockPos, facing);
      }
    }
  }

  private void place(BlockPos blockPos, EnumFacing enumFacing, Vec3 vec3) {
    if (!this.canBetaPlaceNow()) {
      return;
    }
    ItemStack activeItem = Miau.slotComponent.getItemStack();
    if (activeItem != null && ItemUtil.isBlock(activeItem) && this.blockCount > 0) {
      boolean strict = this.rayCast.getValue() == 2;
      if (strict) {
        int prevSlot = mc.thePlayer.inventory.currentItem;
        mc.thePlayer.inventory.currentItem = Miau.slotComponent.getItemIndex();
        ((IAccessorMinecraft) mc).callRightClickMouse();
        mc.thePlayer.inventory.currentItem = prevSlot;
        this.blockCount--;
        this.placedThisTick = true;
      } else if (mc.playerController.onPlayerRightClick(
          mc.thePlayer, mc.theWorld, activeItem, blockPos, enumFacing, vec3)) {
        if (mc.playerController.getCurrentGameType() != GameType.CREATIVE) {
          this.blockCount--;
        }
        this.placedThisTick = true;
        if (this.isBetaMode()) {
          this.betaPlaceCooldown = 1;
          this.betaPlaceTicks = 0;
        }
        this.placements--;
        mc.thePlayer.swingItem();
      }
    }
  }

  private MovingObjectPosition getPlacementMop(BlockData blockData, float yaw, float pitch) {
    MovingObjectPosition mop =
        RotationUtil.rayTrace(yaw, pitch, mc.playerController.getBlockReachDistance(), 1.0F);
    if (mop == null
        || mop.typeOfHit != MovingObjectType.BLOCK
        || !mop.getBlockPos().equals(blockData.blockPos())
        || mop.sideHit != blockData.facing()) {
      return null;
    }
    return mop;
  }

  private boolean isDuplicateSnapRotation(float yaw, float pitch) {
    return !Float.isNaN(this.lastSnapPlaceYaw)
        && Math.abs(MathHelper.wrapAngleTo180_float(yaw - this.lastSnapPlaceYaw)) < 0.35F;
  }

  private float[] getSnapRotation(BlockData blockData, float yaw, float pitch) {
    float baseYaw = RotationUtil.quantizeAngle(yaw);
    float basePitch = RotationUtil.quantizeAngle(MathHelper.clamp_float(pitch, -90.0F, 90.0F));

    if (!this.isDuplicateSnapRotation(baseYaw, basePitch)) {
      return new float[] {baseYaw, basePitch};
    }

    for (int i = 0; i < 24; i++) {
      float yawStep = 0.35F + 0.075F * (float) (i / 2);
      float pitchStep = 0.025F + 0.01F * (float) (i / 3);
      float testYaw = RotationUtil.quantizeAngle(baseYaw + (i % 2 == 0 ? yawStep : -yawStep));
      float testPitch =
          RotationUtil.quantizeAngle(
              MathHelper.clamp_float(
                  basePitch + (i % 4 < 2 ? pitchStep : -pitchStep), -90.0F, 90.0F));

      if (!this.isDuplicateSnapRotation(testYaw, testPitch)
          && this.getPlacementMop(blockData, testYaw, testPitch) != null) {
        return new float[] {testYaw, testPitch};
      }
    }

    return null;
  }

  private void rememberSnapRotation() {
    this.lastSnapPlaceYaw = this.yaw;
    this.lastSnapPlacePitch = this.pitch;
  }

  private EnumFacing yawToFacing(float yaw) {
    if (yaw < -135.0F || yaw > 135.0F) {
      return EnumFacing.NORTH;
    } else if (yaw < -45.0F) {
      return EnumFacing.EAST;
    } else {
      return yaw < 45.0F ? EnumFacing.SOUTH : EnumFacing.WEST;
    }
  }

  private double distanceToEdge(EnumFacing enumFacing) {
    switch (enumFacing) {
      case NORTH:
        return mc.thePlayer.posZ - Math.floor(mc.thePlayer.posZ);
      case EAST:
        return Math.ceil(mc.thePlayer.posX) - mc.thePlayer.posX;
      case SOUTH:
        return Math.ceil(mc.thePlayer.posZ) - mc.thePlayer.posZ;
      case WEST:
      default:
        return mc.thePlayer.posX - Math.floor(mc.thePlayer.posX);
    }
  }

  private void calculateSneaking() {
    if (this.ticksOnAir == 0) {
      KeyBinding.setKeyBindState(mc.gameSettings.keyBindSneak.getKeyCode(), false);
    }

    this.sneakingTicks--;

    if (!this.sneak.getValue() && this.pause <= 0) {
      return;
    }

    int ahead = (int) (float) this.startSneaking.getValue();
    int place = (int) RandomUtil.nextFloat(this.placeDelay.getValue(), this.placeDelay.getSecondValue());
    int after = (int) (float) this.stopSneaking.getValue();

    if (this.pause > 0) {
      this.pause--;
      this.sneakingTicks = 0;
      this.placements = 0;
    }

    if (this.sneakingTicks >= 0) {
      KeyBinding.setKeyBindState(mc.gameSettings.keyBindSneak.getKeyCode(), true);
      return;
    }

    if (this.ticksOnAir > 0) {
      this.sneakingTicks = after;
    }

    if (this.ticksOnAir > 0
        || PlayerUtil.blockRelativeToPlayer(
               mc.thePlayer.motionX * ahead, 1.0, mc.thePlayer.motionZ * ahead)
               instanceof BlockAir) {
      if (this.placements <= 0) {
        this.sneakingTicks = ahead + place + after;
        this.placements = this.sneakEvery.getValue();
      }
    }
  }

  private float getSpeed() {
    if (!mc.thePlayer.onGround) {
      return (float) this.airMotion.getValue() / 100.0F;
    } else {
      return MoveUtil.getSpeedLevel() > 0
          ? (float) this.speedMotion.getValue() / 100.0F
          : (float) this.groundMotion.getValue() / 100.0F;
    }
  }

  private double getRandomOffset() {
    return 0.2155 - RandomUtil.nextDouble(1.0E-4, 9.0E-4);
  }

  private float getCurrentYaw() {
    return MoveUtil.adjustYaw(
        mc.thePlayer.rotationYaw,
        (float) MoveUtil.getForwardValue(),
        (float) MoveUtil.getLeftValue());
  }

  private boolean isDiagonal(float yaw) {
    float absYaw = Math.abs(yaw % 90.0F);
    return absYaw > 20.0F && absYaw < 70.0F;
  }

  private boolean isTowering() {
    if (mc.thePlayer.onGround && MoveUtil.isForwardPressed() && !PlayerUtil.isAirAbove()) {
      boolean keepY = this.keepY.getValue() == 3 || this.keepY.getValue() == 4;
      boolean tower = this.tower.getValue() == 3;
      return keepY && this.stage > 0 || tower && mc.gameSettings.keyBindJump.isKeyDown();
    } else {
      return false;
    }
  }

  public Scaffold() {
    super("Scaffold", false);
  }

  public int getSlot() {
    return this.lastSlot;
  }

  @EventTarget(Priority.HIGH)
  public void onUpdate(UpdateEvent event) {
    if (this.isEnabled() && event.getType() == EventType.PRE) {
      this.placedThisTick = false;
      this.updateBetaState();
      this.quietBetaMovement();
      if (this.safeStuckDelayTicks > 0) {
        this.safeStuckDelayTicks--;
        if (this.safeStuckDelayTicks <= 0) {
          this.safeStuckTicks = 1;
        }
      }
      if (this.safeStuckTicks > 0) {
        if (!this.safeStuckActive) {
          this.savedMotionX = mc.thePlayer.motionX;
          this.savedMotionY = mc.thePlayer.motionY;
          this.savedMotionZ = mc.thePlayer.motionZ;
          this.safeStuckActive = true;
        }
        Miau.blinkManager.setBlinkState(true, BlinkModules.BLINK);
        mc.thePlayer.motionX = 0.0;
        mc.thePlayer.motionY = 0.0;
        mc.thePlayer.motionZ = 0.0;
      } else if (this.safeStuckActive) {
        Miau.blinkManager.setBlinkState(false, BlinkModules.BLINK);
        mc.thePlayer.motionX = this.savedMotionX;
        mc.thePlayer.motionY = this.savedMotionY;
        mc.thePlayer.motionZ = this.savedMotionZ;
        this.safeStuckActive = false;
      }
      if (this.rotationTick > 0) {
        this.rotationTick--;
      }
      if (mc.thePlayer.onGround) {
        this.ticksOnAir = 0;
      } else {
        this.ticksOnAir++;
      }
      this.calculateSneaking();
      if (hypixeltower.getValue()
          && mc.thePlayer.motionY <= 0.0
          && Math.sqrt(
                  mc.thePlayer.motionX * mc.thePlayer.motionX
                      + mc.thePlayer.motionZ * mc.thePlayer.motionZ)
              <= 0.02D
          && mc.thePlayer.motionY >= -0.09
          && !(Keyboard.isKeyDown(mc.gameSettings.keyBindForward.getKeyCode())
              || Keyboard.isKeyDown(mc.gameSettings.keyBindBack.getKeyCode())
              || Keyboard.isKeyDown(mc.gameSettings.keyBindLeft.getKeyCode())
              || Keyboard.isKeyDown(mc.gameSettings.keyBindRight.getKeyCode()))
          && Keyboard.isKeyDown(mc.gameSettings.keyBindJump.getKeyCode())) {
        mc.thePlayer.motionY = -0.38;
      }
      if (mc.thePlayer.onGround) {
        if (this.stage > 0) {
          this.stage--;
        }
        if (this.stage < 0) {
          this.stage++;
        }
        if (this.stage == 0
            && this.keepY.getValue() != 0
            && (!(Boolean) this.keepYonPress.getValue() || PlayerUtil.isUsingItem())
            && (!this.disableWhileJumpActive.getValue()
                || !mc.thePlayer.isPotionActive(Potion.jump))
            && (this.tellyRightClick.getValue() ? this.isRightClickHeld() : !mc.gameSettings.keyBindJump.isKeyDown())) {
          this.stage = 1;
        }
        this.startY = this.shouldKeepY ? this.startY : MathHelper.floor_double(mc.thePlayer.posY);
        this.shouldKeepY = false;
        this.towering = false;
      }
      if (this.canPlace()) {
        ItemStack stack = Miau.slotComponent.getItemStack();
        int count = (stack != null && stack.getItem() instanceof ItemBlock) ? stack.stackSize : 0;
        this.blockCount = Math.min(this.blockCount, count);
        if (this.blockCount <= 0) {
          int slot = Miau.slotComponent.getItemIndex();
          if (this.blockCount == 0) {
            slot--;
          }
          for (int i = slot; i > slot - 9; i--) {
            int hotbarSlot = (i % 9 + 9) % 9;
            ItemStack candidate = mc.thePlayer.inventory.getStackInSlot(hotbarSlot);
            if (candidate != null && candidate.getItem() instanceof ItemBlock) {
              Miau.slotComponent.setSlot(hotbarSlot);
              this.blockCount = candidate.stackSize;
              break;
            }
          }
        }
        float currentYaw = this.getCurrentYaw();
        float yawDiffTo180 = RotationUtil.wrapAngleDiff(currentYaw - 180.0F, event.getYaw());
        float diagonalYaw =
            this.isDiagonal(currentYaw)
                ? yawDiffTo180
                : RotationUtil.wrapAngleDiff(
                    currentYaw - 135.0F * ((currentYaw + 180.0F) % 90.0F < 45.0F ? 1.0F : -1.0F),
                    event.getYaw());
        boolean snapMode = this.rotationMode.getValue() == ROTATION_SNAP;
        boolean betaMode = this.rotationMode.getValue() == ROTATION_BETA;
        boolean betaTelly = this.isBetaTellyMode();
        this.snapRotating = false;
        if (!this.canRotate) {
          switch (this.rotationMode.getValue()) {
            case 1:
              if (this.yaw == -180.0F && this.pitch == 0.0F) {
                this.yaw = RotationUtil.quantizeAngle(diagonalYaw);
                this.pitch = RotationUtil.quantizeAngle(85.0F);
              } else {
                this.yaw = RotationUtil.quantizeAngle(diagonalYaw);
              }
              break;
            case 2:
              if (this.yaw == -180.0F && this.pitch == 0.0F) {
                this.yaw = RotationUtil.quantizeAngle(yawDiffTo180);
                this.pitch = RotationUtil.quantizeAngle(85.0F);
              } else {
                this.yaw = RotationUtil.quantizeAngle(yawDiffTo180);
              }
              break;
            case 3:
              if (this.yaw == -180.0F && this.pitch == 0.0F) {
                this.yaw = RotationUtil.quantizeAngle(diagonalYaw);
                this.pitch = RotationUtil.quantizeAngle(85.0F);
              } else {
                this.yaw = RotationUtil.quantizeAngle(diagonalYaw);
              }
              break;
            case 4: // God Bridge Mode
              // 1. SNAP YAW TO NEAREST 45-DEGREE DIAGONAL
              float roundedYaw = Math.round(currentYaw / 45.0f) * 45.0f;
              this.yaw = RotationUtil.quantizeAngle(roundedYaw);

              // 2. SET THE GODBRIDGE PITCH
              if (this.pitch == 0.0F || !this.canRotate) {
                float godBridgePitch = 79.3f;
                this.pitch = RotationUtil.quantizeAngle(godBridgePitch);
              }
              break;
            case 5:
              if (this.yaw == -180.0F && this.pitch == 0.0F) {
                this.yaw = RotationUtil.quantizeAngle(diagonalYaw);
                this.pitch = RotationUtil.quantizeAngle(85.0F);
              } else {
                float targetYaw = this.isDiagonal(currentYaw) ? diagonalYaw : yawDiffTo180;
                float yawDiff = MathHelper.wrapAngleTo180_float(targetYaw - this.yaw);
                float pitchDiff = MathHelper.wrapAngleTo180_float(85.0F - this.pitch);
                float yawTolerance =
                    this.rotationTick >= 2
                        ? RandomUtil.nextFloat(
                            tellystartrotationminspeed.getValue(),
                            tellystartrotationmaxspeed.getValue())
                        : RandomUtil.nextFloat(
                            tellynormalrotationminspeed.getValue(),
                            tellynormalrotationmaxspeed.getValue());
                float pitchTolerance =
                    this.rotationTick >= 2
                        ? RandomUtil.nextFloat(
                            tellystartrotationminspeed.getValue(),
                            tellystartrotationmaxspeed.getValue())
                        : RandomUtil.nextFloat(
                            tellynormalrotationminspeed.getValue(),
                            tellynormalrotationmaxspeed.getValue());
                this.yaw =
                    RotationUtil.quantizeAngle(
                        this.yaw + RotationUtil.clampAngle(yawDiff, yawTolerance));
                this.pitch =
                    RotationUtil.quantizeAngle(
                        this.pitch + RotationUtil.clampAngle(pitchDiff, pitchTolerance));
              }
            case ROTATION_SNAP:
              this.yaw = RotationUtil.quantizeAngle(yawDiffTo180);
              this.pitch = RotationUtil.quantizeAngle(85.0F);
              break;
            case ROTATION_BETA:
              if (this.yaw == -180.0F && this.pitch == 0.0F) {
                this.yaw = RotationUtil.quantizeAngle(event.getYaw());
                this.pitch = RotationUtil.quantizeAngle(event.getPitch());
                this.lastBetaSentYaw = event.getYaw();
                this.lastBetaSentPitch = event.getPitch();
              }
              break;
          }
        }
        // Apply rotation speed limiting to smooth rotation changes
        float rotationSpeedMin = this.rotationSpeed.getValue();
        float rotationSpeedMax = this.rotationSpeed.getSecondValue();
        if (rotationSpeedMin < 10.0F && this.rotationMode.getValue() != 0) {
          float speed = RandomUtil.nextFloat(rotationSpeedMin, rotationSpeedMax);
          float yawDiff = MathHelper.wrapAngleTo180_float(this.yaw - event.getYaw());
          float pitchDiff = MathHelper.wrapAngleTo180_float(this.pitch - event.getPitch());
          if (Math.abs(yawDiff) > speed) {
            this.yaw = RotationUtil.quantizeAngle(
                event.getYaw() + RotationUtil.clampAngle(yawDiff, speed));
          }
          if (Math.abs(pitchDiff) > speed) {
            this.pitch = RotationUtil.quantizeAngle(
                event.getPitch() + RotationUtil.clampAngle(pitchDiff, speed));
          }
        }
        BlockData blockData = this.getBlockData();

        Vec3 hitVec = null;
        if (blockData != null) {
          double[] x = placeOffsets;
          double[] y = placeOffsets;
          double[] z = placeOffsets;
          switch (blockData.facing()) {
            case NORTH:
              z = new double[] {0.0};
              break;
            case EAST:
              x = new double[] {1.0};
              break;
            case SOUTH:
              z = new double[] {1.0};
              break;
            case WEST:
              x = new double[] {0.0};
              break;
            case DOWN:
              y = new double[] {0.0};
              break;
            case UP:
              y = new double[] {1.0};
          }
          float bestYaw = -180.0F;
          float bestPitch = 0.0F;
          float bestDiff = 0.0F;
          for (double dx : x) {
            for (double dy : y) {
              for (double dz : z) {
                double relX = (double) blockData.blockPos().getX() + dx - mc.thePlayer.posX;
                double relY =
                    (double) blockData.blockPos().getY()
                        + dy
                        - mc.thePlayer.posY
                        - (double) mc.thePlayer.getEyeHeight();
                double relZ = (double) blockData.blockPos().getZ() + dz - mc.thePlayer.posZ;
                float baseYaw = RotationUtil.wrapAngleDiff(this.yaw, event.getYaw());
                float[] rotations =
                    RotationUtil.getRotationsTo(relX, relY, relZ, baseYaw, this.pitch);
                MovingObjectPosition mop =
                    RotationUtil.rayTrace(
                        rotations[0],
                        rotations[1],
                        mc.playerController.getBlockReachDistance(),
                        1.0F);
                if (mop != null
                    && mop.typeOfHit == MovingObjectType.BLOCK
                    && mop.getBlockPos().equals(blockData.blockPos())
                    && mop.sideHit == blockData.facing()) {
                  float totalDiff =
                      Math.abs(rotations[0] - baseYaw) + Math.abs(rotations[1] - this.pitch);
                  if (bestYaw == -180.0F && bestPitch == 0.0F || totalDiff < bestDiff) {
                    bestYaw = rotations[0];
                    bestPitch = rotations[1];
                    bestDiff = totalDiff;
                    hitVec = mop.hitVec;
                  }
                }
              }
            }
          }
          if (bestYaw != -180.0F || bestPitch != 0.0F) {
            this.yaw = bestYaw;
            this.pitch = bestPitch;
            this.canRotate = true;
          } else if (betaMode) {
            this.canRotate = false;
          }
        }
        boolean towerRotating = this.towering || this.isTowering();
        boolean snapAlreadyLooking = false;
        boolean snapCanPlace = true;
        if (snapMode && !towerRotating && blockData != null) {
          MovingObjectPosition currentMop =
              this.getPlacementMop(blockData, event.getYaw(), event.getPitch());
          if (currentMop != null) {
            float[] snapRotation =
                this.getSnapRotation(blockData, event.getYaw(), event.getPitch());
            if (snapRotation == null) {
              snapCanPlace = false;
              hitVec = null;
            } else {
              this.yaw = snapRotation[0];
              this.pitch = snapRotation[1];
              this.canRotate = true;
              MovingObjectPosition snapMop = this.getPlacementMop(blockData, this.yaw, this.pitch);
              hitVec = snapMop != null ? snapMop.hitVec : currentMop.hitVec;
              this.snapRotating = true;
              if (this.rotationTick > 1) {
                this.rotationTick = 1;
              }
            }
          } else if (hitVec != null && this.canRotate) {
            float[] snapRotation = this.getSnapRotation(blockData, this.yaw, this.pitch);
            if (snapRotation == null) {
              snapCanPlace = false;
              hitVec = null;
            } else {
              this.yaw = snapRotation[0];
              this.pitch = snapRotation[1];
              MovingObjectPosition snapMop = this.getPlacementMop(blockData, this.yaw, this.pitch);
              if (snapMop != null) {
                hitVec = snapMop.hitVec;
              }
              this.snapRotating = true;
              if (this.rotationTick > 1) {
                this.rotationTick = 1;
              }
            }
          }
        }
        if (this.canRotate
            && MoveUtil.isForwardPressed()
            && Math.abs(MathHelper.wrapAngleTo180_float(yawDiffTo180 - this.yaw)) < 90.0F) {
          switch (this.rotationMode.getValue()) {
            case 2:
              this.yaw = RotationUtil.quantizeAngle(yawDiffTo180);
              break;
            case 3:
              this.yaw = RotationUtil.quantizeAngle(diagonalYaw);
          }
        }
        float placeYaw = this.yaw;
        float placePitch = this.pitch;
        if (this.rotationMode.getValue() != 0
            && (!snapMode || this.snapRotating || towerRotating)) {
          float targetYaw = this.yaw;
          float targetPitch = this.pitch;
          if ((!betaMode || betaTelly)
              && this.towering
              && (mc.thePlayer.motionY > 0.0 || mc.thePlayer.posY > (double) (this.startY + 1))) {
            float yawDiff = MathHelper.wrapAngleTo180_float(this.yaw - event.getYaw());
            float tolerance =
                this.rotationTick >= 2
                    ? RandomUtil.nextFloat(
                        tellystartrotationminspeed.getValue(),
                        tellystartrotationmaxspeed.getValue())
                    : RandomUtil.nextFloat(
                        tellynormalrotationminspeed.getValue(),
                        tellynormalrotationmaxspeed.getValue());
            if (Math.abs(yawDiff) > tolerance) {
              float clampedYaw = RotationUtil.clampAngle(yawDiff, tolerance);
              targetYaw = RotationUtil.quantizeAngle(event.getYaw() + clampedYaw);
              this.rotationTick = Math.max(this.rotationTick, 1);
            }
          }
          if (towerRotating && this.isTowering()) {
            if (!betaMode || betaTelly) {
              float yawDelta =
                  MathHelper.wrapAngleTo180_float(mc.thePlayer.rotationYaw - event.getYaw());
              targetYaw =
                  RotationUtil.quantizeAngle(
                      event.getYaw() + yawDelta * RandomUtil.nextFloat(0.98F, 0.99F));
              targetPitch = RotationUtil.quantizeAngle(RandomUtil.nextFloat(30.0F, 80.0F));
            }
            this.rotationTick = 3;
            this.towering = true;
          }

          placeYaw = targetYaw;
          placePitch = targetPitch;
          // Beta anti-detection: use co-prime pitch quotients to keep GCD within valid range
          // (bypasses ScaffoldH/Rotations[3]: finalSensitivity > 200 && deltaPitch > 0.25)
          // and clamp pitch when yaw delta is large near placement
          // (bypasses ScaffoldE/Rotations[2]: deltaPitch > 8 && deltaYaw > 100)
          if (betaMode) {
            if (!Float.isNaN(this.lastBetaSentYaw)) {
              boolean clampE = this.betaPlaceTicks < 3;
              float[] corrected =
                  RotationUtil.antiDetectionRotation(
                      targetYaw,
                      targetPitch,
                      this.lastBetaSentYaw,
                      this.lastBetaSentPitch,
                      this.lastBetaPitchQuotient,
                      clampE);
              targetYaw = corrected[0];
              targetPitch = corrected[1];
              placeYaw = corrected[0];
              placePitch = corrected[1];
              // Extract pitch quotient for next tick's co-prime enforcement
              float mcpSens =
                  (float)
                      (mc.gameSettings.mouseSensitivity
                              * (1.0 + Math.random() / 10000000.0)
                              * 0.6F
                          + 0.2F);
              double m = mcpSens * mcpSens * mcpSens * 8.0F * 0.15D;
              this.lastBetaPitchQuotient =
                  Math.round((corrected[1] - this.lastBetaSentPitch) / m);
              if (this.lastBetaPitchQuotient == 0L) {
                this.lastBetaPitchQuotient =
                    corrected[1] > this.lastBetaSentPitch ? 1L : -1L;
              }
            }
            this.lastBetaSentYaw = targetYaw;
            this.lastBetaSentPitch = targetPitch;
            this.betaPlaceTicks++;
          }
          event.setRotation(targetYaw, targetPitch, 3);
          if (this.moveFix.getValue() == 1) {
            event.setPervRotation(targetYaw, 3);
          }
        }
        if (betaMode && blockData != null && hitVec != null) {
          MovingObjectPosition verifiedMop = this.getPlacementMop(blockData, placeYaw, placePitch);
          if (verifiedMop == null) {
            hitVec = null;
          } else {
            hitVec = verifiedMop.hitVec;
          }
        }
        if (blockData != null
            && hitVec != null
            && snapCanPlace
            && (this.rotationTick <= 0 || snapAlreadyLooking)
            && this.ticksOnAir >= RandomUtil.nextFloat(
                this.placeDelay.getValue(),
                this.placeDelay.getSecondValue())) {
          this.place(blockData.blockPos(), blockData.facing(), hitVec);
          if (snapMode) {
            this.rememberSnapRotation();
          }
          if (this.multiplace.getValue() && !snapMode) {
            for (int i = 0; i < 3; i++) {
              blockData = this.getBlockData();
              if (blockData == null) {
                break;
              }
              MovingObjectPosition mop =
                  RotationUtil.rayTrace(
                      this.yaw, this.pitch, mc.playerController.getBlockReachDistance(), 1.0F);
              if (mop != null
                  && mop.typeOfHit == MovingObjectType.BLOCK
                  && mop.getBlockPos().equals(blockData.blockPos())
                  && mop.sideHit == blockData.facing()) {
                this.place(blockData.blockPos(), blockData.facing(), mop.hitVec);
              } else {
                hitVec = BlockUtil.getClickVec(blockData.blockPos(), blockData.facing());
                double dx = hitVec.xCoord - mc.thePlayer.posX;
                double dy =
                    hitVec.yCoord - mc.thePlayer.posY - (double) mc.thePlayer.getEyeHeight();
                double dz = hitVec.zCoord - mc.thePlayer.posZ;
                float[] rotations =
                    RotationUtil.getRotationsTo(dx, dy, dz, event.getYaw(), event.getPitch());
                if (!(Math.abs(rotations[0] - this.yaw) < 120.0F)
                    || !(Math.abs(rotations[1] - this.pitch) < 60.0F)) {
                  break;
                }
                mop =
                    RotationUtil.rayTrace(
                        rotations[0],
                        rotations[1],
                        mc.playerController.getBlockReachDistance(),
                        1.0F);
                if (mop == null
                    || mop.typeOfHit != MovingObjectType.BLOCK
                    || !mop.getBlockPos().equals(blockData.blockPos())
                    || mop.sideHit != blockData.facing()) {
                  break;
                }
                this.place(blockData.blockPos(), blockData.facing(), mop.hitVec);
              }
            }
          }
        }
        if (this.targetFacing != null) {
          if (betaMode) {
            this.targetFacing = null;
          } else if (this.rotationTick <= 0 && !this.placedThisTick) {
            int playerBlockX = MathHelper.floor_double(mc.thePlayer.posX);
            int playerBlockY = MathHelper.floor_double(mc.thePlayer.posY);
            int playerBlockZ = MathHelper.floor_double(mc.thePlayer.posZ);
            BlockPos belowPlayer = new BlockPos(playerBlockX, playerBlockY - 1, playerBlockZ);
            hitVec = BlockUtil.getHitVec(belowPlayer, this.targetFacing, this.yaw, this.pitch);
            this.place(belowPlayer, this.targetFacing, hitVec);
          }
          this.targetFacing = null;
        } else if ((this.keepY.getValue() == 2 || this.keepY.getValue() == 4)
            && this.stage > 0
            && !mc.thePlayer.onGround) {
          int nextBlockY = MathHelper.floor_double(mc.thePlayer.posY + mc.thePlayer.motionY);
          if (nextBlockY <= this.startY && mc.thePlayer.posY > (double) (this.startY + 1)) {
            this.shouldKeepY = true;
            blockData = this.getBlockData();
            if (blockData != null && this.rotationTick <= 0 && !this.placedThisTick) {
              MovingObjectPosition mop = this.getPlacementMop(blockData, this.yaw, this.pitch);
              if (mop != null) {
                this.place(blockData.blockPos(), blockData.facing(), mop.hitVec);
              }
            }
          }
        }
      }
    }
  }

  @EventTarget
  public void onStrafe(StrafeEvent event) {
    if (this.isEnabled()) {
      if (this.safeStuckTicks > 0) {
        event.setForward(0.0F);
        event.setStrafe(0.0F);
        return;
      }
      if (this.isBetaMode() && !this.isBetaTellyMode()) {
        this.towerTick = 0;
        this.towerDelay = 0;
        if (!(this.keepY.getValue() == 3 || this.keepY.getValue() == 4)) {
          return;
        }
      }
      if (!mc.thePlayer.isCollidedHorizontally
          && mc.thePlayer.hurtTime <= 5
          && !mc.thePlayer.isPotionActive(Potion.jump)
          && mc.gameSettings.keyBindJump.isKeyDown()
          && ItemUtil.isHoldingBlock()) {
        int yState = (int) (mc.thePlayer.posY % 1.0 * 100.0);
        switch (this.tower.getValue()) {
          case 1:
            switch (this.towerTick) {
              case 0:
                if (mc.thePlayer.onGround) {
                  this.towerTick = 1;
                  mc.thePlayer.motionY = -0.0784000015258789;
                }
                return;
              case 1:
                if (yState == 0 && PlayerUtil.isAirBelow()) {
                  this.startY = MathHelper.floor_double(mc.thePlayer.posY);
                  this.towerTick = 2;
                  mc.thePlayer.motionY = 0.42F;
                  if (MoveUtil.isForwardPressed()) {
                    MoveUtil.setSpeed(MoveUtil.getSpeed(), MoveUtil.getMoveYaw());
                  } else {
                    MoveUtil.setSpeed(0.0);
                    event.setForward(0.0F);
                    event.setStrafe(0.0F);
                  }
                  return;
                } else {
                  this.towerTick = 0;
                  return;
                }
              case 2:
                this.towerTick = 3;
                mc.thePlayer.motionY = 0.75 - mc.thePlayer.posY % 1.0;
                return;
              case 3:
                this.towerTick = 1;
                mc.thePlayer.motionY = 1.0 - mc.thePlayer.posY % 1.0;
                return;
              default:
                this.towerTick = 0;
                return;
            }
          case 2:
            switch (this.towerTick) {
              case 0:
                if (mc.thePlayer.onGround) {
                  this.towerTick = 1;
                  mc.thePlayer.motionY = -0.0784000015258789;
                }
                return;
              case 1:
                if (yState == 0 && PlayerUtil.isAirBelow()) {
                  this.startY = MathHelper.floor_double(mc.thePlayer.posY);
                  if (!MoveUtil.isForwardPressed()) {
                    this.towerDelay = 2;
                    MoveUtil.setSpeed(0.0);
                    event.setForward(0.0F);
                    event.setStrafe(0.0F);
                    EnumFacing facing =
                        this.yawToFacing(MathHelper.wrapAngleTo180_float(this.yaw - 180.0F));
                    double distance = this.distanceToEdge(facing);
                    if (distance > 0.1) {
                      if (mc.thePlayer.onGround) {
                        Vec3i directionVec = facing.getDirectionVec();
                        double offset = Math.min(this.getRandomOffset(), distance - 0.05);
                        double jitter = RandomUtil.nextDouble(0.02, 0.03);
                        AxisAlignedBB nextBox =
                            mc.thePlayer
                                .getEntityBoundingBox()
                                .offset(
                                    (double) directionVec.getX() * (offset - jitter),
                                    0.0,
                                    (double) directionVec.getZ() * (offset - jitter));
                        if (mc.theWorld
                            .getCollidingBoundingBoxes(mc.thePlayer, nextBox)
                            .isEmpty()) {
                          mc.thePlayer.motionY = -0.0784000015258789;
                          mc.thePlayer.setPosition(
                              nextBox.minX + (nextBox.maxX - nextBox.minX) / 2.0,
                              nextBox.minY,
                              nextBox.minZ + (nextBox.maxZ - nextBox.minZ) / 2.0);
                        }
                        return;
                      }
                    } else {
                      this.towerTick = 2;
                      this.targetFacing = facing;
                      mc.thePlayer.motionY = 0.42F;
                    }
                    return;
                  } else {
                    this.towerTick = 2;
                    this.towerDelay++;
                    mc.thePlayer.motionY = 0.42F;
                    MoveUtil.setSpeed(MoveUtil.getSpeed(), MoveUtil.getMoveYaw());
                    return;
                  }
                } else {
                  this.towerTick = 0;
                  this.towerDelay = 0;
                  return;
                }
              case 2:
                this.towerTick = 3;
                mc.thePlayer.motionY =
                    mc.thePlayer.motionY - RandomUtil.nextDouble(0.00101, 0.00109);
                return;
              case 3:
                if (this.towerDelay >= 4) {
                  this.towerTick = 4;
                  this.towerDelay = 0;
                } else {
                  this.towerTick = 1;
                  mc.thePlayer.motionY = 1.0 - mc.thePlayer.posY % 1.0;
                }
                return;
              case 4:
                this.towerTick = 5;
                return;
              case 5:
                if (!PlayerUtil.isAirBelow()) {
                  this.towerTick = 0;
                } else {
                  this.towerTick = 1;
                  mc.thePlayer.motionY -= 0.08;
                  mc.thePlayer.motionY *= 0.98F;
                  mc.thePlayer.motionY -= 0.08;
                  mc.thePlayer.motionY *= 0.98F;
                }
                return;
              default:
                this.towerTick = 0;
                this.towerDelay = 0;
                return;
            }
          default:
            this.towerTick = 0;
            this.towerDelay = 0;
        }
      } else {
        this.towerTick = 0;
        this.towerDelay = 0;
      }
    }
  }

  @EventTarget
  public void onMoveInput(MoveInputEvent event) {
    if (this.isEnabled()) {
      if (this.safeStuckTicks > 0) {
        mc.thePlayer.movementInput.moveForward = 0.0f;
        mc.thePlayer.movementInput.moveStrafe = 0.0f;
        mc.thePlayer.movementInput.jump = false;
        mc.thePlayer.movementInput.sneak = false;
        return;
      }
      this.quietBetaMovement();
      if (this.moveFix.getValue() == 1
          && RotationState.isActived()
          && RotationState.getPriority() == 3.0F
          && MoveUtil.isForwardPressed()) {
        MoveUtil.fixStrafe(RotationState.getSmoothedYaw());
      }
      if (mc.thePlayer.onGround && this.stage > 0 && MoveUtil.isForwardPressed()) {
        mc.thePlayer.movementInput.jump = true;
      }
      this.calculateSneaking(event);
    }
  }

  private void calculateSneaking(MoveInputEvent event) {
    if (this.slow-- > 0) {
      mc.thePlayer.movementInput.moveForward = 0.0F;
      mc.thePlayer.movementInput.moveStrafe = 0.0F;
    }
  }

  @EventTarget
  public void onLivingUpdate(LivingUpdateEvent event) {
    if (this.isEnabled()) {
      if (this.safeStuckTicks > 0) {
        mc.thePlayer.motionX = 0.0;
        mc.thePlayer.motionY = 0.0;
        mc.thePlayer.motionZ = 0.0;
        this.safeStuckTicks--;
      }
      this.quietBetaMovement();
      float speed = this.isBetaMode() && !this.isBetaTellyMode() ? 1.0F : this.getSpeed();
      if (speed != 1.0F) {
        if (mc.thePlayer.movementInput.moveForward != 0.0F
            && mc.thePlayer.movementInput.moveStrafe != 0.0F) {
          mc.thePlayer.movementInput.moveForward =
              mc.thePlayer.movementInput.moveForward * (1.0F / (float) Math.sqrt(2.0));
          mc.thePlayer.movementInput.moveStrafe =
              mc.thePlayer.movementInput.moveStrafe * (1.0F / (float) Math.sqrt(2.0));
        }
        mc.thePlayer.movementInput.moveForward *= speed;
        mc.thePlayer.movementInput.moveStrafe *= speed;
      }
      if (this.shouldStopSprint()) {
        mc.thePlayer.setSprinting(false);
      }

      if (this.safe.getValue()
          && this.tower.getValue() == 3
          && mc.gameSettings.keyBindJump.isKeyDown()) {
        float moveYaw = this.getCurrentYaw();
        boolean diagonal = this.isDiagonal(moveYaw);
        if (diagonal && !mc.thePlayer.onGround) {
          double motionY = mc.thePlayer.motionY;
          if (this.safePrevMotionY > 0.0 && motionY <= 0.0) {
            double motionXZ =
                Math.sqrt(
                    mc.thePlayer.motionX * mc.thePlayer.motionX
                        + mc.thePlayer.motionZ * mc.thePlayer.motionZ);
            double motionXZSpeedBps = motionXZ * 20.0;
            if (this.safeStuckDelayTicks <= 0
                && this.safeStuckTicks <= 0
                && motionXZSpeedBps >= 4.67) {
              this.safeStuckDelayTicks = this.safeStuckDelayTicksProperty.getValue();
            }
          }
          this.safePrevMotionY = motionY;
        } else {
          this.safePrevMotionY = mc.thePlayer.motionY;
        }
      } else {
        this.safePrevMotionY = mc.thePlayer.motionY;
      }
    }
  }

  @EventTarget
  public void onSafeWalk(SafeWalkEvent event) {
    if (this.isEnabled() && this.safeWalk.getValue()) {
      if (mc.thePlayer.onGround
          && mc.thePlayer.motionY <= 0.0
          && PlayerUtil.canMove(mc.thePlayer.motionX, mc.thePlayer.motionZ, -1.0)) {
        event.setSafeWalk(true);
      }
    }
  }

  @EventTarget
  public void onRender(Render2DEvent event) {
    if (mc.thePlayer == null) return;

    long currentFrame = System.currentTimeMillis();
    float delta = (currentFrame - lastFrame) / 1000f;
    lastFrame = currentFrame;

    boolean shouldShow = this.isEnabled() && this.blockCounter.getValue();

    float target = shouldShow ? 1f : 0f;
    animationProgress += (target - animationProgress) * 12f * delta;
    animationProgress = Math.max(0f, Math.min(1f, animationProgress));

    if (animationProgress <= 0.01f) return;

    ItemStack itemStack = null;
    int count = 0;
    ItemStack held = Miau.slotComponent.getItemStack();
    if (held != null && held.getItem() instanceof ItemBlock) {
      itemStack = held;
    }

    for (int i = 0; i < 9; i++) {
      ItemStack stack = mc.thePlayer.inventory.getStackInSlot(i);
      if (stack != null && stack.stackSize > 0) {
        Item item = stack.getItem();
        if (item instanceof ItemBlock) {
          Block block = ((ItemBlock) item).getBlock();
          if (!BlockUtil.isInteractable(block) && BlockUtil.isSolid(block)) {
            count += stack.stackSize;
            if (itemStack == null) {
              itemStack = stack;
            }
          }
        }
      }
    }

    if (itemStack == null) return;

    ScaledResolution sr = new ScaledResolution(mc);
    String amount = String.valueOf(count);
    String info = "Blocks: " + amount;

    float textWidth = FontRepository.getHudFont(18).width(info);
    float width = 16f + 8f + textWidth + 8f;
    float height = 22f;
    float x = (sr.getScaledWidth() - width) / 2f;
    float y = sr.getScaledHeight() - 90f;

    GlStateManager.pushMatrix();

    float centerX = x + width / 2f;
    float centerY = y + height / 2f;
    GlStateManager.translate(centerX, centerY, 0);
    GlStateManager.scale(animationProgress, animationProgress, 1f);
    GlStateManager.translate(-centerX, -centerY, 0);

    PostProcessing postProcessing =
        (PostProcessing) Miau.moduleManager.modules.get(PostProcessing.class);
    boolean shaders =
        postProcessing != null
            && postProcessing.isEnabled()
            && (postProcessing.blur.getValue() || postProcessing.bloom.getValue());

    if (shaders) {
      if (postProcessing.bloom.getValue()) {
        GlStateManager.pushMatrix();
        GlStateManager.translate(centerX, centerY, 0);
        GlStateManager.scale(animationProgress, animationProgress, 1f);
        GlStateManager.translate(-centerX, -centerY, 0);
        RoundedUtils.drawRound(x - 1, y - 1, width + 2, height + 2, 4f, new Color(81, 99, 149, 80));
        GlStateManager.popMatrix();
      }

      if (postProcessing.blur.getValue()) {
        GlStateManager.pushMatrix();
        GlStateManager.translate(centerX, centerY, 0);
        GlStateManager.scale(animationProgress, animationProgress, 1f);
        GlStateManager.translate(-centerX, -centerY, 0);
        RoundedUtils.drawRound(x, y, width, height, 4f, new Color(0, 0, 0, 150));
        GlStateManager.popMatrix();
      }
    }

    int bgAlpha = (int) (150 * animationProgress);
    RoundedUtils.drawRound(x, y, width, height, 4f, new Color(0, 0, 0, bgAlpha));

    GlStateManager.pushMatrix();
    RenderHelper.enableGUIStandardItemLighting();
    mc.getRenderItem().renderItemAndEffectIntoGUI(itemStack, (int) x + 4, (int) y + 3);
    RenderHelper.disableStandardItemLighting();
    GlStateManager.popMatrix();

    GlStateManager.enableBlend();
    int textAlpha = (int) (255 * animationProgress);
    float fontY = y + (height / 2f) - (FontRepository.getHudFont(18).height() / 2f);
    float textX = x + 24f;

    FontRepository.getHudFont(18)
        .drawWithShadow(info, textX, fontY, new Color(255, 255, 255, textAlpha).getRGB());

    GlStateManager.popMatrix();
  }

  @EventTarget
  public void onLeftClick(LeftClickMouseEvent event) {
    if (this.isEnabled()) {
      event.setCancelled(true);
    }
  }

  @EventTarget
  public void onRightClick(RightClickMouseEvent event) {
    if (this.isEnabled()) {
      event.setCancelled(true);
    }
  }

  @EventTarget
  public void onHitBlock(HitBlockEvent event) {
    if (this.isEnabled()) {
      event.setCancelled(true);
    }
  }

  @EventTarget
  public void onSwap(SwapItemEvent event) {
    if (this.isEnabled()) {
      this.lastSlot = event.setSlot(this.lastSlot);
      event.setCancelled(true);
    }
  }

  @Override
  public void onEnabled() {
    if (mc.thePlayer != null) {
      this.lastSlot = Miau.slotComponent.getItemIndex();
    } else {
      this.lastSlot = -1;
    }
    this.blockCount = -1;
    this.rotationTick = 3;
    this.yaw = -180.0F;
    this.pitch = 0.0F;
    this.canRotate = false;
    this.towerTick = 0;
    this.towerDelay = 0;
    this.towering = false;
    this.safeStuckTicks = 0;
    this.safeStuckDelayTicks = 0;
    this.safePrevMotionY = 0.0;
    this.safeStuckActive = false;
    this.sneakingTicks = -1;
    this.placements = 0;
    this.pause = 0;
    this.slow = 0;
    this.ticksOnAir = 0;
    this.snapRotating = false;
    this.betaAirTicks = 0;
    this.betaGroundTicks = 0;
    this.betaPlaceCooldown = 0;
    this.lastSnapPlaceYaw = Float.NaN;
    this.lastSnapPlacePitch = Float.NaN;
    this.lastBetaSentYaw = Float.NaN;
    this.lastBetaSentPitch = Float.NaN;
    this.lastBetaPitchQuotient = 0L;
    this.betaPlaceTicks = 999;
  }

  @Override
  public void onDisabled() {
    if (mc.thePlayer != null && this.lastSlot != -1) {
      mc.thePlayer.inventory.currentItem = this.lastSlot;
    }
    Miau.blinkManager.setBlinkState(false, BlinkModules.BLINK);
    if (this.safeStuckActive && mc.thePlayer != null) {
      mc.thePlayer.motionX = this.savedMotionX;
      mc.thePlayer.motionY = this.savedMotionY;
      mc.thePlayer.motionZ = this.savedMotionZ;
    }
    this.safeStuckTicks = 0;
    this.safeStuckDelayTicks = 0;
    this.safePrevMotionY = 0.0;
    this.safeStuckActive = false;
    this.sneakingTicks = -1;
    this.placements = 0;
    this.pause = 0;
    this.slow = 0;
    this.ticksOnAir = 0;
    this.betaAirTicks = 0;
    this.betaGroundTicks = 0;
    this.betaPlaceCooldown = 0;
    this.lastBetaSentYaw = Float.NaN;
    this.lastBetaSentPitch = Float.NaN;
    this.lastBetaPitchQuotient = 0L;
    this.betaPlaceTicks = 999;
  }

  public int getBlockCount() {
    return this.blockCount;
  }

  public static class BlockData {
    private final BlockPos blockPos;
    private final EnumFacing facing;

    public BlockData(BlockPos blockPos, EnumFacing enumFacing) {
      this.blockPos = blockPos;
      this.facing = enumFacing;
    }

    public BlockPos blockPos() {
      return this.blockPos;
    }

    public EnumFacing facing() {
      return this.facing;
    }
  }
}
