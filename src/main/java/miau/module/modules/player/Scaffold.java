package miau.module.modules.player;

import java.awt.*;
import java.util.ArrayList;
import java.util.Comparator;
import miau.Miau;
import miau.component.BadPacketsComponent;
import miau.event.EventTarget;
import miau.event.impl.*;
import miau.event.types.EventType;
import miau.event.types.Priority;
import miau.management.RotationState;
import miau.mixin.IAccessorKeyBinding;
import miau.mixin.IAccessorMinecraft;
import miau.module.Module;
import miau.module.modules.movement.LongJump;
import miau.module.modules.render.HUD;
import miau.property.properties.*;
import miau.util.font.FontRepository;
import miau.util.math.RandomUtil;
import miau.util.network.PacketUtil;
import miau.util.player.*;
import miau.util.shader.BlurUtils;
import miau.util.shader.RoundedUtils;
import miau.util.time.TimerUtil;
import miau.util.world.BlockUtil;
import net.minecraft.block.Block;
import net.minecraft.block.BlockAir;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderGlobal;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.WorldRenderer;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.init.Blocks;
import net.minecraft.item.Item;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.network.Packet;
import net.minecraft.network.play.client.*;
import net.minecraft.potion.Potion;
import net.minecraft.util.*;
import net.minecraft.world.WorldSettings.GameType;
import org.lwjgl.input.Keyboard;
import org.lwjgl.opengl.GL11;

public class Scaffold extends Module {
  private static final Minecraft mc = Minecraft.getMinecraft();

  private static final double[] placeOffsets =
      new double[] {
        0.03125, 0.09375, 0.15625, 0.21875, 0.28125, 0.34375,
        0.40625, 0.46875, 0.53125, 0.59375, 0.65625, 0.71875,
        0.78125, 0.84375, 0.90625, 0.96875
      };

  private int rotationTick = 0;
  private int lastSlot = -1;
  private int blockCount = -1;
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
  private float animationProgress = 0f;
  private long lastFrame = System.currentTimeMillis();
  private net.minecraft.util.MovingObjectPosition lastBlockRenderRaytrace = null;
  private final java.util.Map<net.minecraft.util.BlockPos, miau.util.time.TimerUtil>
      blockRenderHighlights = new java.util.HashMap<>();

  private float targetYaw, targetPitch;
  private float yawDrift, pitchDrift;
  private int directionalChange;
  private int sneakingTicks;
  private int pause;
  private int ticksOnAir;
  private int slow;
  private float forward, strafe;
  private EnumFacingOffset enumFacing;
  private BlockPos blockFace;
  private Vec3 targetBlock;
  private int placements;
  private int recursions;
  private int offGroundTicks;
  private int onGroundTicks;

  // Watchdog Sprint state fields
  private boolean watchdogJump = false;
  private boolean watchdogJumpHandled = false;
  private int watchdogPreviousTick = -1;
  private int watchdogPreviousBlockValue = -1;
  private int watchdogHasC08Packet = 0;
  private boolean watchdogStart2 = false;
  private boolean watchdogStart3 = false;
  private boolean watchdogStart4 = false;
  private int watchdogStartTriggerCount = 0;
  private int watchdogBlock = 0;
  private int watchdogTime = 0;
  private double watchdogSpeed = 0;
  private boolean watchdogEnable = false;
  private int watchdogTicks = 0;
  private int watchdogOngroundticks = 0;

  public final ModeProperty rotationMode =
      new ModeProperty(
          "rotations",
          2,
          new String[] {
            "NONE",
            "DEFAULT",
            "BACKWARDS",
            "SIDEWAYS",
            "GRIM_TEST",
            "GODBRIDGE",
            "EAGLE",
            "BREESILY",
            "SNAP",
            "TELLY"
          });

  public final ModeProperty sprintMode =
      new ModeProperty(
          "sprint",
          0,
          new String[] {
            "NONE", "VANILLA", "LEGIT", "WATCHDOG_SLOW", "WATCHDOG_FAST", "WATCHDOG_JUMP"
          });
  public final BooleanProperty jumpSprint =
      new BooleanProperty("jump-sprint", true, () -> this.sprintMode.getValue() == 1);
  public final BooleanProperty diaSprint =
      new BooleanProperty("dia-sprint", true, () -> this.sprintMode.getValue() == 1);

  public final ModeProperty tower =
      new ModeProperty("tower", 0, new String[] {"NONE", "VANILLA", "EXTRA", "TELLY", "NORMAL"});

  public final ModeProperty keepY =
      new ModeProperty("keep-y", 0, new String[] {"NONE", "VANILLA", "EXTRA", "TELLY"});
  public final BooleanProperty keepYonPress =
      new BooleanProperty("keep-y-on-press", false, () -> this.keepY.getValue() != 0);
  public final BooleanProperty disableWhileJumpActive =
      new BooleanProperty("no-keep-y-on-jump-potion", false, () -> this.keepY.getValue() != 0);

  public final ModeProperty moveFix =
      new ModeProperty("move-fix", 1, new String[] {"NONE", "SILENT"});
  public final BooleanProperty safeWalk = new BooleanProperty("safe-walk", true);
  public final BooleanProperty multiplace = new BooleanProperty("multi-place", true);
  public final BooleanProperty blockCounter = new BooleanProperty("block-counter", true);
  public final PercentProperty groundMotion = new PercentProperty("ground-motion", 100);
  public final PercentProperty airMotion = new PercentProperty("air-motion", 100);
  public final PercentProperty speedMotion = new PercentProperty("speed-motion", 100);

  public final ModeProperty yawOffsetProp =
      new ModeProperty("yaw-offset", 0, new String[] {"0", "45", "-45"});

  public final ModeProperty rayCast =
      new ModeProperty("ray-cast", 0, new String[] {"OFF", "NORMAL", "STRICT"});
  public final BooleanProperty sneak = new BooleanProperty("sneak", false);
  public final IntProperty startSneaking =
      new IntProperty("start-sneaking", 0, 0, 5, () -> this.sneak.getValue());
  public final IntProperty stopSneaking =
      new IntProperty("stop-sneaking", 0, 0, 5, () -> this.sneak.getValue());
  public final IntProperty sneakEvery =
      new IntProperty("sneak-every", 1, 1, 10, () -> this.sneak.getValue());
  public final FloatProperty sneakingSpeed =
      new FloatProperty("sneaking-speed", 0.2F, 0.2F, 1.0F, () -> this.sneak.getValue());
  public final BooleanProperty ignoreSpeed = new BooleanProperty("ignore-speed", false);

  public final BooleanProperty swing = new BooleanProperty("swing", true);

  public final BooleanProperty blockRender = new BooleanProperty("block-render", false);
  public final ModeProperty blockRenderColorMode =
      new ModeProperty(
          "block-render-color",
          0,
          new String[] {"HUD", "CUSTOM"},
          () -> this.blockRender.getValue());
  public final ColorProperty blockRenderColor =
      new ColorProperty(
          "block-render-custom-color",
          0xFF55AAFF,
          () -> this.blockRender.getValue() && this.blockRenderColorMode.getValue() == 1);
  public final BooleanProperty blockRenderRaytrace =
      new BooleanProperty("block-render-raytrace", true, () -> this.blockRender.getValue());
  public final IntProperty blockRenderAlpha =
      new IntProperty(
          "block-render-alpha",
          200,
          0,
          255,
          () -> this.blockRender.getValue() && this.blockRenderRaytrace.getValue());
  public final BooleanProperty blockRenderOutline =
      new BooleanProperty("block-render-outline", true, () -> this.blockRender.getValue());
  public final BooleanProperty blockRenderShade =
      new BooleanProperty("block-render-shade", false, () -> this.blockRender.getValue());

  public Scaffold() {
    super("Scaffold", false);
  }

  public int getSlot() {
    return Miau.slotComponent.getItemIndex();
  }

  private boolean shouldStopSprint() {
    if (this.isTowering()) return false;
    boolean keepYActive = this.keepY.getValue() == 1 || this.keepY.getValue() == 2;
    if (keepYActive && this.stage > 0) return false;

    int sprint = this.sprintMode.getValue();
    switch (sprint) {
      case 0:
        return true;
      case 1:
        return mc.thePlayer.onGround
            ? !this.jumpSprint.getValue()
            : !(this.diaSprint.getValue() && this.isDiagonal(this.getCurrentYaw()));
      case 2:
      case 3: // WATCHDOG_SLOW
      case 4: // WATCHDOG_FAST
      case 5: // WATCHDOG_JUMP
        return false;
      default:
        return false;
    }
  }

  private boolean canPlace() {
    BedNuker bedNuker = (BedNuker) miau.Miau.moduleManager.modules.get(BedNuker.class);
    if (bedNuker.isEnabled() && bedNuker.isReady()) return false;
    LongJump longJump = (LongJump) miau.Miau.moduleManager.modules.get(LongJump.class);
    return !longJump.isEnabled() || !longJump.isAutoMode() || longJump.isJumping();
  }

  private EnumFacing getBestFacing(BlockPos blockPos1, BlockPos blockPos3) {
    double offset = 0.0;
    EnumFacing enumFacing = null;
    for (EnumFacing facing : EnumFacing.VALUES) {
      if (facing == EnumFacing.DOWN) continue;
      BlockPos pos = blockPos1.offset(facing);
      if (pos.getY() <= blockPos3.getY()) {
        double distance =
            pos.distanceSqToCenter(
                blockPos3.getX() + 0.5, blockPos3.getY() + 0.5, blockPos3.getZ() + 0.5);
        if (enumFacing == null
            || distance < offset
            || (distance == offset && facing == EnumFacing.UP)) {
          offset = distance;
          enumFacing = facing;
        }
      }
    }
    return enumFacing;
  }

  private BlockData getBlockData() {
    int sy = MathHelper.floor_double(mc.thePlayer.posY);
    BlockPos targetPos =
        new BlockPos(
            MathHelper.floor_double(mc.thePlayer.posX),
            (this.stage != 0 && !this.shouldKeepY ? Math.min(sy, this.startY) : sy) - 1,
            MathHelper.floor_double(mc.thePlayer.posZ));
    if (!BlockUtil.isReplaceable(targetPos)) return null;

    ArrayList<BlockPos> positions = new ArrayList<>();
    for (int x = -4; x <= 4; x++) {
      for (int y = -4; y <= 0; y++) {
        for (int z = -4; z <= 4; z++) {
          BlockPos pos = targetPos.add(x, y, z);
          if (!BlockUtil.isReplaceable(pos)
              && !BlockUtil.isInteractable(pos)
              && mc.thePlayer.getDistance(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5)
                  <= mc.playerController.getBlockReachDistance()
              && (this.stage == 0 || this.shouldKeepY || pos.getY() < this.startY)) {
            for (EnumFacing facing : EnumFacing.VALUES) {
              if (facing != EnumFacing.DOWN) {
                BlockPos bp = pos.offset(facing);
                if (BlockUtil.isReplaceable(bp)) {
                  positions.add(pos);
                }
              }
            }
          }
        }
      }
    }
    if (positions.isEmpty()) return null;
    positions.sort(
        Comparator.comparingDouble(
            o ->
                o.distanceSqToCenter(
                    targetPos.getX() + 0.5, targetPos.getY() + 0.5, targetPos.getZ() + 0.5)));
    BlockPos blockPos = positions.get(0);
    EnumFacing facing = this.getBestFacing(blockPos, targetPos);
    return facing == null ? null : new BlockData(blockPos, facing);
  }

  private void place(BlockPos blockPos, EnumFacing enumFacing, Vec3 vec3) {
    ItemStack activeItem = Miau.slotComponent.getItemStack();
    if (activeItem != null && ItemUtil.isBlock(activeItem) && this.blockCount > 0) {
      if (mc.playerController.onPlayerRightClick(
          mc.thePlayer, mc.theWorld, activeItem, blockPos, enumFacing, vec3)) {
        if (mc.playerController.getCurrentGameType() != GameType.CREATIVE) this.blockCount--;
        if (this.swing.getValue()) {
          mc.thePlayer.swingItem();
        } else {
          PacketUtil.sendPacket(new C0APacketAnimation());
        }
        if (this.blockRender.getValue()) {
          TimerUtil timer = new TimerUtil();
          timer.reset();
          this.blockRenderHighlights.put(blockPos.offset(enumFacing), timer);
          this.lastBlockRenderRaytrace = new MovingObjectPosition(vec3, enumFacing, blockPos);
        }
      }
    }
  }

  private EnumFacing yawToFacing(float yaw) {
    if (yaw < -135.0F || yaw > 135.0F) return EnumFacing.NORTH;
    if (yaw < -45.0F) return EnumFacing.EAST;
    return yaw < 45.0F ? EnumFacing.SOUTH : EnumFacing.WEST;
  }

  private double distanceToEdge(EnumFacing enumFacing) {
    switch (enumFacing) {
      case NORTH:
        return mc.thePlayer.posZ - Math.floor(mc.thePlayer.posZ);
      case EAST:
        return Math.ceil(mc.thePlayer.posX) - mc.thePlayer.posX;
      case SOUTH:
        return Math.ceil(mc.thePlayer.posZ) - mc.thePlayer.posZ;
      default:
        return mc.thePlayer.posX - Math.floor(mc.thePlayer.posX);
    }
  }

  private float getSpeed() {
    if (!mc.thePlayer.onGround) return (float) this.airMotion.getValue() / 100.0F;
    return MoveUtil.getSpeedLevel() > 0
        ? (float) this.speedMotion.getValue() / 100.0F
        : (float) this.groundMotion.getValue() / 100.0F;
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
    // When Watchdog modes are active, they handle their own Y-control - skip tower/keepY checks
    int sprint = this.sprintMode.getValue();
    if (sprint >= 3) return false;

    if (mc.thePlayer.onGround && MoveUtil.isForwardPressed() && !PlayerUtil.isAirAbove()) {
      boolean keepYTelly = this.keepY.getValue() == 3;
      boolean towerTelly = this.tower.getValue() == 3;
      return (keepYTelly && this.stage > 0)
          || (towerTelly && mc.gameSettings.keyBindJump.isKeyDown());
    }
    return false;
  }

  private int findBlock() {

    for (int i = 0; i < 9; i++) {
      ItemStack stack = mc.thePlayer.inventory.getStackInSlot(i);
      if (stack != null && stack.stackSize > 0 && ItemUtil.isBlock(stack)) {
        return i;
      }
    }
    return -1;
  }

  private void calculateSneaking() {
    if (ticksOnAir == 0) ((IAccessorKeyBinding) mc.gameSettings.keyBindSneak).setPressed(false);
    this.sneakingTicks--;

    if (!this.sneak.getValue() && pause <= 0) return;

    int ahead = this.startSneaking.getValue();
    int place = 0;
    int after = this.stopSneaking.getValue();

    if (pause > 0) {
      pause--;
      sneakingTicks = 0;
      placements = 0;
    }

    if (this.sneakingTicks >= 0) {
      ((IAccessorKeyBinding) mc.gameSettings.keyBindSneak).setPressed(true);
      return;
    }

    if (ticksOnAir > 0) {
      this.sneakingTicks = after;
    }

    if (ticksOnAir > 0 || PlayerUtil.isAirBelow()) {
      if (placements <= 0) {
        this.sneakingTicks = ahead + place + after;
        placements = this.sneakEvery.getValue();
      }
    }
  }

  /** Rise's getRotations - finds valid rotations by scanning at 45-degree offsets. */
  private void getRotations(final int yawOffset) {
    double difference =
        mc.thePlayer.posY
            + mc.thePlayer.getEyeHeight()
            - targetBlock.yCoord
            - 0.5
            - (Math.random() - 0.5) * 0.1;

    for (int offset = -180 + yawOffset; offset <= 180; offset += 45) {
      mc.thePlayer.setPosition(
          mc.thePlayer.posX, mc.thePlayer.posY - difference, mc.thePlayer.posZ);
      MovingObjectPosition mop =
          RayCastUtil.rayCast(mc.thePlayer.rotationYaw + (offset * 3), 0, 4.5F);
      mc.thePlayer.setPosition(
          mc.thePlayer.posX, mc.thePlayer.posY + difference, mc.thePlayer.posZ);

      if (mop == null || mop.hitVec == null) return;

      float[] rot = RotationUtil.calculate(mop.hitVec);
      MovingObjectPosition check = RayCastUtil.rayCast(rot[0], rot[1], 4.5F);
      if (check != null
          && check.getBlockPos() != null
          && check.getBlockPos().equals(blockFace)
          && check.sideHit == enumFacing.getEnumFacing()) {
        targetYaw = rot[0];
        targetPitch = rot[1];
        return;
      }
    }

    float[] backup =
        RotationUtil.calculate(new Vec3(blockFace.getX(), blockFace.getY(), blockFace.getZ()));
    MovingObjectPosition checkBackup = RayCastUtil.rayCast(targetYaw, targetPitch, 4.5F);
    if (checkBackup == null
        || checkBackup.getBlockPos() == null
        || !checkBackup.getBlockPos().equals(blockFace)) {
      targetYaw = backup[0];
      targetPitch = backup[1];
    }
  }

  private boolean doesNotContainBlock(int down) {
    return mc.theWorld
        .getBlockState(new BlockPos(mc.thePlayer.posX, mc.thePlayer.posY - down, mc.thePlayer.posZ))
        .getBlock()
        .isReplaceable(
            mc.thePlayer.worldObj,
            new BlockPos(
                mc.thePlayer.posX, Math.floor(mc.thePlayer.posY) + down, mc.thePlayer.posZ));
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

    HUD hud = (HUD) Miau.moduleManager.modules.get(HUD.class);
    boolean shaders = hud != null && hud.shaders.getValue();

    if (shaders) {

      BlurUtils.prepareBlur();
      GlStateManager.pushMatrix();
      GlStateManager.translate(centerX, centerY, 0);
      GlStateManager.scale(animationProgress, animationProgress, 1f);
      GlStateManager.translate(-centerX, -centerY, 0);
      RoundedUtils.drawRound(x, y, width, height, 4f, new Color(0, 0, 0, 150));
      GlStateManager.popMatrix();
      BlurUtils.blurEnd(2, 3);

      BlurUtils.prepareBloom();
      GlStateManager.pushMatrix();
      GlStateManager.translate(centerX, centerY, 0);
      GlStateManager.scale(animationProgress, animationProgress, 1f);
      GlStateManager.translate(-centerX, -centerY, 0);
      RoundedUtils.drawRound(x - 1, y - 1, width + 2, height + 2, 4f, new Color(81, 99, 149, 80));
      GlStateManager.popMatrix();
      BlurUtils.bloomEnd(2, 3);
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

  public void onRender3D(Render3DEvent event) {
    if (!this.isEnabled()
        || !this.blockRender.getValue()
        || mc.thePlayer == null
        || mc.theWorld == null) {
      return;
    }
    Color renderColor = this.getBlockRenderColor();
    if (this.blockRenderRaytrace.getValue()) {
      java.util.Iterator<java.util.Map.Entry<net.minecraft.util.BlockPos, TimerUtil>> iterator =
          this.blockRenderHighlights.entrySet().iterator();
      while (iterator.hasNext()) {
        java.util.Map.Entry<net.minecraft.util.BlockPos, TimerUtil> entry = iterator.next();
        long elapsed = entry.getValue().getElapsedTime();
        int alpha = 210 - (int) (210.0F * elapsed / 750.0F);
        if (alpha <= 0) {
          iterator.remove();
          continue;
        }
        this.renderScaffoldBlock(entry.getKey(), this.mergeAlpha(renderColor, alpha));
      }
      return;
    }
    MovingObjectPosition hitResult = mc.objectMouseOver;
    if (hitResult != null && hitResult.typeOfHit == MovingObjectPosition.MovingObjectType.MISS) {
      hitResult = this.lastBlockRenderRaytrace;
    } else if (hitResult != null) {
      this.lastBlockRenderRaytrace = hitResult;
    }
    if (hitResult == null) hitResult = this.lastBlockRenderRaytrace;
    if (hitResult != null && hitResult.typeOfHit == MovingObjectPosition.MovingObjectType.BLOCK) {
      this.renderScaffoldBlock(
          hitResult.getBlockPos(), this.mergeAlpha(renderColor, this.blockRenderAlpha.getValue()));
    }
  }

  private Color getBlockRenderColor() {
    if (this.blockRenderColorMode.getValue() == 0) {
      HUD hud = (HUD) Miau.moduleManager.modules.get(HUD.class);
      return hud != null ? hud.getColor(System.currentTimeMillis()) : Color.WHITE;
    }
    return new Color(this.blockRenderColor.getValue());
  }

  private int mergeAlpha(Color color, int alpha) {
    int clampedAlpha = Math.max(0, Math.min(255, alpha));
    return (clampedAlpha << 24)
        | (color.getRed() << 16)
        | (color.getGreen() << 8)
        | color.getBlue();
  }

  private void renderScaffoldBlock(BlockPos blockPos, int color) {
    if (blockPos == null) return;
    this.renderScaffoldBox(
        blockPos.getX(),
        blockPos.getY(),
        blockPos.getZ(),
        1.0D,
        1.0D,
        1.0D,
        color,
        this.blockRenderOutline.getValue(),
        this.blockRenderShade.getValue());
  }

  private void renderScaffoldBox(
      int x,
      int y,
      int z,
      double x2,
      double y2,
      double z2,
      int color,
      boolean outline,
      boolean shade) {
    double xPos = x - mc.getRenderManager().viewerPosX;
    double yPos = y - mc.getRenderManager().viewerPosY;
    double zPos = z - mc.getRenderManager().viewerPosZ;
    GL11.glPushMatrix();
    GL11.glBlendFunc(770, 771);
    GL11.glEnable(3042);
    GL11.glLineWidth(2.0F);
    GL11.glDisable(GL11.GL_TEXTURE_2D);
    GL11.glDisable(2929);
    GL11.glDepthMask(false);
    float alpha = (color >> 24 & 0xFF) / 255.0F;
    float red = (color >> 16 & 0xFF) / 255.0F;
    float green = (color >> 8 & 0xFF) / 255.0F;
    float blue = (color & 0xFF) / 255.0F;
    GL11.glColor4f(red, green, blue, alpha);
    AxisAlignedBB bb = new AxisAlignedBB(xPos, yPos, zPos, xPos + x2, yPos + y2, zPos + z2);
    if (outline) {
      RenderGlobal.drawSelectionBoundingBox(bb);
    }
    if (shade) {
      Tessellator tessellator = Tessellator.getInstance();
      WorldRenderer worldRenderer = tessellator.getWorldRenderer();
      worldRenderer.begin(7, DefaultVertexFormats.POSITION_COLOR);
      worldRenderer.pos(bb.minX, bb.maxY, bb.maxZ).color(red, green, blue, alpha).endVertex();
      worldRenderer.pos(bb.minX, bb.minY, bb.maxZ).color(red, green, blue, alpha).endVertex();
      worldRenderer.pos(bb.maxX, bb.minY, bb.maxZ).color(red, green, blue, alpha).endVertex();
      worldRenderer.pos(bb.maxX, bb.maxY, bb.maxZ).color(red, green, blue, alpha).endVertex();
      worldRenderer.pos(bb.minX, bb.maxY, bb.minZ).color(red, green, blue, alpha).endVertex();
      worldRenderer.pos(bb.maxX, bb.maxY, bb.minZ).color(red, green, blue, alpha).endVertex();
      worldRenderer.pos(bb.maxX, bb.minY, bb.minZ).color(red, green, blue, alpha).endVertex();
      worldRenderer.pos(bb.minX, bb.minY, bb.minZ).color(red, green, blue, alpha).endVertex();
      worldRenderer.pos(bb.maxX, bb.maxY, bb.maxZ).color(red, green, blue, alpha).endVertex();
      worldRenderer.pos(bb.maxX, bb.minY, bb.maxZ).color(red, green, blue, alpha).endVertex();
      worldRenderer.pos(bb.maxX, bb.minY, bb.minZ).color(red, green, blue, alpha).endVertex();
      worldRenderer.pos(bb.maxX, bb.maxY, bb.minZ).color(red, green, blue, alpha).endVertex();
      worldRenderer.pos(bb.minX, bb.maxY, bb.minZ).color(red, green, blue, alpha).endVertex();
      worldRenderer.pos(bb.minX, bb.minY, bb.minZ).color(red, green, blue, alpha).endVertex();
      worldRenderer.pos(bb.minX, bb.minY, bb.maxZ).color(red, green, blue, alpha).endVertex();
      worldRenderer.pos(bb.minX, bb.maxY, bb.maxZ).color(red, green, blue, alpha).endVertex();
      worldRenderer.pos(bb.minX, bb.maxY, bb.minZ).color(red, green, blue, alpha).endVertex();
      worldRenderer.pos(bb.minX, bb.maxY, bb.maxZ).color(red, green, blue, alpha).endVertex();
      worldRenderer.pos(bb.maxX, bb.maxY, bb.maxZ).color(red, green, blue, alpha).endVertex();
      worldRenderer.pos(bb.maxX, bb.maxY, bb.minZ).color(red, green, blue, alpha).endVertex();
      worldRenderer.pos(bb.minX, bb.minY, bb.minZ).color(red, green, blue, alpha).endVertex();
      worldRenderer.pos(bb.maxX, bb.minY, bb.minZ).color(red, green, blue, alpha).endVertex();
      worldRenderer.pos(bb.maxX, bb.minY, bb.maxZ).color(red, green, blue, alpha).endVertex();
      worldRenderer.pos(bb.minX, bb.minY, bb.maxZ).color(red, green, blue, alpha).endVertex();
      tessellator.draw();
    }
    GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
    GL11.glEnable(GL11.GL_TEXTURE_2D);
    GL11.glEnable(2929);
    GL11.glDepthMask(true);
    GL11.glDisable(3042);
    GL11.glPopMatrix();
  }

  @EventTarget(Priority.HIGH)
  public void onUpdate(UpdateEvent event) {
    if (!this.isEnabled() || event.getType() != EventType.PRE) return;

    if (this.rotationTick > 0) this.rotationTick--;

    if (mc.thePlayer.onGround) {
      this.onGroundTicks++;
      this.offGroundTicks = 0;
    } else {
      this.offGroundTicks++;
      this.onGroundTicks = 0;
    }

    if (mc.thePlayer.onGround) {
      if (this.stage > 0) this.stage--;
      if (this.stage < 0) this.stage++;
      if (this.stage == 0
          && this.keepY.getValue() != 0
          && (!this.keepYonPress.getValue() || PlayerUtil.isUsingItem())
          && (!this.disableWhileJumpActive.getValue() || !mc.thePlayer.isPotionActive(Potion.jump))
          && !mc.gameSettings.keyBindJump.isKeyDown()
          // When Watchdog modes are active, they handle their own Y-control - skip keepY stage
          && this.sprintMode.getValue() < 3) {
        this.stage = 1;
      }
      this.startY = this.shouldKeepY ? this.startY : MathHelper.floor_double(mc.thePlayer.posY);
      this.shouldKeepY = false;
      this.towering = false;
    }

    if (!this.canPlace()) return;

    int sprint = this.sprintMode.getValue();

    // Watchdog Jump - ground speed multipliers + jump handling (equivalent to Rise PreMotionEvent)
    if (sprint == 5) {
      this.recursions = 1;

      // onPreMotionEvent logic
      if (event.getType() == EventType.PRE) {
        if (mc.thePlayer.onGround) {
          watchdogOngroundticks++;
        } else {
          watchdogOngroundticks = 0;
        }

        // Speed multipliers when on ground with no jump
        if (this.onGroundTicks > 2 && !mc.gameSettings.keyBindJump.isKeyDown()) {
          MoveUtil.strafe();
          if (!mc.thePlayer.isPotionActive(Potion.moveSpeed)) {
            mc.thePlayer.motionZ *= 1.129;
            mc.thePlayer.motionX *= 1.129;
          } else if (mc.thePlayer.getActivePotionEffect(Potion.moveSpeed).getAmplifier() + 1 >= 2) {
            mc.thePlayer.motionZ *= 1.143;
            mc.thePlayer.motionX *= 1.143;
          } else {
            mc.thePlayer.motionZ *= 1.131;
            mc.thePlayer.motionX *= 1.131;
          }
        }

        // C08 ice packet spoofing
        if (watchdogStart3 && MoveUtil.isMoving() && Math.random() > 0.5) {
          java.util.Random random = new java.util.Random();
          float hitX = random.nextFloat();
          float hitZ = random.nextFloat();
          PacketUtil.sendPacket(
              new C08PacketPlayerBlockPlacement(
                  new BlockPos(mc.thePlayer.posX, mc.thePlayer.posY, mc.thePlayer.posZ),
                  EnumFacing.UP.getIndex(),
                  new ItemStack(Blocks.ice),
                  hitX,
                  1.0F,
                  hitZ));
          watchdogStart3 = false;
        }

        // Previous tick tracking
        if (watchdogPreviousTick != -1 && mc.thePlayer.ticksExisted - watchdogPreviousTick >= 4) {
          watchdogPreviousBlockValue = watchdogBlock;
        } else if (watchdogPreviousTick == -1) {
          watchdogPreviousTick = mc.thePlayer.ticksExisted;
          watchdogPreviousBlockValue = watchdogBlock;
        }

        // Ground strafe
        if (mc.thePlayer.onGround) {
          MoveUtil.strafe();
        }

        // PosY offset
        if (mc.thePlayer.onGround) {
          event.setRotation(event.getNewYaw(), event.getNewPitch(), 3);
        }

        // Jump release handling
        if (watchdogJump && !mc.gameSettings.keyBindJump.isKeyDown()) {
          MoveUtil.stop();
          watchdogJump = false;
          // Set rotations to 90 pitch for scaffold placement
          float newYaw = mc.thePlayer.rotationYaw + (float) ((Math.random() - 0.5) * 3);
          event.setRotation(newYaw, 90.0F, 3);
          if (!(PlayerUtil.block(mc.thePlayer.posX, mc.thePlayer.prevPosY, mc.thePlayer.posY)
              instanceof BlockAir)) {
            this.startY = (int) mc.thePlayer.posY - 1;
          }
        }

        if (mc.gameSettings.keyBindJump.isKeyDown()) {
          watchdogJump = true;
        }
      }
    }

    // Watchdog Fast - enable state tracking
    if (sprint == 4) {
      if (mc.thePlayer.onGround && !watchdogEnable && !mc.gameSettings.keyBindJump.isKeyDown()) {
        MoveUtil.stop();
        watchdogEnable = true;
      }
      if (mc.thePlayer.onGround) {
        event.setRotation(event.getNewYaw(), event.getNewPitch(), 3);
      }
    }

    // Watchdog Jump - PreUpdate logic: auto-jump, block finding, safe walk
    if (sprint == 5) {
      boolean start = mc.thePlayer.posY <= this.startY + 1;

      // Block finding when above startY and about to land
      if (Miau.slotComponent.getItemStack() != null
          && mc.thePlayer.posY > this.startY
          && mc.thePlayer.posY + MoveUtil.predictedMotion(mc.thePlayer.motionY, 2)
              < this.startY + 1) {
        int blockSlot2 = SlotUtil.findBlock();
        if (blockSlot2 != -1) Miau.slotComponent.setSlot(blockSlot2);
      }

      // Jump trigger when on ground
      if (!start && mc.thePlayer.onGround && !mc.gameSettings.keyBindJump.isKeyDown()) {
        MoveUtil.strafe(MoveUtil.getbaseMoveSpeed() * 0.9);
        mc.thePlayer.jump();
      }

      // Auto-jump handling (sameY equivalent)
      if (this.offGroundTicks == 1 && !mc.gameSettings.keyBindJump.isKeyDown()) {
        MoveUtil.strafe();
      }

      // Safe walk control
      // Safe walk control handled via SafeWalkEvent

      // Jump key pressed - initial jump trigger
      if (mc.gameSettings.keyBindJump.isPressed() && mc.thePlayer.onGround && watchdogStart2) {
        watchdogStart2 = false;
        MoveUtil.strafe(MoveUtil.getbaseMoveSpeed() * 0.9);
      }

      // Slow down when falling
      if (start && !mc.gameSettings.keyBindJump.isKeyDown() && !mc.thePlayer.onGround) {
        MoveUtil.stop();
      }

      // Start trigger tracking
      if (start && mc.thePlayer.onGround && !mc.gameSettings.keyBindJump.isKeyDown()) {
        watchdogStartTriggerCount++;
      }
      if (!start) {
        watchdogStartTriggerCount = 0;
      }
    }

    int blockSlot = this.findBlock();
    if (blockSlot != -1) Miau.slotComponent.setSlot(blockSlot);
    ItemStack stack = Miau.slotComponent.getItemStack();
    int count = (stack != null && stack.getItem() instanceof ItemBlock) ? stack.stackSize : 0;
    this.blockCount = Math.min(this.blockCount, count);
    if (this.blockCount <= 0) {
      int slot = Miau.slotComponent.getItemIndex();
      if (this.blockCount == 0) slot--;
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

    targetYaw = this.yaw;
    targetPitch = this.pitch;
    float snappedYaw = 0;

    int mode = this.rotationMode.getValue();

    if (mode >= 5) {

      this.canRotate = true;

      switch (mode) {
        case 5:
          {
            ItemStack held = mc.thePlayer.inventory.getCurrentItem();
            if (held != null && held.getItem() instanceof ItemBlock && ticksOnAir > 0) {
              ((IAccessorMinecraft) mc).callRightClickMouse();
            }

            targetYaw =
                (mc.thePlayer.rotationYaw - mc.thePlayer.rotationYaw % 90)
                    - 180
                    + 45 * (mc.thePlayer.rotationYaw > 0 ? 1 : -1);
            targetPitch = 76.4F;

            directionalChange++;
            if (Math.abs(MathHelper.wrapAngleTo180_double(targetYaw - (mc.thePlayer.rotationYaw)))
                > 10) {
              directionalChange = (int) (Math.random() * 4);
              yawDrift = (float) (Math.random() - 0.5) / 10f;
              pitchDrift = (float) (Math.random() - 0.5) / 10f;
            }
            if (Math.random() > 0.99) {
              yawDrift = (float) (Math.random() - 0.5) / 10f;
              pitchDrift = (float) (Math.random() - 0.5) / 10f;
            }
            targetYaw += yawDrift;
            targetPitch += pitchDrift;
            break;
          }
        case 6:
          {
            float yawWrapped = (mc.thePlayer.rotationYaw + 10000000) % 360;
            float staticYaw = (yawWrapped - 180) - (yawWrapped % 90) + 45;
            float staticPitch = 78;

            boolean straight =
                (Math.min(Math.abs(yawWrapped % 90), Math.abs(90 - yawWrapped) % 90)
                    < Math.min(
                        Math.abs(yawWrapped + 45) % 90, Math.abs(90 - (yawWrapped + 45)) % 90));

            if (straight) {
              MovingObjectPosition check90 = RayCastUtil.rayCast(staticYaw + 90, staticPitch, 3);
              MovingObjectPosition checkMain = RayCastUtil.rayCast(staticYaw, staticPitch, 3);
              if (check90 != null
                  && check90.typeOfHit == MovingObjectPosition.MovingObjectType.BLOCK
                  && (checkMain == null
                      || checkMain.typeOfHit != MovingObjectPosition.MovingObjectType.BLOCK)) {
                staticYaw += 90;
              }
            }
            if (!straight) staticYaw += 90;

            targetYaw = staticYaw + yawDrift / 2;
            targetPitch = staticPitch + pitchDrift / 2;

            ItemStack held = mc.thePlayer.inventory.getCurrentItem();
            if (Math.random() > (mc.thePlayer.onGround ? 0.5 : 0.2)
                && held != null
                && held.getItem() instanceof ItemBlock) {
              ((IAccessorMinecraft) mc).callRightClickMouse();
            }

            mc.thePlayer.movementInput.sneak = mc.theWorld != null;
            if (this.offGroundTicks >= 4 && MoveUtil.isMoving()) {
              ((IAccessorKeyBinding) mc.gameSettings.keyBindSneak).setPressed(true);
            }
            if (this.onGroundTicks == 1)
              ((IAccessorKeyBinding) mc.gameSettings.keyBindSneak).setPressed(false);
            break;
          }
        case 7:
          {
            if (enumFacing != null) {
              if (enumFacing.getEnumFacing() == EnumFacing.UP) {
                targetPitch = 90;
              } else {
                double staticYaw =
                    (Math.toDegrees(
                                Math.atan2(
                                    enumFacing.getOffset().zCoord, enumFacing.getOffset().xCoord))
                            % 360)
                        - 90;
                double staticPitch = 80;

                targetYaw = (float) staticYaw + yawDrift;
                targetPitch = (float) staticPitch + pitchDrift;
              }
            }
            if (Math.random() > 0.99 || targetPitch % 90 == 0) {
              yawDrift = (float) (Math.random() - 0.5);
              pitchDrift = (float) (Math.random() - 0.5);
            }
            break;
          }
        case 8:
          {
            if (enumFacing != null
                && blockFace != null
                && !(ticksOnAir > 0
                    && !overBlockCheck(enumFacing.getEnumFacing(), blockFace, true))) {
              snappedYaw = targetYaw;

              this.getRotations(Integer.parseInt(this.yawOffsetProp.getModeString()));

              float movementYaw =
                  (float)
                          (Math.toDegrees(
                              MoveUtil.direction(mc.thePlayer.rotationYaw, forward, strafe)))
                      - Integer.parseInt(this.yawOffsetProp.getModeString());
              targetYaw = movementYaw;
            } else {
              this.getRotations(Integer.parseInt(this.yawOffsetProp.getModeString()));
            }
            break;
          }
        case 9: // TELLY
          {
            if (recursions == 0) {
              int time = this.offGroundTicks;

              // Auto right-click at tick 0 or 2 (like Rise Telly)
              if (time == 2 || time == 0) {
                ItemStack held = mc.thePlayer.inventory.getCurrentItem();
                if (held != null && held.getItem() instanceof ItemBlock) {
                  ((IAccessorMinecraft) mc).callRightClickMouse();
                }
              }

              int yawOff = Integer.parseInt(this.yawOffsetProp.getModeString());
              int maxOffTicks = (this.keepY.getValue() != 0) ? 10 : 7;

              if (time >= 3 && this.offGroundTicks <= maxOffTicks) {
                // Check if we're over the target block
                if (enumFacing == null
                    || blockFace == null
                    || !overBlockCheck(
                        enumFacing.getEnumFacing(), blockFace, this.rayCast.getValue() == 2)) {
                  this.getRotations(yawOff);
                }
              } else {
                this.getRotations(yawOff);
                targetYaw = mc.thePlayer.rotationYaw;
              }

              if (this.offGroundTicks <= 3) {
                // Block placement disabled early in air
              }
            }
            break;
          }
      }
      this.yaw = targetYaw;
      this.pitch = targetPitch;

    } else {

      if (!this.canRotate) {
        switch (mode) {
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
          case 4:
            if (this.yaw == -180.0F && this.pitch == 0.0F) {
              this.yaw = RotationUtil.quantizeAngle(diagonalYaw);
              this.pitch = RotationUtil.quantizeAngle(85.0F);
            } else {
              this.yaw = RotationUtil.quantizeAngle(diagonalYaw);
            }
            break;
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
            break;
        }

        float bestYaw = -180.0F;
        float bestPitch = 0.0F;
        float bestDiff = 0.0F;
        for (double dx : x) {
          for (double dy : y) {
            for (double dz : z) {
              double relX = blockData.blockPos().getX() + dx - mc.thePlayer.posX;
              double relY =
                  blockData.blockPos().getY()
                      + dy
                      - mc.thePlayer.posY
                      - mc.thePlayer.getEyeHeight();
              double relZ = blockData.blockPos().getZ() + dz - mc.thePlayer.posZ;
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
                  && mop.typeOfHit == MovingObjectPosition.MovingObjectType.BLOCK
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
        }
      }

      if (this.canRotate
          && MoveUtil.isForwardPressed()
          && Math.abs(MathHelper.wrapAngleTo180_float(yawDiffTo180 - this.yaw)) < 90.0F) {
        switch (mode) {
          case 2:
            this.yaw = RotationUtil.quantizeAngle(yawDiffTo180);
            break;
          case 3:
          case 4:
            this.yaw = RotationUtil.quantizeAngle(diagonalYaw);
            break;
        }
      }

      if (mode != 0) {
        if (this.towering
            && (mc.thePlayer.motionY > 0.0 || mc.thePlayer.posY > (double) (this.startY + 1))) {
          float yawDiff = MathHelper.wrapAngleTo180_float(this.yaw - event.getYaw());
          float tolerance =
              this.rotationTick >= 2
                  ? RandomUtil.nextFloat(90.0F, 95.0F)
                  : RandomUtil.nextFloat(30.0F, 35.0F);
          if (Math.abs(yawDiff) > tolerance) {
            float clampedYaw = RotationUtil.clampAngle(yawDiff, tolerance);
            this.yaw = RotationUtil.quantizeAngle(event.getYaw() + clampedYaw);
            this.rotationTick = Math.max(this.rotationTick, 1);
          }
        }
        if (this.isTowering()) {
          float yawDelta =
              MathHelper.wrapAngleTo180_float(mc.thePlayer.rotationYaw - event.getYaw());
          this.yaw =
              RotationUtil.quantizeAngle(
                  event.getYaw() + yawDelta * RandomUtil.nextFloat(0.98F, 0.99F));
          this.pitch = RotationUtil.quantizeAngle(RandomUtil.nextFloat(30.0F, 80.0F));
          this.rotationTick = 3;
          this.towering = true;
        }
        event.setRotation(this.yaw, this.pitch, 3);
        if (this.moveFix.getValue() == 1) event.setPervRotation(this.yaw, 3);
      }

      if (blockData != null && hitVec != null && this.rotationTick <= 0) {
        this.place(blockData.blockPos(), blockData.facing(), hitVec);
        if (this.multiplace.getValue()) {
          for (int i = 0; i < 3; i++) {
            blockData = this.getBlockData();
            if (blockData == null) break;
            MovingObjectPosition mop =
                RotationUtil.rayTrace(
                    this.yaw, this.pitch, mc.playerController.getBlockReachDistance(), 1.0F);
            if (mop != null
                && mop.typeOfHit == MovingObjectPosition.MovingObjectType.BLOCK
                && mop.getBlockPos().equals(blockData.blockPos())
                && mop.sideHit == blockData.facing()) {
              this.place(blockData.blockPos(), blockData.facing(), mop.hitVec);
            } else {
              hitVec = BlockUtil.getClickVec(blockData.blockPos(), blockData.facing());
              double dx = hitVec.xCoord - mc.thePlayer.posX;
              double dy = hitVec.yCoord - mc.thePlayer.posY - mc.thePlayer.getEyeHeight();
              double dz = hitVec.zCoord - mc.thePlayer.posZ;
              float[] rotations =
                  RotationUtil.getRotationsTo(dx, dy, dz, event.getYaw(), event.getPitch());
              if (!(Math.abs(rotations[0] - this.yaw) < 120.0F)
                  || !(Math.abs(rotations[1] - this.pitch) < 60.0F)) break;
              mop =
                  RotationUtil.rayTrace(
                      rotations[0],
                      rotations[1],
                      mc.playerController.getBlockReachDistance(),
                      1.0F);
              if (mop == null
                  || mop.typeOfHit != MovingObjectPosition.MovingObjectType.BLOCK
                  || !mop.getBlockPos().equals(blockData.blockPos())
                  || mop.sideHit != blockData.facing()) break;
              this.place(blockData.blockPos(), blockData.facing(), mop.hitVec);
            }
          }
        }
      }

      if (this.targetFacing != null) {
        if (this.rotationTick <= 0) {
          int pX = MathHelper.floor_double(mc.thePlayer.posX);
          int pY = MathHelper.floor_double(mc.thePlayer.posY);
          int pZ = MathHelper.floor_double(mc.thePlayer.posZ);
          BlockPos below = new BlockPos(pX, pY - 1, pZ);
          Vec3 hv = BlockUtil.getHitVec(below, this.targetFacing, this.yaw, this.pitch);
          this.place(below, this.targetFacing, hv);
        }
        this.targetFacing = null;
      } else if (this.keepY.getValue() == 2 && this.stage > 0 && !mc.thePlayer.onGround) {
        int nextY = MathHelper.floor_double(mc.thePlayer.posY + mc.thePlayer.motionY);
        if (nextY <= this.startY && mc.thePlayer.posY > (double) (this.startY + 1)) {
          this.shouldKeepY = true;
          blockData = this.getBlockData();
          if (blockData != null && this.rotationTick <= 0) {
            hitVec =
                BlockUtil.getHitVec(blockData.blockPos(), blockData.facing(), this.yaw, this.pitch);
            this.place(blockData.blockPos(), blockData.facing(), hitVec);
          }
        }
      }
    }

    if (mode >= 5) {

      event.setRotation(this.yaw, this.pitch, 3);
      if (this.moveFix.getValue() == 1) event.setPervRotation(this.yaw, 3);

      BlockData bd = this.getBlockData();

      if (bd != null) {
        this.targetBlock =
            new Vec3(bd.blockPos().getX(), bd.blockPos().getY(), bd.blockPos().getZ());
        this.enumFacing =
            new EnumFacingOffset(
                bd.facing(),
                new Vec3(
                    bd.facing().getDirectionVec().getX(),
                    bd.facing().getDirectionVec().getY(),
                    bd.facing().getDirectionVec().getZ()));
        this.blockFace =
            new BlockPos(bd.blockPos())
                .add(
                    bd.facing().getDirectionVec().getX(),
                    bd.facing().getDirectionVec().getY(),
                    bd.facing().getDirectionVec().getZ());

        boolean badPackets = BadPacketsComponent.bad(false, true, false, false, true);

        if (!mc.gameSettings.keyBindJump.isKeyDown() || MoveUtil.isMoving()) {
          if (doesNotContainBlock(1)) {
            ticksOnAir++;
          } else {
            ticksOnAir = 0;
          }
        }

        boolean canPlaceNow = !badPackets && ticksOnAir > 0;

        if (canPlaceNow
            && (rayCast.getValue() == 0
                || overBlockCheck(bd.facing(), blockFace, rayCast.getValue() == 2))) {
          this.place(bd.blockPos(), bd.facing(), BlockUtil.getClickVec(bd.blockPos(), bd.facing()));
          ticksOnAir = 0;

          ItemStack item = Miau.slotComponent.getItemStack();
          if (item != null && item.stackSize == 0) {
            mc.thePlayer.inventory.mainInventory[Miau.slotComponent.getItemIndex()] = null;
          }
        } else if (Math.random() > 0.3
            && mc.objectMouseOver != null
            && mc.objectMouseOver.typeOfHit != null
            && mc.objectMouseOver.getBlockPos() != null
            && mc.objectMouseOver.getBlockPos().equals(blockFace)
            && blockFace != null
            && mc.objectMouseOver.sideHit == EnumFacing.UP
            && rayCast.getValue() == 2
            && !(mc.theWorld
                    .getBlockState(
                        new BlockPos(mc.thePlayer.posX, mc.thePlayer.posY - 1, mc.thePlayer.posZ))
                    .getBlock()
                instanceof BlockAir)) {
          ((IAccessorMinecraft) mc).callRightClickMouse();
        }

        if (mc.gameSettings.keyBindJump.isKeyDown() && mc.thePlayer.posY % 1 > 0.5) {
          startY = MathHelper.floor_double(mc.thePlayer.posY);
        }
        if ((mc.thePlayer.posY < startY || mc.thePlayer.onGround) && !MoveUtil.isMoving()) {
          startY = MathHelper.floor_double(mc.thePlayer.posY);
        }
      }
    }
  }

  /** Raycast check - returns true if the current rotation can see the target block face. */
  private boolean overBlockCheck(EnumFacing enumFacing, BlockPos pos, boolean strict) {
    MovingObjectPosition mop = RayCastUtil.rayCast(this.yaw, this.pitch, 4.5F);
    if (mop == null || mop.hitVec == null || mop.getBlockPos() == null) return false;
    return mop.getBlockPos().equals(pos) && (!strict || mop.sideHit == enumFacing);
  }

  @EventTarget
  public void onStrafe(StrafeEvent event) {
    if (!this.isEnabled()) return;

    int sprint = this.sprintMode.getValue();

    // Watchdog Slow - speed limiting
    if (sprint == 3) {
      ((IAccessorKeyBinding) mc.gameSettings.keyBindSprint).setPressed(true);
      mc.thePlayer.setSprinting(true);
      double limit = mc.thePlayer.isPotionActive(Potion.moveSpeed) ? 0.118 : 0.083;
      if (mc.thePlayer.onGround) MoveUtil.strafe(limit - (Math.random() * 0.0001));
      if (MoveUtil.speed() >= limit && !mc.gameSettings.keyBindJump.isKeyDown()) {
        MoveUtil.moveFlying((MoveUtil.speed() - limit) * -1);
      }
      return;
    }

    // Watchdog Fast - jump motion on enable
    if (sprint == 4) {
      if (mc.thePlayer.onGround
          && watchdogHasC08Packet > 0
          && !mc.gameSettings.keyBindJump.isKeyDown()) {
        mc.thePlayer.motionY = 0.42;
        watchdogHasC08Packet = 0;
      }
    }

    if (!this.yawOffsetProp.getModeString().equals("0") && this.moveFix.getValue() == 0) {}

    if (!mc.thePlayer.isCollidedHorizontally
        && mc.thePlayer.hurtTime <= 5
        && !mc.thePlayer.isPotionActive(Potion.jump)
        && mc.gameSettings.keyBindJump.isKeyDown()
        && Miau.slotComponent.isHoldingBlock()) {
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
                      Vec3i dirVec = facing.getDirectionVec();
                      double offset = Math.min(this.getRandomOffset(), distance - 0.05);
                      double jitter = RandomUtil.nextDouble(0.02, 0.03);
                      AxisAlignedBB nextBox =
                          mc.thePlayer
                              .getEntityBoundingBox()
                              .offset(
                                  dirVec.getX() * (offset - jitter),
                                  0.0,
                                  dirVec.getZ() * (offset - jitter));
                      if (mc.theWorld.getCollidingBoundingBoxes(mc.thePlayer, nextBox).isEmpty()) {
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
              mc.thePlayer.motionY -= RandomUtil.nextDouble(0.00101, 0.00109);
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
              if (!PlayerUtil.isAirBelow()) this.towerTick = 0;
              else {
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
        case 4:
          if (mc.thePlayer.onGround) {
            mc.thePlayer.motionY = 0.42F;
          }
          return;
        default:
          this.towerTick = 0;
          this.towerDelay = 0;
      }
    } else {
      this.towerTick = 0;
      this.towerDelay = 0;
    }
  }

  @EventTarget
  public void onMoveInput(MoveInputEvent event) {
    if (!this.isEnabled()) return;

    if (this.moveFix.getValue() == 1
        && RotationState.isActived()
        && RotationState.getPriority() == 3.0F
        && MoveUtil.isForwardPressed()) {
      MoveUtil.fixStrafe(RotationState.getSmoothedYaw());
    }

    // Telly rotation mode auto-jump (equivalent to Rise's runMode)
    if (this.rotationMode.getValue() == 9 && mc.thePlayer.onGround && MoveUtil.isMoving()) {
      mc.thePlayer.movementInput.jump = true;
    }

    if (mc.thePlayer.onGround && this.stage > 0 && MoveUtil.isForwardPressed()) {
      mc.thePlayer.movementInput.jump = true;
    }

    if (this.sneak.getValue()) {
      float speed = this.sneakingSpeed.getValue();
      if (speed > 0.2F && mc.thePlayer.movementInput.sneak) {
        mc.thePlayer.movementInput.moveForward *= 0.3F / 0.2F * speed;
        mc.thePlayer.movementInput.moveStrafe *= 0.3F / 0.2F * speed;
      }
    }
  }

  @EventTarget
  public void onLivingUpdate(LivingUpdateEvent event) {
    if (!this.isEnabled()) return;

    int sprint = this.sprintMode.getValue();

    float speed = this.getSpeed();
    if (speed != 1.0F) {
      if (mc.thePlayer.movementInput.moveForward != 0.0F
          && mc.thePlayer.movementInput.moveStrafe != 0.0F) {
        mc.thePlayer.movementInput.moveForward *= (1.0F / (float) Math.sqrt(2.0));
        mc.thePlayer.movementInput.moveStrafe *= (1.0F / (float) Math.sqrt(2.0));
      }
      mc.thePlayer.movementInput.moveForward *= speed;
      mc.thePlayer.movementInput.moveStrafe *= speed;
    }

    if (this.shouldStopSprint()) {
      mc.thePlayer.setSprinting(false);
    }

    // Watchdog Slow - force sprint always
    if (sprint == 3) {
      ((IAccessorKeyBinding) mc.gameSettings.keyBindSprint).setPressed(true);
      mc.thePlayer.setSprinting(true);
    }

    // Watchdog Fast - diagonal prevention
    if (sprint == 4 && mc.thePlayer.onGround && !mc.gameSettings.keyBindJump.isKeyDown()) {
      ((IAccessorMinecraft) mc).getTimer().timerSpeed = 1.0029f;
      MoveUtil.preventDiagonalSpeed();
      mc.thePlayer.motionZ *= .998;
      mc.thePlayer.motionX *= .998;
    }

    if (slow > 0) {
      slow--;
      mc.thePlayer.movementInput.moveForward = 0;
      mc.thePlayer.movementInput.moveStrafe = 0;
    }

    this.calculateSneaking();
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
  public void onLeftClick(LeftClickMouseEvent event) {
    if (this.isEnabled()) event.setCancelled(true);
  }

  @EventTarget
  public void onRightClick(RightClickMouseEvent event) {
    if (this.isEnabled()) event.setCancelled(true);
  }

  @EventTarget
  public void onHitBlock(HitBlockEvent event) {
    if (this.isEnabled()) event.setCancelled(true);
  }

  @EventTarget
  public void onSwap(SwapItemEvent event) {
    if (this.isEnabled()) {
      this.lastSlot = event.setSlot(this.lastSlot);
      event.setCancelled(true);
    }
  }

  @EventTarget
  public void onPacketSend(PacketEvent event) {
    if (event.getType() != EventType.SEND) return;
    Packet<?> packet = event.getPacket();

    if (packet instanceof C08PacketPlayerBlockPlacement) {
      C08PacketPlayerBlockPlacement p = (C08PacketPlayerBlockPlacement) packet;
      if (!p.getPosition().equals(new BlockPos(-1, -1, -1))) {
        placements--;
      }
      // Watchdog Jump: track C08 for ice packet assist
      if (this.sprintMode.getValue() == 5) {
        watchdogBlock++;
        watchdogStart3 = true;
      }
      // Watchdog Fast: track C08 for jump motion trigger
      if (this.sprintMode.getValue() == 4) {
        watchdogHasC08Packet++;
      }
    }
  }

  @Override
  public void onEnabled() {
    if (mc.thePlayer == null) return;

    this.lastSlot = Miau.slotComponent.getItemIndex();
    this.blockCount = -1;
    this.rotationTick = 3;
    this.yaw = -180.0F;
    this.pitch = 0.0F;
    this.canRotate = false;
    this.towerTick = 0;
    this.towerDelay = 0;
    this.towering = false;

    this.targetYaw = mc.thePlayer.rotationYaw - 180;
    this.targetPitch = 90;
    this.pitchDrift = (float) ((Math.random() - 0.5) * (Math.random() - 0.5) * 10);
    this.yawDrift = (float) ((Math.random() - 0.5) * (Math.random() - 0.5) * 10);
    this.startY = MathHelper.floor_double(mc.thePlayer.posY);
    this.sneakingTicks = -1;
    this.ticksOnAir = 0;
    this.directionalChange = 0;
    this.pause = 0;
    this.slow = 0;
    this.placements = 0;
    this.recursions = 0;
    this.offGroundTicks = 0;
    this.onGroundTicks = 0;

    // Watchdog mode reset
    this.watchdogJump = false;
    this.watchdogJumpHandled = false;
    this.watchdogPreviousTick = -1;
    this.watchdogPreviousBlockValue = -1;
    this.watchdogHasC08Packet = 0;
    this.watchdogStart2 = true;
    this.watchdogStart3 = false;
    this.watchdogStart4 = false;
    this.watchdogStartTriggerCount = 0;
    this.watchdogBlock = 0;
    this.watchdogTime = 0;
    this.watchdogSpeed = 0;
    this.watchdogEnable = true;
    this.watchdogTicks = 0;
    this.watchdogOngroundticks = 0;

    BadPacketsComponent.reset();
  }

  @Override
  public void onDisabled() {
    if (mc.thePlayer != null && this.lastSlot != -1) {
      mc.thePlayer.inventory.currentItem = this.lastSlot;
    }

    ((IAccessorKeyBinding) mc.gameSettings.keyBindSneak)
        .setPressed(Keyboard.isKeyDown(mc.gameSettings.keyBindSneak.getKeyCode()));

    // Watchdog mode cleanup
    ((IAccessorMinecraft) mc).getTimer().timerSpeed = 1.0F;
    mc.thePlayer.setSprinting(false);
    // mc.thePlayer.safeWalk = false;

    // Reset Watchdog Jump state
    if (this.sprintMode.getValue() == 5) {
      watchdogStart3 = false;
      watchdogStart2 = false;
      watchdogHasC08Packet = 0;
      if (watchdogJump) {
        MoveUtil.stop();
      }
    }
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
