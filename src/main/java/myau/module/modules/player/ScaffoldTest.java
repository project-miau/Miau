package myau.module.modules.player;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import myau.event.EventTarget;
import myau.event.impl.*;
import myau.event.types.EventType;
import myau.mixin.IAccessorEntityLivingBase;
import myau.mixin.IAccessorKeyBinding;
import myau.mixin.IAccessorMinecraft;
import myau.module.Module;
import myau.property.properties.*;
import myau.util.math.FastNoiseLite;
import myau.util.network.PacketUtil;
import myau.util.player.*;
import myau.util.time.TimerUtil;
import net.minecraft.block.Block;
import net.minecraft.block.BlockAir;
import net.minecraft.block.BlockCarpet;
import net.minecraft.block.BlockChest;
import net.minecraft.block.BlockContainer;
import net.minecraft.block.BlockFurnace;
import net.minecraft.block.BlockLadder;
import net.minecraft.block.BlockLiquid;
import net.minecraft.block.BlockSkull;
import net.minecraft.block.BlockSnow;
import net.minecraft.block.material.Material;
import net.minecraft.client.Minecraft;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.network.play.client.C09PacketHeldItemChange;
import net.minecraft.network.play.client.C0APacketAnimation;
import net.minecraft.network.play.client.C0BPacketEntityAction;
import net.minecraft.potion.Potion;
import net.minecraft.util.BlockPos;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.MathHelper;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.Vec3;
import org.lwjgl.input.Keyboard;

public class ScaffoldTest extends Module {

  public static final Minecraft mc = Minecraft.getMinecraft();

  // === Field declarations ===
  public int posY;
  public int spoofSlot;
  public int itemBefore;
  public int enabledTicks;
  public int rotationTicks;
  public int dragClickTicks;
  public int sneakTicks;
  public int timerTicks;
  public int placed;
  public int adStrafeBlocks;
  public int yadStrafeBlocks;
  public int intaveBypassBlocks;
  public int offGroundTicks;
  public boolean wasJump;
  public boolean adStrafeDirection;
  public boolean silentSneak;
  public float lastYaw;
  public float lastPitch;
  public float scaffoldYaw;
  public float scaffoldPitch;
  public float startYaw;
  public float polarTicks;
  public FastNoiseLite noise = new FastNoiseLite();
  public BlockPos blockPos;
  public EnumFacing facing;
  public TimerUtil adStrafeTimer = new TimerUtil();
  public TimerUtil jumpBlockPlacementTimer = new TimerUtil();
  public TimerUtil offGroundSpeedTimer = new TimerUtil();
  public TimerUtil intaveBypassTimer = new TimerUtil();

  // === Settings (Gothaj -> Miau Property mapping) ===
  public ModeProperty rotations =
      new ModeProperty(
          "Rotations",
          0,
          new String[] {
            "Static god", "Polar", "Intave", "Hypixel", "Keep", "Snap", "Telly", "Direct", "None"
          });
  public BooleanProperty polarFull =
      new BooleanProperty(
          "Polar full", false, () -> this.rotations.getModeString().equalsIgnoreCase("Polar"));
  public BooleanProperty polarStrong =
      new BooleanProperty(
          "Polar strong", false, () -> this.rotations.getModeString().equalsIgnoreCase("Polar"));

  public ModeProperty sprint =
      new ModeProperty(
          "Sprint",
          0,
          new String[] {
            "Allways",
            "Off",
            "Legit",
            "Switch",
            "Motion modifier",
            "No packet",
            "Packet legit",
            "Old intave"
          });

  public ModeProperty motionModifierSprint =
      new ModeProperty(
          "Motion modifier sprint",
          0,
          new String[] {"Air", "Ground", "Both", "Off"},
          () -> this.sprint.getModeString().equalsIgnoreCase("Motion modifier"));

  public ModeProperty motionModifierSprintGround =
      new ModeProperty(
          "Sprint ground mode",
          0,
          new String[] {
            "On place", "Tick", "On jump", "Place and jump", "Tick and jump", "Allways"
          },
          () ->
              this.sprint.getModeString().equalsIgnoreCase("Motion modifier")
                  && (this.motionModifierSprint.getModeString().equalsIgnoreCase("Ground")
                      || this.motionModifierSprint.getModeString().equalsIgnoreCase("Both")));

  public ModeProperty motionModifierSprintAir =
      new ModeProperty(
          "Sprint air mode",
          0,
          new String[] {"On place", "Tick", "Allways"},
          () ->
              this.sprint.getModeString().equalsIgnoreCase("Motion modifier")
                  && (this.motionModifierSprint.getModeString().equalsIgnoreCase("Air")
                      || this.motionModifierSprint.getModeString().equalsIgnoreCase("Both")));

  public ModeProperty sprintPacket =
      new ModeProperty(
          "Sprint packet",
          0,
          new String[] {"Air", "Ground", "Both", "Off"},
          () ->
              this.sprint.getModeString().equalsIgnoreCase("Motion modifier")
                  && !this.motionModifierSprint.getModeString().equalsIgnoreCase("Off"));

  public ModeProperty motionModifierMode =
      new ModeProperty(
          "Motion modifier mode",
          0,
          new String[] {"Air", "Ground", "Both"},
          () -> this.sprint.getModeString().equalsIgnoreCase("Motion modifier"));

  public ModeProperty motionModifierGround =
      new ModeProperty(
          "Ground mode",
          0,
          new String[] {"On place", "Tick", "On jump", "Place and jump", "Tick and jump"},
          () ->
              this.sprint.getModeString().equalsIgnoreCase("Motion modifier")
                  && (this.motionModifierMode.getModeString().equalsIgnoreCase("Ground")
                      || this.motionModifierMode.getModeString().equalsIgnoreCase("Both")));

  public ModeProperty motionModifierAir =
      new ModeProperty(
          "Air mode",
          0,
          new String[] {"On place", "Tick"},
          () ->
              this.sprint.getModeString().equalsIgnoreCase("Motion modifier")
                  && (this.motionModifierMode.getModeString().equalsIgnoreCase("Air")
                      || this.motionModifierMode.getModeString().equalsIgnoreCase("Both")));

  public FloatProperty motionModifierTick =
      new FloatProperty(
          "Tick delay",
          3.0f,
          1.0f,
          40.0f,
          () ->
              this.sprint.getModeString().equalsIgnoreCase("Motion modifier")
                  && (this.motionModifierAir.getModeString().equalsIgnoreCase("Tick")
                      || this.motionModifierGround.getModeString().equalsIgnoreCase("Tick")
                      || this.motionModifierGround
                          .getModeString()
                          .equalsIgnoreCase("Tick and jump")));

  public FloatProperty groundMotion =
      new FloatProperty(
          "Ground motion",
          1.0f,
          0.0f,
          3.0f,
          () ->
              this.sprint.getModeString().equalsIgnoreCase("Motion modifier")
                  && (this.motionModifierMode.getModeString().equalsIgnoreCase("Ground")
                      || this.motionModifierMode.getModeString().equalsIgnoreCase("Both")));

  public FloatProperty airMotion =
      new FloatProperty(
          "Air motion",
          1.0f,
          0.0f,
          3.0f,
          () ->
              this.sprint.getModeString().equalsIgnoreCase("Motion modifier")
                  && (this.motionModifierMode.getModeString().equalsIgnoreCase("Air")
                      || this.motionModifierMode.getModeString().equalsIgnoreCase("Both")));

  public ModeProperty tower =
      new ModeProperty(
          "Tower", 0, new String[] {"None", "Vanilla", "NCP", "Hypixel", "Timer", "Intave"});
  public ModeProperty spoof =
      new ModeProperty("Spoof slot", 0, new String[] {"None", "Normal", "Fake"});
  public BooleanProperty sprintWhenJump =
      new BooleanProperty(
          "Sprint when jump",
          true,
          () -> !this.sprint.getModeString().equalsIgnoreCase("Motion modifier"));
  public FloatProperty expand = new FloatProperty("Expand", 0.0f, 0.0f, 8.0f);
  public BooleanProperty timer = new BooleanProperty("Timer", false);
  public FloatProperty timerSpeed =
      new FloatProperty("Timer speed", 1.0f, 0.0f, 2.0f, () -> this.timer.getValue());
  public FloatProperty timerDelay =
      new FloatProperty("Timer delay", 4.0f, 0.0f, 20.0f, () -> this.timer.getValue());
  public FloatProperty timerTime =
      new FloatProperty("Timer time", 2.0f, 1.0f, 40.0f, () -> this.timer.getValue());
  public ModeProperty sneak =
      new ModeProperty("Sneak", 0, new String[] {"Off", "Normal", "Silent"});
  public FloatProperty sneakDelay =
      new FloatProperty(
          "Sneak delay",
          4.0f,
          0.0f,
          20.0f,
          () -> !this.sneak.getModeString().equalsIgnoreCase("Off"));
  public FloatProperty sneakTime =
      new FloatProperty(
          "Sneak time",
          2.0f,
          1.0f,
          8.0f,
          () ->
              !this.sneak.getModeString().equalsIgnoreCase("Off")
                  && !this.sneak.getModeString().equalsIgnoreCase("Silent"));
  public BooleanProperty safeWalk = new BooleanProperty("Safe walk", true);
  public BooleanProperty moveFix = new BooleanProperty("Move fix", true);
  public BooleanProperty swing = new BooleanProperty("Swing", false);
  public BooleanProperty serverSideSwing = new BooleanProperty("Server side swing", true);
  public BooleanProperty jump = new BooleanProperty("Jump", false);
  public BooleanProperty polarJump = new BooleanProperty("Polar jump", false);
  public BooleanProperty hypixelJump = new BooleanProperty("Hypixel jump", false);
  public BooleanProperty godbridgeJump = new BooleanProperty("Godbridge jump", false);
  public BooleanProperty intaveCloudBypass = new BooleanProperty("Intave cloud bypass", false);
  public BooleanProperty switchBack = new BooleanProperty("Switch back", true);
  public BooleanProperty adStrafe = new BooleanProperty("A D strafe", false);
  public FloatProperty adStrafeDelay =
      new FloatProperty("A D strafe delay", 0.0f, 0.0f, 10.0f, () -> this.adStrafe.getValue());
  public BooleanProperty dragClick = new BooleanProperty("Drag click", false);

  // === Inventory check blocks (same as Gothaj) ===
  private static final List<Block> invalidBlocks =
      new ArrayList<Block>() {
        {
          add(Blocks.enchanting_table);
          add(Blocks.carpet);
          add(Blocks.glass_pane);
          add(Blocks.ladder);
          add(Blocks.web);
          add(Blocks.stained_glass_pane);
          add(Blocks.iron_bars);
          add(Blocks.air);
          add(Blocks.water);
          add(Blocks.flowing_water);
          add(Blocks.lava);
          add(Blocks.ladder);
          add(Blocks.soul_sand);
          add(Blocks.ice);
          add(Blocks.packed_ice);
          add(Blocks.sand);
          add(Blocks.flowing_lava);
          add(Blocks.snow_layer);
          add(Blocks.chest);
          add(Blocks.ender_chest);
          add(Blocks.torch);
          add(Blocks.anvil);
          add(Blocks.trapped_chest);
          add(Blocks.noteblock);
          add(Blocks.jukebox);
          add(Blocks.wooden_pressure_plate);
          add(Blocks.stone_pressure_plate);
          add(Blocks.light_weighted_pressure_plate);
          add(Blocks.heavy_weighted_pressure_plate);
          add(Blocks.stone_button);
          add(Blocks.tnt);
          add(Blocks.wooden_button);
          add(Blocks.lever);
          add(Blocks.crafting_table);
          add(Blocks.furnace);
          add(Blocks.stone_slab);
          add(Blocks.wooden_slab);
          add(Blocks.stone_slab2);
          add(Blocks.brown_mushroom);
          add(Blocks.red_mushroom);
          add(Blocks.gold_block);
          add(Blocks.red_flower);
          add(Blocks.yellow_flower);
          add(Blocks.flower_pot);
        }
      };

  public ScaffoldTest() {
    super("ScaffoldTest", false);
    this.noise.SetNoiseType(FastNoiseLite.NoiseType.OpenSimplex2);
  }

  @Override
  public void onDisabled() {
    ((IAccessorMinecraft) this.mc).setRightClickDelayTimer(6);
    if (this.switchBack.getValue() && this.spoof.getModeString().equalsIgnoreCase("None")) {
      this.mc.thePlayer.inventory.currentItem = this.itemBefore;
    }

    if (this.spoof.getModeString().equalsIgnoreCase("Fake")
        && this.mc.thePlayer.inventory.currentItem != this.spoofSlot) {
      PacketUtil.sendPacketNoEvent(
          new C09PacketHeldItemChange(this.mc.thePlayer.inventory.currentItem));
    }

    // No smooth rotation module check needed - just reset
    RotationUtil.customRots = false;
    RotationUtil.serverYaw = this.mc.thePlayer.rotationYaw;
    RotationUtil.serverPitch = this.mc.thePlayer.rotationPitch;

    ((IAccessorKeyBinding) this.mc.gameSettings.keyBindSneak)
        .setPressed(Keyboard.isKeyDown(this.mc.gameSettings.keyBindSneak.getKeyCode()));
    ((IAccessorMinecraft) this.mc).getTimer().timerSpeed = 1.0F;
    this.enabledTicks = 11;
  }

  @Override
  public void onEnabled() {
    if (this.sprint.getModeString().equalsIgnoreCase("Legit")) {
      if (MoveUtil.isMovingKeybinds()
          && Keyboard.isKeyDown(this.mc.gameSettings.keyBindForward.getKeyCode())
          && (double)
                  Math.abs(
                      MathHelper.wrapAngleTo180_float(this.mc.thePlayer.rotationYaw)
                          - MathHelper.wrapAngleTo180_float(RotationUtil.serverYaw))
              < 66.5) {
        this.mc.thePlayer.setSprinting(true);
        ((IAccessorKeyBinding) this.mc.gameSettings.keyBindSprint).setPressed(true);
      } else {
        ((IAccessorKeyBinding) this.mc.gameSettings.keyBindSprint).setPressed(false);
        this.mc.thePlayer.setSprinting(false);
      }
    }

    this.placed = 0;
    ((IAccessorKeyBinding) this.mc.gameSettings.keyBindSneak).setPressed(false);
    this.silentSneak = false;
    ((IAccessorMinecraft) this.mc).setRightClickDelayTimer(0);
    this.enabledTicks = 0;
    this.sneakTicks = 0;
    this.timerTicks = 0;
    this.adStrafeBlocks = 0;
    this.intaveBypassBlocks = 0;
    this.startYaw = this.mc.thePlayer.rotationYaw;
    if (!RotationUtil.customRots) {
      RotationUtil.serverYaw = this.mc.thePlayer.rotationYaw;
      RotationUtil.serverPitch = this.mc.thePlayer.rotationPitch;
    }

    this.itemBefore = this.mc.thePlayer.inventory.currentItem;
    this.lastYaw = this.mc.thePlayer.rotationYaw - 180.0F;
    this.lastPitch = 79.0F;
    this.posY = (int) (this.mc.thePlayer.posY - 1.0);
    this.spoofSlot = this.mc.thePlayer.inventory.currentItem;
    this.scaffoldYaw = this.mc.thePlayer.rotationYaw - 180.0F;
    this.scaffoldPitch = 79.0F;
  }

  // --- Event Handlers ---

  @EventTarget
  public void onSafeWalk(SafeWalkEvent e) {
    if (this.hasBlocks() && this.safeWalk.getValue()) {
      e.setSafeWalk(true);
    }
  }

  @EventTarget
  public void onMoveInput(MoveInputEvent e) {
    if (!this.hasBlocks()) return;

    if (this.sneak.getModeString().equalsIgnoreCase("Normal")) {
      if (this.sneakDelay.getValue() == 0.0f) {
        ((IAccessorKeyBinding) this.mc.gameSettings.keyBindSneak).setPressed(true);
      } else if ((double) this.sneakTicks >= this.sneakDelay.getValue()) {
        ((IAccessorKeyBinding) this.mc.gameSettings.keyBindSneak).setPressed(true);
        if ((double) this.sneakTicks >= this.sneakDelay.getValue() + this.sneakTime.getValue()) {
          this.sneakTicks = 0;
          ((IAccessorKeyBinding) this.mc.gameSettings.keyBindSneak).setPressed(false);
        }
      }
    }

    if (this.sneak.getModeString().equalsIgnoreCase("Silent")
        && (double) this.sneakTicks >= this.sneakDelay.getValue()) {
      if (!this.silentSneak) {
        PacketUtil.sendPacketNoEvent(
            new C0BPacketEntityAction(
                this.mc.thePlayer, C0BPacketEntityAction.Action.START_SNEAKING));
        this.silentSneak = true;
      } else {
        PacketUtil.sendPacketNoEvent(
            new C0BPacketEntityAction(
                this.mc.thePlayer, C0BPacketEntityAction.Action.STOP_SNEAKING));
        this.silentSneak = false;
        this.sneakTicks = 0;
      }
    }

    if ((double) this.sneakTicks >= this.sneakDelay.getValue()) {
      this.sneakTicks++;
    }

    if (this.adStrafe.getValue() && !MoveUtil.isGoingDiagonally()) {
      int delay = (int) (60.0 * Math.max(1.0, this.adStrafeDelay.getValue() / 2.0));
      if ((double) this.adStrafeBlocks >= this.adStrafeDelay.getValue()
          && this.adStrafeTimer.hasTimeElapsed((double) delay, true)) {
        this.adStrafeBlocks = 0;
        this.adStrafeDirection = !this.adStrafeDirection;
      }

      if (!this.adStrafeTimer.hasTimeElapsed((double) delay, false)
          && MoveUtil.isMovingKeybinds()
          && !Keyboard.isKeyDown(this.mc.gameSettings.keyBindLeft.getKeyCode())
          && !Keyboard.isKeyDown(this.mc.gameSettings.keyBindRight.getKeyCode())) {
        if (this.adStrafeDirection) {
          ((IAccessorKeyBinding) this.mc.gameSettings.keyBindLeft).setPressed(true);
          ((IAccessorKeyBinding) this.mc.gameSettings.keyBindRight).setPressed(false);
        } else {
          ((IAccessorKeyBinding) this.mc.gameSettings.keyBindRight).setPressed(true);
          ((IAccessorKeyBinding) this.mc.gameSettings.keyBindLeft).setPressed(false);
        }
      }
    }
  }

  @EventTarget
  public void onPacket(PacketEvent e) {
    if (!this.hasBlocks()) return;

    if (e.getPacket() instanceof C09PacketHeldItemChange
        && this.spoof.getModeString().equalsIgnoreCase("Fake")) {
      // In Miau PacketEvent is cancellable
      e.setCancelled(true);
    }

    if (e.getPacket() instanceof C0APacketAnimation && !this.serverSideSwing.getValue()) {
      e.setCancelled(true);
    }
  }

  @EventTarget
  public void onTick(TickEvent e) {
    if (e.getType() != EventType.PRE) return;
    if (!this.hasBlocks()) return;

    this.rotationTicks = this.mc.thePlayer.ticksExisted + 1;
    this.polarTicks++;
    this.enabledTicks++;
    ((IAccessorEntityLivingBase) this.mc.thePlayer).setJumpTicks(0);

    // === Timer delay handler (was EventTimeDelay) ===
    if (this.timer.getValue()) {
      if (this.timerDelay.getValue() == 0.0f) {
        ((IAccessorMinecraft) this.mc).getTimer().timerSpeed = this.timerSpeed.getValue();
      } else if ((double) this.timerTicks >= this.timerDelay.getValue()) {
        ((IAccessorMinecraft) this.mc).getTimer().timerSpeed = this.timerSpeed.getValue();
        if ((double) this.timerTicks >= this.timerDelay.getValue() + this.timerTime.getValue()) {
          this.timerTicks = 0;
          ((IAccessorMinecraft) this.mc).getTimer().timerSpeed = 1.0F;
        }
      }
      if ((double) this.timerTicks >= this.timerDelay.getValue()) {
        this.timerTicks++;
      }
    }

    // === Jump logic (was in onTick) ===
    if (this.mc.thePlayer.motionX != 0.0
        && this.mc.thePlayer.motionZ != 0.0
        && this.mc.thePlayer.onGround) {
      if (!Keyboard.isKeyDown(this.mc.gameSettings.keyBindJump.getKeyCode())) {
        if (!this.polarJump.getValue() && !this.godbridgeJump.getValue()) {
          if (this.jump.getValue() || this.hypixelJump.getValue()) {
            this.jump();
          }
        } else if (this.placed >= 8) {
          this.jump();
        }
      }
      this.wasJump = true;
    }
  }

  @EventTarget
  public void onStrafe(StrafeEvent e) {
    if (!this.hasBlocks()) return;

    if (this.moveFix.getValue() && RotationUtil.customRots && !this.mc.isSingleplayer()) {
      MoveUtil.silentMoveFix(e);
    }
  }

  @EventTarget
  public void onJump(JumpEvent e) {
    if (!this.hasBlocks()) return;

    if (this.tower.getModeString().toLowerCase().equals("intave") && !MoveUtil.isMovingKeybinds()) {
      this.mc.thePlayer.motionY = 0.41;
    }

    if (this.sprint.getModeString().equalsIgnoreCase("Motion modifier")) {
      String groundMode = this.motionModifierMode.getModeString();
      String sprintGround = this.motionModifierSprintGround.getModeString();

      if ((this.motionModifierSprint.getModeString().equalsIgnoreCase("Ground")
              || this.motionModifierSprint.getModeString().equalsIgnoreCase("Both"))
          && (sprintGround.equalsIgnoreCase("On jump")
              || sprintGround.equalsIgnoreCase("Tick and jump")
              || sprintGround.equalsIgnoreCase("Place and jump"))) {
        ((IAccessorKeyBinding) this.mc.gameSettings.keyBindSprint).setPressed(true);
        this.mc.thePlayer.setSprinting(
            (this.sprintPacket.getModeString().equalsIgnoreCase("Ground")
                    || this.sprintPacket.getModeString().equalsIgnoreCase("Both"))
                && Keyboard.isKeyDown(this.mc.gameSettings.keyBindForward.getKeyCode()));
      }

      if ((groundMode.equalsIgnoreCase("Ground") || groundMode.equalsIgnoreCase("Both"))
          && (this.motionModifierGround.getModeString().equalsIgnoreCase("On jump")
              || this.motionModifierGround.getModeString().equalsIgnoreCase("Tick and jump")
              || this.motionModifierGround.getModeString().equalsIgnoreCase("Place and jump"))) {
        this.mc.thePlayer.motionX =
            this.mc.thePlayer.motionX * (double) this.groundMotion.getValue();
        this.mc.thePlayer.motionZ =
            this.mc.thePlayer.motionZ * (double) this.groundMotion.getValue();
      }
    }
  }

  // === Main Update Event - handles Look + Motion + Click combined ===

  @EventTarget
  public void onUpdate(UpdateEvent e) {
    if (!this.hasBlocks()) {
      RotationUtil.serverYaw = this.mc.thePlayer.rotationYaw;
      RotationUtil.serverPitch = this.mc.thePlayer.rotationPitch;
      RotationUtil.customRots = false;
      return;
    }

    if (e.getType() == EventType.PRE) {
      // === Intave cloud bypass (was EventMotion) ===
      if (this.intaveCloudBypass.getValue()) {
        if (this.intaveBypassBlocks >= 40) {
          if (MoveUtil.isOnGround(0.01)) {
            if (!this.intaveBypassTimer.hasTimeElapsed(350.0, false)) {
              this.mc.thePlayer.setPosition(this.mc.thePlayer.posX, 1.0E7, this.mc.thePlayer.posZ);
            } else {
              this.intaveBypassBlocks = 0;
            }
          } else {
            this.intaveBypassTimer.reset();
          }
        } else {
          this.intaveBypassTimer.reset();
        }
      }

      // === Sprint handler (was EventMotion) ===
      this.sprintHandler();

      // === Tower handler ===
      this.tower();

      // === Process block data + Rotations (was EventLook) ===
      this.processBlockData();
      this.rotations();

      // === Set rotation on update event (was EventMotion PRE rotation set) ===
      if (RotationUtil.customRots) {
        e.setRotation(RotationUtil.serverYaw, RotationUtil.serverPitch, 100);
      }
    }

    if (e.getType() == EventType.POST) {
      // === Place blocks (was EventClick) ===
      if (this.mc.thePlayer.onGround) {
        this.offGroundTicks = 0;
      } else {
        this.offGroundTicks++;
      }
      this.dragClickTicks++;
      this.place();

      if (this.polarJump.getValue()
          && !Keyboard.isKeyDown(this.mc.gameSettings.keyBindJump.getKeyCode())
          && !this.mc.thePlayer.onGround
          && this.mc.thePlayer.fallDistance > 0.0F) {
        this.jumpBlockPlacementTimer.hasTimeElapsed(250.0, false);
      }

      if (this.dragClick.getValue()) {
        this.fakeClick();
      }
    }
  }

  // === Logic Methods (100% Gothaj) ===

  public void fakeClick() {
    if (!this.rotations.getModeString().equalsIgnoreCase("Static god")
        || !Keyboard.isKeyDown(this.mc.gameSettings.keyBindSneak.getKeyCode())
            && MoveUtil.isMoving()) {
      if (this.mc.thePlayer.getHeldItem() != null
          && this.mc.thePlayer.getHeldItem().getItem() != null
          && this.mc.thePlayer.getHeldItem().getItem() instanceof ItemBlock
          && this.dragClickTicks >= 1
          && Math.random() > 0.1
          && this.blockPos != null
          && this.mc.objectMouseOver.getBlockPos() != null
          && this.mc.objectMouseOver.getBlockPos().equals(this.blockPos)
          && (this.mc.thePlayer.onGround || this.mc.objectMouseOver.sideHit != EnumFacing.UP)) {
        ((IAccessorMinecraft) this.mc).callRightClickMouse();
      }
    }
  }

  public void tower() {
    String var1 = this.tower.getModeString().toLowerCase();
    switch (var1) {
      case "ncp":
        if (this.mc.thePlayer.posY % 1.0 <= 0.41
            && Keyboard.isKeyDown(this.mc.gameSettings.keyBindJump.getKeyCode())) {
          if (!MoveUtil.isMoving()) {
            this.mc.thePlayer.motionX *= 0.0;
            this.mc.thePlayer.motionZ *= 0.0;
          }
          if (this.mc.thePlayer.posY % 1.0 <= 0.41
              && Keyboard.isKeyDown(this.mc.gameSettings.keyBindJump.getKeyCode())) {
            this.mc.thePlayer.setPosition(
                this.mc.thePlayer.posX, Math.floor(this.mc.thePlayer.posY), this.mc.thePlayer.posZ);
            if (MoveUtil.getSpeed() > 0.05) {
              this.mc.thePlayer.motionY -= 0.1;
              MoveUtil.strafe((float) MoveUtil.getBaseMoveSpeedGothaj());
            } else {
              this.jump();
            }
          }
        }
        ((IAccessorEntityLivingBase) this.mc.thePlayer).setJumpTicks(0);
        break;
      case "timer":
        if (Keyboard.isKeyDown(this.mc.gameSettings.keyBindJump.getKeyCode())) {
          ((IAccessorMinecraft) this.mc).getTimer().timerSpeed = 1.25F;
        }
        break;
      case "vanilla":
        if (this.mc.thePlayer.posY % 1.0 <= 0.41
            && Keyboard.isKeyDown(this.mc.gameSettings.keyBindJump.getKeyCode())) {
          this.mc.thePlayer.setPosition(
              this.mc.thePlayer.posX, Math.floor(this.mc.thePlayer.posY), this.mc.thePlayer.posZ);
          this.mc.thePlayer.motionY -= 0.1;
        }
        ((IAccessorEntityLivingBase) this.mc.thePlayer).setJumpTicks(0);
        break;
      case "hypixel":
        if (this.mc.thePlayer.posY % 1.0 <= 0.41
            && Keyboard.isKeyDown(this.mc.gameSettings.keyBindJump.getKeyCode())) {
          this.mc.thePlayer.setPosition(
              this.mc.thePlayer.posX, Math.floor(this.mc.thePlayer.posY), this.mc.thePlayer.posZ);
          this.mc.thePlayer.motionY -= 0.1;
          MoveUtil.strafe((float) MoveUtil.getBaseMoveSpeedGothaj());
        }
        ((IAccessorEntityLivingBase) this.mc.thePlayer).setJumpTicks(0);
        break;
    }
  }

  public void sprintHandler() {
    String sprintMode = this.sprint.getModeString();
    if (!this.sprintWhenJump.getValue()
        && Keyboard.isKeyDown(this.mc.gameSettings.keyBindJump.getKeyCode())) {
      // When jump key pressed and sprintWhenJump disabled
      if (sprintMode.equalsIgnoreCase("Off")) {
        ((IAccessorKeyBinding) this.mc.gameSettings.keyBindSprint).setPressed(false);
        this.mc.thePlayer.setSprinting(false);
      } else if (MoveUtil.isMovingKeybinds()
          && Keyboard.isKeyDown(this.mc.gameSettings.keyBindForward.getKeyCode())
          && (double)
                  Math.abs(
                      MathHelper.wrapAngleTo180_float(this.mc.thePlayer.rotationYaw)
                          - MathHelper.wrapAngleTo180_float(RotationUtil.serverYaw))
              < 66.5) {
        this.mc.thePlayer.setSprinting(true);
        ((IAccessorKeyBinding) this.mc.gameSettings.keyBindSprint).setPressed(true);
      } else {
        ((IAccessorKeyBinding) this.mc.gameSettings.keyBindSprint).setPressed(false);
        this.mc.thePlayer.setSprinting(false);
      }
    } else {
      switch (sprintMode.toLowerCase()) {
        case "allways":
          ((IAccessorKeyBinding) this.mc.gameSettings.keyBindSprint).setPressed(true);
          if (this.mc.thePlayer.moveForward > 0.0F) {
            this.mc.thePlayer.setSprinting(true);
          }
          break;
        case "off":
          ((IAccessorKeyBinding) this.mc.gameSettings.keyBindSprint).setPressed(false);
          this.mc.thePlayer.setSprinting(false);
          break;
        case "legit":
          if (MoveUtil.isMovingKeybinds()
              && Keyboard.isKeyDown(this.mc.gameSettings.keyBindForward.getKeyCode())
              && (double)
                      Math.abs(
                          MathHelper.wrapAngleTo180_float(this.mc.thePlayer.rotationYaw)
                              - MathHelper.wrapAngleTo180_float(RotationUtil.serverYaw))
                  < 66.5) {
            this.mc.thePlayer.setSprinting(true);
            ((IAccessorKeyBinding) this.mc.gameSettings.keyBindSprint).setPressed(true);
          } else {
            ((IAccessorKeyBinding) this.mc.gameSettings.keyBindSprint).setPressed(false);
            this.mc.thePlayer.setSprinting(false);
          }
          break;
        case "switch":
          ((IAccessorKeyBinding) this.mc.gameSettings.keyBindSprint)
              .setPressed(this.mc.thePlayer.ticksExisted % 1 == 0);
          this.mc.thePlayer.setSprinting(this.mc.thePlayer.ticksExisted % 2 == 0);
          break;
        case "no packet":
          ((IAccessorKeyBinding) this.mc.gameSettings.keyBindSprint).setPressed(true);
          break;
        case "packet legit":
          ((IAccessorKeyBinding) this.mc.gameSettings.keyBindSprint).setPressed(true);
          if (this.mc.thePlayer.moveForward > 0.0F) {
            this.mc.thePlayer.setSprinting(true);
          }
          break;
        case "old intave":
          if (!this.mc.thePlayer.onGround
              && MoveUtil.isMovingKeybinds()
              && MoveUtil.getSpeed() < 0.253
              && this.mc.thePlayer.hurtTime == 0
              && this.offGroundSpeedTimer.hasTimeElapsed(150.0, true)) {
            MoveUtil.strafe(0.273F);
          }
          ((IAccessorKeyBinding) this.mc.gameSettings.keyBindSprint).setPressed(false);
          this.mc.thePlayer.setSprinting(false);
          break;
        case "motion modifier":
          ((IAccessorKeyBinding) this.mc.gameSettings.keyBindSprint).setPressed(false);
          this.mc.thePlayer.setSprinting(false);
          if (this.mc.thePlayer.onGround) {
            String mmSprint = this.motionModifierSprint.getModeString();
            String mmGround = this.motionModifierSprintGround.getModeString();
            if (mmSprint.equalsIgnoreCase("Ground") || mmSprint.equalsIgnoreCase("Both")) {
              if (mmGround.equalsIgnoreCase("Allways")) {
                ((IAccessorKeyBinding) this.mc.gameSettings.keyBindSprint).setPressed(true);
                this.mc.thePlayer.setSprinting(
                    (this.sprintPacket.getModeString().equalsIgnoreCase("Ground")
                            || this.sprintPacket.getModeString().equalsIgnoreCase("Both"))
                        && Keyboard.isKeyDown(this.mc.gameSettings.keyBindForward.getKeyCode()));
              }
              if ((mmGround.equalsIgnoreCase("Tick") || mmGround.equalsIgnoreCase("Tick and jump"))
                  && (double) this.mc.thePlayer.ticksExisted
                          % (double) this.motionModifierTick.getValue()
                      == 0.0) {
                ((IAccessorKeyBinding) this.mc.gameSettings.keyBindSprint).setPressed(true);
                this.mc.thePlayer.setSprinting(
                    (this.sprintPacket.getModeString().equalsIgnoreCase("Ground")
                            || this.sprintPacket.getModeString().equalsIgnoreCase("Both"))
                        && Keyboard.isKeyDown(this.mc.gameSettings.keyBindForward.getKeyCode()));
              }
            }
            String mmModeG = this.motionModifierMode.getModeString();
            if ((mmModeG.equalsIgnoreCase("Ground") || mmModeG.equalsIgnoreCase("Both"))
                && (this.motionModifierGround.getModeString().equalsIgnoreCase("Tick")
                    || this.motionModifierGround.getModeString().equalsIgnoreCase("Tick and jump"))
                && (double) this.mc.thePlayer.ticksExisted
                        % (double) this.motionModifierTick.getValue()
                    == 0.0) {
              this.mc.thePlayer.motionX =
                  this.mc.thePlayer.motionX * (double) this.groundMotion.getValue();
              this.mc.thePlayer.motionZ =
                  this.mc.thePlayer.motionZ * (double) this.groundMotion.getValue();
            }
          } else {
            String mmSprintAir = this.motionModifierSprint.getModeString();
            String mmAirMode = this.motionModifierSprintAir.getModeString();
            if (mmSprintAir.equalsIgnoreCase("Air") || mmSprintAir.equalsIgnoreCase("Both")) {
              if (mmAirMode.equalsIgnoreCase("Allways")) {
                ((IAccessorKeyBinding) this.mc.gameSettings.keyBindSprint).setPressed(true);
                this.mc.thePlayer.setSprinting(
                    (this.sprintPacket.getModeString().equalsIgnoreCase("Air")
                            || this.sprintPacket.getModeString().equalsIgnoreCase("Both"))
                        && Keyboard.isKeyDown(this.mc.gameSettings.keyBindForward.getKeyCode()));
              }
              if (mmAirMode.equalsIgnoreCase("Tick")
                  && (double) this.mc.thePlayer.ticksExisted
                          % (double) this.motionModifierTick.getValue()
                      == 0.0) {
                ((IAccessorKeyBinding) this.mc.gameSettings.keyBindSprint).setPressed(true);
                this.mc.thePlayer.setSprinting(
                    (this.sprintPacket.getModeString().equalsIgnoreCase("Air")
                            || this.sprintPacket.getModeString().equalsIgnoreCase("Both"))
                        && Keyboard.isKeyDown(this.mc.gameSettings.keyBindForward.getKeyCode()));
              }
            }
            String mmModeA = this.motionModifierMode.getModeString();
            if ((mmModeA.equalsIgnoreCase("Air") || mmModeA.equalsIgnoreCase("Both"))
                && this.motionModifierAir.getModeString().equalsIgnoreCase("Tick")
                && (double) this.mc.thePlayer.ticksExisted
                        % (double) this.motionModifierTick.getValue()
                    == 0.0) {
              this.mc.thePlayer.motionX =
                  this.mc.thePlayer.motionX * (double) this.airMotion.getValue();
              this.mc.thePlayer.motionZ =
                  this.mc.thePlayer.motionZ * (double) this.airMotion.getValue();
            }
          }
          break;
      }
    }
  }

  // === Rotation Logic (100% Gothaj) ===

  public void rotations() {
    boolean stop = false;
    float currentYaw = this.scaffoldYaw;
    if (MoveUtil.isMoving() && this.mc.thePlayer.hurtTime == 0) {
      currentYaw =
          (float)
              Math.toDegrees(MoveUtil.getDirectionKeybinds(this.mc.thePlayer.rotationYaw - 180.0F));
    }

    float toTurnYaw = currentYaw;
    float currentPitch = this.lastPitch;
    String var5 = this.rotations.getModeString().toLowerCase();

    switch (var5) {
      case "direct":
        currentYaw += 180.0F;
        if (this.blockPos != null && this.facing != null) {
          for (float pitch = 90.0F; pitch > 30.0F; pitch--) {
            if (RotationUtil.lookingAtBlock(
                this.blockPos, this.mc.thePlayer.rotationYaw, pitch, this.facing, false)) {
              currentPitch = pitch;
            }
          }
        }
        break;
      case "intave":
        currentYaw = this.scaffoldYaw;
        if (this.blockPos != null && this.facing != null) {
          MovingObjectPosition rotationRay =
              RotationUtil.rayCast(
                  1.0F,
                  new float[] {this.scaffoldYaw, this.scaffoldPitch},
                  (double) this.mc.playerController.getBlockReachDistance(),
                  2.0);
          this.scaffoldPitch =
              RotationUtil.getYawBasedPitch(
                  this.blockPos, this.facing, currentYaw, this.lastPitch, 84);
          if (rotationRay != null && !rotationRay.getBlockPos().equals(this.blockPos)) {
            float[] rots =
                RotationUtil.getDirectionToBlock(
                    (double) this.blockPos.getX(),
                    (double) this.blockPos.getY(),
                    (double) this.blockPos.getZ(),
                    this.facing);
            int maxTicks = 0;
            if (rots != null) {
              maxTicks =
                  (int)
                      Math.abs(MathHelper.wrapAngleTo180_float(this.scaffoldYaw - rots[0]) / 4.0F);
            }
            for (int ticks = 0; ticks <= maxTicks && !stop; ticks++) {
              if (rots != null) {
                this.scaffoldYaw = RotationUtil.updateRotation(this.scaffoldYaw, rots[0], 5.0F);
              }
              this.scaffoldPitch =
                  RotationUtil.getYawBasedPitch(
                      this.blockPos, this.facing, this.scaffoldYaw, this.lastPitch, 84);
              MovingObjectPosition stopRay =
                  RotationUtil.rayCast(
                      1.0F,
                      new float[] {this.scaffoldYaw, this.scaffoldPitch},
                      (double) this.mc.playerController.getBlockReachDistance(),
                      2.0);
              if (stopRay != null
                  && stopRay.getBlockPos().equals(this.blockPos)
                  && stopRay.sideHit == this.facing) {
                stop = true;
              }
            }
          }
        }
        currentYaw = this.scaffoldYaw;
        currentPitch = this.scaffoldPitch;
        break;
      case "static god":
        this.scaffoldPitch = 75.7F;
        currentYaw = (float) MathHelper.roundUp((int) (this.startYaw + 180.0F), 45);
        currentPitch = this.scaffoldPitch;
        break;
      case "keep":
        if (this.mc.theWorld.isAirBlock(
                new BlockPos(this.mc.thePlayer.posX, (double) this.posY, this.mc.thePlayer.posZ))
            && this.blockPos != null
            && this.facing != null) {
          float[] rots =
              RotationUtil.getDirectionToBlock(
                  (double) this.blockPos.getX(),
                  (double) this.blockPos.getY(),
                  (double) this.blockPos.getZ(),
                  this.facing);
          if (rots != null) {
            this.scaffoldYaw = rots[0];
            this.scaffoldPitch = rots[1];
          }
        }
        currentYaw = this.scaffoldYaw;
        currentPitch = this.scaffoldPitch;
        break;
      case "snap":
        if (this.mc.theWorld.isAirBlock(
            new BlockPos(this.mc.thePlayer.posX, (double) this.posY, this.mc.thePlayer.posZ))) {
          if (this.blockPos != null && this.facing != null) {
            float[] rots =
                RotationUtil.getDirectionToBlock(
                    (double) this.blockPos.getX(),
                    (double) this.blockPos.getY(),
                    (double) this.blockPos.getZ(),
                    this.facing);
            if (rots != null) {
              currentYaw = rots[0];
              currentPitch = rots[1];
            }
          }
        } else {
          currentYaw = this.mc.thePlayer.rotationYaw;
          currentPitch = this.mc.thePlayer.rotationPitch;
        }
        break;
      case "polar":
        currentYaw = this.scaffoldYaw;
        int yawSpeed = 1;
        int pitchSpeed = 1;
        int yawMultiplier = 1;
        int pitchMultiplier = 4;
        float yawAdd =
            this.noise.GetNoise(
                    this.polarTicks * (float) yawSpeed + 50.0F,
                    this.polarTicks * (float) yawSpeed + 50.0F)
                * (float) yawMultiplier;
        float pitchAdd =
            this.noise.GetNoise(
                    this.polarTicks * (float) pitchSpeed, this.polarTicks * (float) pitchSpeed)
                * (float) pitchMultiplier;
        this.scaffoldPitch = 78.0F;

        if (this.mc.thePlayer.motionX != 0.0 && this.mc.thePlayer.motionZ != 0.0) {
          if (this.blockPos != null && this.facing != null) {
            MovingObjectPosition checkRay =
                RotationUtil.rayCast(
                    1.0F,
                    new float[] {this.scaffoldYaw, this.scaffoldPitch},
                    (double) this.mc.playerController.getBlockReachDistance(),
                    2.0);

            if (checkRay != null
                && (checkRay.getBlockPos().getX() != this.blockPos.getX()
                    || checkRay.getBlockPos().getZ() != this.blockPos.getZ()
                    || (Keyboard.isKeyDown(this.mc.gameSettings.keyBindJump.getKeyCode())
                        && checkRay.getBlockPos().getY() != this.blockPos.getY()
                        && this.polarFull.getValue()))) {
              int maxTicksPolar = 720;
              for (int ticks = 0; ticks < maxTicksPolar && !stop; ticks++) {
                if (yawMultiplier < 360) {
                  yawMultiplier++;
                }
                if (ticks >= 100
                    && this.mc.objectMouseOver != null
                    && this.mc.objectMouseOver.sideHit != EnumFacing.UP
                    && this.polarStrong.getValue()) {
                  pitchMultiplier = 6;
                }
                yawAdd =
                    this.noise.GetNoise(
                            this.polarTicks * (float) yawSpeed + 50.0F,
                            this.polarTicks * (float) yawSpeed + 50.0F)
                        * (float) yawMultiplier;
                pitchAdd =
                    this.noise.GetNoise(
                            this.polarTicks * (float) pitchSpeed,
                            this.polarTicks * (float) pitchSpeed)
                        * (float) pitchMultiplier;
                MovingObjectPosition rotationRay =
                    RotationUtil.rayCast(
                        1.0F,
                        new float[] {this.scaffoldYaw + yawAdd, this.scaffoldPitch + pitchAdd},
                        (double) this.mc.playerController.getBlockReachDistance(),
                        2.0);
                if (rotationRay != null
                    && rotationRay.getBlockPos().equals(this.blockPos)
                    && rotationRay.sideHit == this.facing) {
                  currentYaw = this.scaffoldYaw + yawAdd;
                  currentPitch = this.scaffoldPitch + pitchAdd;
                  stop = true;
                }
                this.polarTicks++;
              }
            } else {
              int maxTicksPolar = 100;
              for (int ticks = 0; ticks < maxTicksPolar; ticks++) {
                if (ticks >= 100
                    && this.mc.objectMouseOver != null
                    && this.mc.objectMouseOver.sideHit != EnumFacing.UP
                    && this.polarStrong.getValue()) {
                  pitchMultiplier = 6;
                  maxTicksPolar = 200;
                }
                yawAdd =
                    this.noise.GetNoise(
                            this.polarTicks * (float) yawSpeed + 50.0F,
                            this.polarTicks * (float) yawSpeed + 50.0F)
                        * (float) yawMultiplier;
                pitchAdd =
                    this.noise.GetNoise(
                            this.polarTicks * (float) pitchSpeed,
                            this.polarTicks * (float) pitchSpeed)
                        * (float) pitchMultiplier;
                MovingObjectPosition rotationRay =
                    RotationUtil.rayCast(
                        1.0F,
                        new float[] {this.scaffoldYaw + yawAdd, this.scaffoldPitch + pitchAdd},
                        (double) this.mc.playerController.getBlockReachDistance(),
                        2.0);
                if (rotationRay != null
                    && rotationRay.getBlockPos().equals(this.blockPos)
                    && rotationRay.sideHit == this.facing) {
                  currentYaw = this.scaffoldYaw + yawAdd;
                  currentPitch = this.scaffoldPitch + pitchAdd;
                }
                this.polarTicks++;
              }
            }
          } else {
            yawAdd =
                this.noise.GetNoise(
                        this.polarTicks * (float) yawSpeed + 50.0F,
                        this.polarTicks * (float) yawSpeed + 50.0F)
                    * (float) yawMultiplier;
            pitchAdd =
                this.noise.GetNoise(
                        this.polarTicks * (float) pitchSpeed, this.polarTicks * (float) pitchSpeed)
                    * (float) pitchMultiplier;
            currentYaw = this.scaffoldYaw + yawAdd;
            currentPitch = this.scaffoldPitch + pitchAdd;
          }
        } else {
          currentYaw = this.scaffoldYaw + yawAdd;
        }
        currentPitch = MathHelper.clamp_float(currentPitch, -90.0F, 90.0F);
        break;
      case "telly":
        if (this.enabledTicks <= 2) {
          this.scaffoldYaw = this.mc.thePlayer.rotationYaw;
        } else if ((this.mc.thePlayer.onGround
                || (double) this.mc.thePlayer.fallDistance >= 0.8
                || (this.offGroundTicks != 0 && this.offGroundTicks <= 2))
            && !Keyboard.isKeyDown(this.mc.gameSettings.keyBindJump.getKeyCode())) {
          stop = true;
          if (!this.mc.thePlayer.onGround
              && this.mc.thePlayer.fallDistance == 0.0F
              && this.offGroundTicks > 1) {
            if (this.rotationTicks == this.mc.thePlayer.ticksExisted) {
              this.scaffoldYaw =
                  RotationUtil.updateRotation(
                      this.scaffoldYaw, this.mc.thePlayer.rotationYaw + 180.0F, 90.0F);
              this.rotationTicks++;
            }
          } else if (this.rotationTicks == this.mc.thePlayer.ticksExisted) {
            this.scaffoldYaw =
                RotationUtil.updateRotation(this.scaffoldYaw, this.mc.thePlayer.rotationYaw, 90.0F);
            this.rotationTicks++;
          }
          if (this.blockPos != null && this.facing != null) {
            this.scaffoldPitch =
                RotationUtil.getYawBasedPitch(
                    this.blockPos, this.facing, currentYaw, this.lastPitch, 84);
          }
        } else if (this.blockPos != null && this.facing != null) {
          this.scaffoldPitch =
              RotationUtil.getYawBasedPitch(
                  this.blockPos, this.facing, currentYaw, this.lastPitch, 84);
          MovingObjectPosition rotationRay =
              RotationUtil.rayCast(
                  1.0F,
                  new float[] {this.scaffoldYaw, this.scaffoldPitch},
                  (double) this.mc.playerController.getBlockReachDistance(),
                  2.0);
          if (rotationRay != null && !rotationRay.getBlockPos().equals(this.blockPos)) {
            float[] rots =
                RotationUtil.getDirectionToBlock(
                    (double) this.blockPos.getX(),
                    (double) this.blockPos.getY(),
                    (double) this.blockPos.getZ(),
                    this.facing);
            int maxTicks = 0;
            if (rots != null) {
              maxTicks =
                  (int)
                      Math.abs(MathHelper.wrapAngleTo180_float(this.scaffoldYaw - rots[0]) / 4.0F);
            }
            for (int ticks = 0; ticks <= maxTicks && !stop; ticks++) {
              if (rots != null) {
                this.scaffoldYaw = RotationUtil.updateRotation(this.scaffoldYaw, rots[0], 5.0F);
              }
              this.scaffoldPitch =
                  RotationUtil.getYawBasedPitch(
                      this.blockPos, this.facing, this.scaffoldYaw, this.lastPitch, 84);
              MovingObjectPosition stopRay =
                  RotationUtil.rayCast(
                      1.0F,
                      new float[] {this.scaffoldYaw, this.scaffoldPitch},
                      (double) this.mc.playerController.getBlockReachDistance(),
                      2.0);
              if (stopRay != null
                  && stopRay.getBlockPos().equals(this.blockPos)
                  && stopRay.sideHit == this.facing) {
                stop = true;
              }
            }
          }
        }
        currentYaw = this.scaffoldYaw;
        currentPitch = this.scaffoldPitch;
        break;
      case "hypixel":
        currentYaw = this.scaffoldYaw;
        if (this.blockPos != null && this.facing != null) {
          MovingObjectPosition rotationRay =
              RotationUtil.rayCast(
                  1.0F,
                  new float[] {this.scaffoldYaw, this.scaffoldPitch},
                  (double) this.mc.playerController.getBlockReachDistance(),
                  2.0);
          this.scaffoldPitch =
              RotationUtil.getYawBasedPitch(
                  this.blockPos, this.facing, currentYaw, this.lastPitch, 90);
          if (rotationRay != null && !rotationRay.getBlockPos().equals(this.blockPos)) {
            float[] rots =
                RotationUtil.getDirectionToBlock(
                    (double) this.blockPos.getX(),
                    (double) this.blockPos.getY(),
                    (double) this.blockPos.getZ(),
                    this.facing);
            int maxTicks = 0;
            if (rots != null) {
              maxTicks =
                  (int)
                      Math.abs(MathHelper.wrapAngleTo180_float(this.scaffoldYaw - rots[0]) / 4.0F);
            }
            for (int ticks = 0; ticks <= maxTicks && !stop; ticks++) {
              if (rots != null) {
                this.scaffoldYaw = RotationUtil.updateRotation(this.scaffoldYaw, rots[0], 5.0F);
              }
              this.scaffoldPitch =
                  RotationUtil.getYawBasedPitch(
                      this.blockPos, this.facing, this.scaffoldYaw, this.lastPitch, 90);
              MovingObjectPosition stopRay =
                  RotationUtil.rayCast(
                      1.0F,
                      new float[] {this.scaffoldYaw, this.scaffoldPitch},
                      (double) this.mc.playerController.getBlockReachDistance(),
                      2.0);
              if (stopRay != null
                  && stopRay.getBlockPos().equals(this.blockPos)
                  && stopRay.sideHit == this.facing) {
                stop = true;
              }
            }
          }
        }
        currentYaw = this.scaffoldYaw;
        currentPitch = this.scaffoldPitch;
        break;
    }

    // Post-rotation update
    if (!stop
        && !this.rotations.getModeString().equalsIgnoreCase("Keep")
        && this.rotationTicks == this.mc.thePlayer.ticksExisted) {
      this.scaffoldYaw =
          RotationUtil.updateRotation(
              this.scaffoldYaw,
              toTurnYaw,
              (float) (this.rotations.getModeString().equalsIgnoreCase("Polar") ? 10 : 20));
      this.rotationTicks++;
    }

    float[] gcdRots =
        RotationUtil.getFixedRotation(
            new float[] {currentYaw, currentPitch}, new float[] {this.lastYaw, this.lastPitch});
    RotationUtil.serverYaw = gcdRots[0];
    RotationUtil.serverPitch = gcdRots[1];
    RotationUtil.customRots = true;

    this.lastYaw = currentYaw;
    this.lastPitch = currentPitch;
  }

  // === Block Placement Logic (100% Gothaj) ===

  public void place() {
    if (this.rotations.getModeString().equalsIgnoreCase("Static god")
        && Keyboard.isKeyDown(this.mc.gameSettings.keyBindSneak.getKeyCode())) {
      this.sneakTicks = 0;
      this.timerTicks = 0;
    } else {
      BlockPos bp = this.mc.objectMouseOver.getBlockPos();
      EnumFacing ef = this.mc.objectMouseOver.sideHit;
      Vec3 hv = this.mc.objectMouseOver.hitVec;
      boolean legit =
          this.expand.getValue() == 0.0f
              && !this.rotations.getModeString().equalsIgnoreCase("Direct");

      if (!legit) {
        bp = this.blockPos;
        ef = this.facing;
        hv = RotationUtil.getVec3(bp, ef);
      }

      if (!this.mc.playerController.getIsHittingBlock() || !legit) {
        ((IAccessorMinecraft) this.mc).setRightClickDelayTimer(4);
        if ((this.mc.objectMouseOver.typeOfHit == MovingObjectPosition.MovingObjectType.BLOCK
                || !legit)
            && (this.mc.theWorld.getBlockState(bp).getBlock().getMaterial() != Material.air
                || !legit)) {
          int item = getBlockSlot(false);
          if (item == -1) {
            RotationUtil.serverYaw = this.mc.thePlayer.rotationYaw;
            RotationUtil.serverPitch = this.mc.thePlayer.rotationPitch;
            RotationUtil.customRots = false;
            return;
          }

          if (this.blockPos == null || this.facing == null) {
            return;
          }

          if (this.mc.objectMouseOver == null) {
            return;
          }

          if (!this.rotations.getModeString().equalsIgnoreCase("Direct")
              && !this.rotations.getModeString().equalsIgnoreCase("None")
              && this.expand.getValue() == 0.0f
              && (!this.blockPos.equals(bp) || this.facing != ef)) {
            return;
          }

          ItemStack stack = this.mc.thePlayer.inventory.getStackInSlot(item);

          if (this.spoof.getModeString().equalsIgnoreCase("Fake")) {
            if (item != this.spoofSlot) {
              PacketUtil.sendPacketNoEvent(new C09PacketHeldItemChange(item));
              this.spoofSlot = item;
            }
          } else {
            this.spoofSlot = this.mc.thePlayer.inventory.currentItem;
            this.mc.thePlayer.inventory.currentItem = item;
          }

          if (this.sprint.getModeString().equalsIgnoreCase("Packet legit")
              && this.mc.thePlayer.isSprinting()) {
            PacketUtil.sendPacketNoEvent(
                new C0BPacketEntityAction(
                    this.mc.thePlayer, C0BPacketEntityAction.Action.STOP_SPRINTING));
          }

          if (this.mc.playerController.onPlayerRightClick(
              this.mc.thePlayer, this.mc.theWorld, stack, bp, ef, hv)) {
            this.dragClickTicks = 0;
            if (this.swing.getValue()) {
              this.mc.thePlayer.swingItem();
            } else {
              this.mc.thePlayer.sendQueue.addToSendQueue(new C0APacketAnimation());
            }

            if (!this.polarJump.getValue() && !this.godbridgeJump.getValue()) {
              this.placed++;
            } else {
              if (this.placed >= 8) {
                this.placed = 0;
              }
              if (!MoveUtil.isGoingDiagonally()
                  && this.mc.thePlayer.motionX != 0.0
                  && this.mc.thePlayer.motionZ != 0.0
                  && this.mc.thePlayer.onGround) {
                this.placed++;
              } else {
                this.placed = 0;
              }
            }

            this.adStrafeBlocks++;
            this.intaveBypassBlocks++;
            this.sneakTicks++;
            this.timerTicks++;

            if (this.sprint.getModeString().equalsIgnoreCase("Motion modifier")) {
              if (this.mc.thePlayer.onGround) {
                if ((this.motionModifierSprint.getModeString().equalsIgnoreCase("Ground")
                        || this.motionModifierSprint.getModeString().equalsIgnoreCase("Both"))
                    && (this.motionModifierSprintGround.getModeString().equalsIgnoreCase("On place")
                        || this.motionModifierSprintGround
                            .getModeString()
                            .equalsIgnoreCase("Place and jump"))) {
                  ((IAccessorKeyBinding) this.mc.gameSettings.keyBindSprint).setPressed(true);
                  this.mc.thePlayer.setSprinting(
                      (this.sprintPacket.getModeString().equalsIgnoreCase("Ground")
                              || this.sprintPacket.getModeString().equalsIgnoreCase("Both"))
                          && Keyboard.isKeyDown(this.mc.gameSettings.keyBindForward.getKeyCode()));
                }
                if ((this.motionModifierMode.getModeString().equalsIgnoreCase("Ground")
                        || this.motionModifierMode.getModeString().equalsIgnoreCase("Both"))
                    && (this.motionModifierGround.getModeString().equalsIgnoreCase("On place")
                        || this.motionModifierGround
                            .getModeString()
                            .equalsIgnoreCase("Place and jump"))) {
                  this.mc.thePlayer.motionX =
                      this.mc.thePlayer.motionX * (double) this.groundMotion.getValue();
                  this.mc.thePlayer.motionZ =
                      this.mc.thePlayer.motionZ * (double) this.groundMotion.getValue();
                }
              } else {
                if ((this.motionModifierSprint.getModeString().equalsIgnoreCase("Air")
                        || this.motionModifierSprint.getModeString().equalsIgnoreCase("Both"))
                    && this.motionModifierSprintAir.getModeString().equalsIgnoreCase("On place")) {
                  ((IAccessorKeyBinding) this.mc.gameSettings.keyBindSprint).setPressed(true);
                  this.mc.thePlayer.setSprinting(
                      (this.sprintPacket.getModeString().equalsIgnoreCase("Air")
                              || this.sprintPacket.getModeString().equalsIgnoreCase("Both"))
                          && Keyboard.isKeyDown(this.mc.gameSettings.keyBindForward.getKeyCode()));
                }
                if ((this.motionModifierMode.getModeString().equalsIgnoreCase("Air")
                        || this.motionModifierMode.getModeString().equalsIgnoreCase("Both"))
                    && this.motionModifierAir.getModeString().equalsIgnoreCase("On place")) {
                  this.mc.thePlayer.motionX =
                      this.mc.thePlayer.motionX * (double) this.airMotion.getValue();
                  this.mc.thePlayer.motionZ =
                      this.mc.thePlayer.motionZ * (double) this.airMotion.getValue();
                }
              }
            }
          }

          if (this.sprint.getModeString().equalsIgnoreCase("Packet legit")
              && this.mc.thePlayer.isSprinting()) {
            PacketUtil.sendPacketNoEvent(
                new C0BPacketEntityAction(
                    this.mc.thePlayer, C0BPacketEntityAction.Action.START_SPRINTING));
          }

          if (this.mc.thePlayer.getHeldItem() == null) return;

          int i = stack != null ? stack.stackSize : 0;
          if (stack.stackSize == 0) {
            this.mc.thePlayer.inventory.mainInventory[this.mc.thePlayer.inventory.currentItem] =
                null;
          } else if (stack.stackSize != i || this.mc.playerController.isInCreativeMode()) {
            this.mc.entityRenderer.itemRenderer.resetEquippedProgress();
          }

          if (this.spoof.getModeString().equalsIgnoreCase("Normal")) {
            this.mc.thePlayer.inventory.currentItem = this.spoofSlot;
          }
        }
      }
    }
  }

  // === Block Data Processing (100% Gothaj) ===

  public void processBlockData() {
    if (!this.jump.getValue() && !this.polarJump.getValue() && !this.hypixelJump.getValue()) {
      this.posY = (int) (this.mc.thePlayer.posY - 1.0);
    } else if (Keyboard.isKeyDown(this.mc.gameSettings.keyBindJump.getKeyCode())) {
      this.posY = (int) (this.mc.thePlayer.posY - 1.0);
    }

    int currentY = this.posY;

    if ((this.hypixelJump.getValue() || this.polarJump.getValue())
        && !Keyboard.isKeyDown(this.mc.gameSettings.keyBindJump.getKeyCode())
        && !this.mc.thePlayer.onGround
        && (((double) this.mc.thePlayer.fallDistance > 0.0
                && (double) this.mc.thePlayer.fallDistance < 0.08
                && (int) this.mc.thePlayer.posY <= this.posY + 2)
            || (double) this.mc.thePlayer.fallDistance > 0.95)) {
      currentY = this.posY + 1;
    }

    if (this.expand.getValue() == 0.0f) {
      this.blockPos =
          this.getBlockPos(this.mc.thePlayer.posX, (double) currentY, this.mc.thePlayer.posZ);
    } else {
      Vec3 vec =
          this.expand(new Vec3(this.mc.thePlayer.posX, (double) currentY, this.mc.thePlayer.posZ));
      this.setBlockFacingOld(new BlockPos(vec.xCoord, vec.yCoord + 1.0, vec.zCoord));
    }

    if (this.blockPos != null && this.expand.getValue() == 0.0f) {
      this.facing =
          this.getPlaceSide(this.mc.thePlayer.posX, (double) currentY, this.mc.thePlayer.posZ);
    }
  }

  // === Block Utility Methods (100% Gothaj) ===

  public static boolean isPosSolid(BlockPos pos) {
    Block block = Minecraft.getMinecraft().theWorld.getBlockState(pos).getBlock();
    return (block.getMaterial().isSolid()
            || !block.isTranslucent()
            || block instanceof BlockLadder
            || block instanceof BlockCarpet
            || block instanceof BlockSnow
            || block instanceof BlockSkull)
        && !block.getMaterial().isLiquid()
        && !(block instanceof BlockContainer);
  }

  private Vec3 expand(Vec3 position) {
    if (this.expand.getValue() > 0.0f) {
      double direction = MoveUtil.getDirection(this.mc.thePlayer.rotationYaw);
      Vec3 expandVector = new Vec3(-Math.sin(direction), 0.0, Math.cos(direction));
      int bestExpand = 0;

      for (int i = 0; (double) i < (double) this.expand.getValue() && MoveUtil.isMoving(); i++) {
        Vec3 vec =
            position
                .addVector(0.0, -1.0, 0.0)
                .add(
                    new Vec3(
                        expandVector.xCoord * (double) i,
                        expandVector.yCoord * (double) i,
                        expandVector.zCoord * (double) i));
        this.setBlockFacingOld(new BlockPos(vec.xCoord, (double) this.posY, vec.zCoord));
        if (this.blockPos != null && this.facing != EnumFacing.UP) {
          bestExpand = i;
        }
      }

      position =
          position.add(
              new Vec3(
                  expandVector.xCoord * (double) bestExpand,
                  expandVector.yCoord * (double) bestExpand,
                  expandVector.zCoord * (double) bestExpand));
      position = new Vec3(position.xCoord, (double) (this.posY - 1), position.zCoord);
    }

    return position;
  }

  public void setBlockFacingOld(BlockPos pos) {
    if (this.mc.theWorld.getBlockState(pos.add(0, -1, 0)).getBlock() != Blocks.air) {
      this.blockPos = pos.add(0, -1, 0);
      this.facing = EnumFacing.UP;
    } else if (this.mc.theWorld.getBlockState(pos.add(-1, 0, 0)).getBlock() != Blocks.air) {
      this.blockPos = pos.add(-1, 0, 0);
      this.facing = EnumFacing.EAST;
    } else if (this.mc.theWorld.getBlockState(pos.add(1, 0, 0)).getBlock() != Blocks.air) {
      this.blockPos = pos.add(1, 0, 0);
      this.facing = EnumFacing.WEST;
    } else if (this.mc.theWorld.getBlockState(pos.add(0, 0, -1)).getBlock() != Blocks.air) {
      this.blockPos = pos.add(0, 0, -1);
      this.facing = EnumFacing.SOUTH;
    } else if (this.mc.theWorld.getBlockState(pos.add(0, 0, 1)).getBlock() != Blocks.air) {
      this.blockPos = pos.add(0, 0, 1);
      this.facing = EnumFacing.NORTH;
    } else if (this.mc.theWorld.getBlockState(pos.add(-1, 0, -1)).getBlock() != Blocks.air) {
      this.facing = EnumFacing.EAST;
      this.blockPos = pos.add(-1, 0, -1);
    } else if (this.mc.theWorld.getBlockState(pos.add(1, 0, 1)).getBlock() != Blocks.air) {
      this.facing = EnumFacing.WEST;
      this.blockPos = pos.add(1, 0, 1);
    } else if (this.mc.theWorld.getBlockState(pos.add(1, 0, -1)).getBlock() != Blocks.air) {
      this.facing = EnumFacing.SOUTH;
      this.blockPos = pos.add(1, 0, -1);
    } else if (this.mc.theWorld.getBlockState(pos.add(-1, 0, 1)).getBlock() != Blocks.air) {
      this.facing = EnumFacing.NORTH;
      this.blockPos = pos.add(-1, 0, 1);
    } else if (this.mc.theWorld.getBlockState(pos.add(0, -1, 1)).getBlock() != Blocks.air) {
      this.blockPos = pos.add(0, -1, 1);
      this.facing = EnumFacing.UP;
    } else if (this.mc.theWorld.getBlockState(pos.add(0, -1, -1)).getBlock() != Blocks.air) {
      this.blockPos = pos.add(0, -1, -1);
      this.facing = EnumFacing.UP;
    } else if (this.mc.theWorld.getBlockState(pos.add(1, -1, 0)).getBlock() != Blocks.air) {
      this.blockPos = pos.add(1, -1, 0);
      this.facing = EnumFacing.UP;
    } else if (this.mc.theWorld.getBlockState(pos.add(-1, -1, 0)).getBlock() != Blocks.air) {
      this.blockPos = pos.add(-1, -1, 0);
      this.facing = EnumFacing.UP;
    }
  }

  private BlockPos getBlockPos(double posX, double posY, double posZ) {
    BlockPos playerPos = new BlockPos(posX, posY, posZ);
    ArrayList<Vec3> positions = new ArrayList<>();
    HashMap<Vec3, BlockPos> hashMap = new HashMap<>();

    for (int y = playerPos.getY() - 1; y <= playerPos.getY(); y++) {
      for (int x = playerPos.getX() - 5; x <= playerPos.getX() + 5; x++) {
        for (int z = playerPos.getZ() - 5; z <= playerPos.getZ() + 5; z++) {
          if (isValidBock(new BlockPos(x, y, z))) {
            BlockPos blockPos = new BlockPos(x, y, z);
            Block block = this.mc.theWorld.getBlockState(blockPos).getBlock();
            double ex =
                MathHelper.clamp_double(
                    posX,
                    (double) blockPos.getX(),
                    (double) blockPos.getX() + (double) block.getBlockBoundsMaxX());
            double ey =
                MathHelper.clamp_double(
                    posY + 1.0,
                    (double) blockPos.getY(),
                    (double) blockPos.getY() + (double) block.getBlockBoundsMaxY());
            double ez =
                MathHelper.clamp_double(
                    posZ,
                    (double) blockPos.getZ(),
                    (double) blockPos.getZ() + (double) block.getBlockBoundsMaxZ());
            Vec3 vec3 = new Vec3(ex, ey, ez);
            positions.add(vec3);
            hashMap.put(vec3, blockPos);
          }
        }
      }
    }

    if (positions.isEmpty()) {
      return null;
    } else {
      positions.sort(Comparator.comparingDouble(this::getBestBlock));
      return hashMap.get(positions.get(0));
    }
  }

  private EnumFacing getPlaceSide(double posX, double posY, double posZ) {
    ArrayList<Vec3> positions = new ArrayList<>();
    HashMap<Vec3, EnumFacing> hashMap = new HashMap<>();
    BlockPos playerPos = new BlockPos(posX, posY + 1.0, posZ);

    if (!isPosSolid(this.blockPos.add(0, 1, 0))
        && !this.blockPos.add(0, 1, 0).equals(playerPos)
        && !this.mc.thePlayer.onGround) {
      new BlockPos(posX, posY, posZ);
      if (!this.jump.getValue() && !this.polarJump.getValue() && !this.hypixelJump.getValue()) {
        BlockPos bp = this.blockPos.add(0, 1, 0);
        Vec3 vec4 = this.getBestHitFeet(bp);
        positions.add(vec4);
        hashMap.put(vec4, EnumFacing.UP);
      } else if (Keyboard.isKeyDown(this.mc.gameSettings.keyBindJump.getKeyCode())
          || ((this.hypixelJump.getValue() || this.polarJump.getValue())
              && !this.mc.thePlayer.onGround
              && (double) this.mc.thePlayer.fallDistance > 0.0
              && (double) this.mc.thePlayer.fallDistance < 0.08
              && (double) ((int) this.mc.thePlayer.posY) <= posY + 1.0)
          || (double) this.mc.thePlayer.fallDistance > 0.95) {
        BlockPos bp = this.blockPos.add(0, 1, 0);
        Vec3 vec4 = this.getBestHitFeet(bp);
        positions.add(vec4);
        hashMap.put(vec4, EnumFacing.UP);
      }
    }

    if (!isPosSolid(this.blockPos.add(1, 0, 0)) && !this.blockPos.add(1, 0, 0).equals(playerPos)) {
      BlockPos bp = this.blockPos.add(1, 0, 0);
      Vec3 vec4 = this.getBestHitFeet(bp);
      positions.add(vec4);
      hashMap.put(vec4, EnumFacing.EAST);
    }

    if (!isPosSolid(this.blockPos.add(-1, 0, 0))
        && !this.blockPos.add(-1, 0, 0).equals(playerPos)) {
      BlockPos bp = this.blockPos.add(-1, 0, 0);
      Vec3 vec4 = this.getBestHitFeet(bp);
      positions.add(vec4);
      hashMap.put(vec4, EnumFacing.WEST);
    }

    if (!isPosSolid(this.blockPos.add(0, 0, 1)) && !this.blockPos.add(0, 0, 1).equals(playerPos)) {
      BlockPos bp = this.blockPos.add(0, 0, 1);
      Vec3 vec4 = this.getBestHitFeet(bp);
      positions.add(vec4);
      hashMap.put(vec4, EnumFacing.SOUTH);
    }

    if (!isPosSolid(this.blockPos.add(0, 0, -1))
        && !this.blockPos.add(0, 0, -1).equals(playerPos)) {
      BlockPos bp = this.blockPos.add(0, 0, -1);
      Vec3 vec4 = this.getBestHitFeet(bp);
      positions.add(vec4);
      hashMap.put(vec4, EnumFacing.NORTH);
    }

    positions.sort(
        Comparator.comparingDouble(
            vec3 -> this.mc.thePlayer.getDistance(vec3.xCoord, vec3.yCoord, vec3.zCoord)));
    if (!positions.isEmpty()) {
      Vec3 vec5 = this.getBestHitFeet(this.blockPos);
      if (this.mc.thePlayer.getDistance(vec5.xCoord, vec5.yCoord, vec5.zCoord)
          >= this.mc.thePlayer.getDistance(
              positions.get(0).xCoord, positions.get(0).yCoord, positions.get(0).zCoord)) {
        return hashMap.get(positions.get(0));
      }
    }

    return null;
  }

  private Vec3 getBestHitFeet(BlockPos blockPos) {
    Block block = this.mc.theWorld.getBlockState(blockPos).getBlock();
    double ex =
        MathHelper.clamp_double(
            this.mc.thePlayer.posX,
            (double) blockPos.getX(),
            (double) blockPos.getX() + (double) block.getBlockBoundsMaxX());
    double ey =
        MathHelper.clamp_double(
            this.mc.thePlayer.posY,
            (double) blockPos.getY(),
            (double) blockPos.getY() + (double) block.getBlockBoundsMaxY());
    double ez =
        MathHelper.clamp_double(
            this.mc.thePlayer.posZ,
            (double) blockPos.getZ(),
            (double) blockPos.getZ() + (double) block.getBlockBoundsMaxZ());
    return new Vec3(ex, ey, ez);
  }

  private double getBestBlock(Vec3 vec3) {
    return this.mc.thePlayer.getDistanceSq(vec3.xCoord, vec3.yCoord, vec3.zCoord);
  }

  public static boolean isValidBock(BlockPos blockPos) {
    Block block = Minecraft.getMinecraft().theWorld.getBlockState(blockPos).getBlock();
    return !(block instanceof BlockLiquid)
        && !(block instanceof BlockAir)
        && !(block instanceof BlockChest)
        && !(block instanceof BlockFurnace);
  }

  // === Inventory Utils (Gothaj style, integrated into module) ===

  public int getBlockSlot(boolean hypixel) {
    int item = -1;
    int stacksize = 0;
    if (!hypixel
        && this.mc.thePlayer.getHeldItem() != null
        && this.mc.thePlayer.getHeldItem().getItem() != null
        && this.mc.thePlayer.getHeldItem().getItem() instanceof ItemBlock
        && !invalidBlocks.contains(
            ((ItemBlock) this.mc.thePlayer.getHeldItem().getItem()).getBlock())) {
      return this.mc.thePlayer.inventory.currentItem;
    } else {
      for (int i = 36; i < 45; i++) {
        if (this.mc.thePlayer.inventoryContainer.getSlot(i).getStack() != null
            && this.mc.thePlayer.inventoryContainer.getSlot(i).getStack().getItem()
                instanceof ItemBlock
            && !invalidBlocks.contains(
                ((ItemBlock) this.mc.thePlayer.inventoryContainer.getSlot(i).getStack().getItem())
                    .getBlock())
            && this.mc.thePlayer.inventoryContainer.getSlot(i).getStack().stackSize >= stacksize) {
          item = i - 36;
          stacksize = this.mc.thePlayer.inventoryContainer.getSlot(i).getStack().stackSize;
        }
      }
      return item;
    }
  }

  public boolean hasBlocks() {
    int item = this.getBlockSlot(this.hypixelJump.getValue());
    if (item == -1) {
      RotationUtil.serverYaw = this.mc.thePlayer.rotationYaw;
      RotationUtil.serverPitch = this.mc.thePlayer.rotationPitch;
      RotationUtil.customRots = false;
      return false;
    } else {
      return true;
    }
  }

  public void jump() {
    // In Gothaj: EventJump event = new EventJump(yaw, motionY);
    // Client.INSTANCE.getEventBus().call(event);
    // In Miau: fire JumpEvent for compatibility
    myau.event.impl.JumpEvent event = new myau.event.impl.JumpEvent(this.mc.thePlayer.rotationYaw);
    // We don't have a direct event bus call for jump cancellation in Miau, so we execute directly
    this.mc.thePlayer.motionY = 0.42;
    if (this.mc.thePlayer.isPotionActive(Potion.jump)) {
      this.mc.thePlayer.motionY =
          this.mc.thePlayer.motionY
              + (double)
                  ((float) (this.mc.thePlayer.getActivePotionEffect(Potion.jump).getAmplifier() + 1)
                      * 0.1F);
    }

    if (this.sprint.getModeString().equalsIgnoreCase("No packet")
        || this.mc.thePlayer.isSprinting()) {
      float f = this.mc.thePlayer.rotationYaw * (float) (Math.PI / 180.0);
      this.mc.thePlayer.motionX = this.mc.thePlayer.motionX - (double) (MathHelper.sin(f) * 0.2F);
      this.mc.thePlayer.motionZ = this.mc.thePlayer.motionZ + (double) (MathHelper.cos(f) * 0.2F);
    }

    this.mc.thePlayer.isAirBorne = true;
  }

  // Inner class kept for structural completeness
  private class BlockData {
    public BlockPos position;
    public BlockPos targetPos;
    public EnumFacing face;
  }
}
