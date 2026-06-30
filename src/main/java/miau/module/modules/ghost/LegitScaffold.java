package miau.module.modules.ghost;

import miau.event.EventTarget;
import miau.event.impl.MoveInputEvent;
import miau.event.impl.PacketEvent;
import miau.event.types.EventType;
import miau.event.types.Priority;
import miau.module.Module;
import miau.property.properties.BooleanProperty;
import miau.property.properties.FloatProperty;
import miau.property.properties.IntProperty;
import miau.util.client.KeyBindUtil;
import miau.util.player.MoveUtil;
import net.minecraft.block.BlockAir;
import net.minecraft.client.Minecraft;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.network.play.client.C08PacketPlayerBlockPlacement;
import net.minecraft.util.BlockPos;
import net.minecraft.util.MathHelper;
import net.minecraft.util.Vec3;

/**
 * LegitScaffold — Predictive edge-detection scaffold
 *
 * <p>Simulates the player's position one tick ahead before Minecraft processes movement, allowing
 * it to know if the player is about to walk off an edge. This means with maximum vanilla offset
 * (0.3) you won't sneak unless you're actually going to fall — unlike other implementations (Vape,
 * Miau) that only check for air under you after you've already moved.
 */
public class LegitScaffold extends Module {
  private static final Minecraft mc = Minecraft.getMinecraft();

  private static final double HW = 0.3;
  private static final double[][] CORNERS = {{-HW, -HW}, {HW, -HW}, {-HW, HW}, {HW, HW}};

  public final FloatProperty edgeOffset = new FloatProperty("Edge offset", 0.0F, 0.0F, 0.3F);
  public final IntProperty unsneakDelay = new IntProperty("Unsneak delay", 50, 50, 300);
  public final IntProperty sneakOnJump = new IntProperty("Sneak on jump", 0, 0, 500);

  public final BooleanProperty sneakKeyPressed = new BooleanProperty("Sneak key pressed", false);
  public final BooleanProperty holdingBlocks = new BooleanProperty("Holding blocks", false);
  public final BooleanProperty lookingDown = new BooleanProperty("Looking down", false);
  public final BooleanProperty notMovingForward = new BooleanProperty("Not moving forward", false);

  private boolean sneakingFromModule;
  private boolean placed;
  private boolean forceRelease;
  private int sneakJumpDelayTicks = -1;
  private int sneakJumpStartTick = -1;
  private int unsneakDelayTicks = -1;
  private int unsneakStartTick = -1;

  public LegitScaffold() {
    super("Legit Scaffold", false);
  }

  @Override
  public void onDisabled() {
    sneakingFromModule = false;
    resetUnsneak();
  }

  @EventTarget(Priority.LOWEST)
  public void onMoveInput(MoveInputEvent event) {
    if (!this.isEnabled()) return;
    if (mc.thePlayer == null || mc.currentScreen != null) return;

    boolean manualSneak = isManualSneak();
    boolean requireSneak = sneakKeyPressed.getValue();

    // ── Manual sneak without "sneak key pressed" requirement → let it pass ──
    if (manualSneak && !requireSneak) {
      resetUnsneak();
      return;
    }

    // ── Module requires sneak key but player isn't holding it ──
    if (requireSneak
        && (!manualSneak
            || (mc.thePlayer.movementInput.moveForward == 0
                && mc.thePlayer.movementInput.moveStrafe == 0))) {
      if (!manualSneak) resetUnsneak();
      repressSneak();
      return;
    }

    // ── Safety / bypass checks ──
    if (notMovingForward.getValue() && mc.thePlayer.movementInput.moveForward > 0) {
      clearSneak();
      return;
    }
    if (lookingDown.getValue() && mc.thePlayer.rotationPitch < 70) {
      clearSneak();
      return;
    }
    if (holdingBlocks.getValue()) {
      ItemStack held = mc.thePlayer.getHeldItem();
      if (held == null || !(held.getItem() instanceof ItemBlock)) {
        clearSneak();
        return;
      }
    }

    // ── Sneak-on-jump: hold sneak while jumping ──
    if (mc.thePlayer.onGround
        && mc.gameSettings.keyBindJump.isKeyDown()
        && (mc.thePlayer.movementInput.moveForward != 0
            || mc.thePlayer.movementInput.moveStrafe != 0)
        && sneakOnJump.getValue() > 0) {
      if (!requireSneak || forceRelease) {
        sneakJumpStartTick = mc.thePlayer.ticksExisted;
        double raw = sneakOnJump.getValue() / 50.0;
        int base = (int) raw;
        sneakJumpDelayTicks = base + (Math.random() < (raw - base) ? 1 : 0);
        pressSneak(true);
        return;
      }
    }

    // ────────────────────────────────────────────────────────────────────────
    //  Predictive simulation: where will the player be after one tick
    //  WITHOUT sneak?  We use MoveUtil.predictMovement() which reads raw
    //  keyboard state directly, not movementInput, so the 0.3x sneak
    //  multiplier has NOT been applied yet — this is exactly what we want.
    // ────────────────────────────────────────────────────────────────────────
    Vec3 currentPos = new Vec3(mc.thePlayer.posX, mc.thePlayer.posY, mc.thePlayer.posZ);
    double[] predictedMotion = MoveUtil.predictMovement();

    // Vertical component: if not on ground, carry existing fall / jump momentum
    double verticalMotion = mc.thePlayer.onGround ? 0.0 : mc.thePlayer.motionY;
    Vec3 simulatedPos =
        currentPos.addVector(predictedMotion[0], verticalMotion, predictedMotion[1]);

    double offset = computeEdgeOffset(simulatedPos, currentPos);

    if (Double.isNaN(offset)) {
      // No block found under any corner → player is already in the air
      if (sneakingFromModule) tryReleaseSneak(true);
      return;
    }

    if (offset > edgeOffset.getValue()) {
      pressSneak(true);
    } else if (sneakingFromModule) {
      tryReleaseSneak(true);
    }
  }

  @EventTarget
  public void onSendPacket(PacketEvent e) {
    if (!this.isEnabled()) return;
    if (e.getType() != EventType.SEND) return;
    if (e.getPacket() instanceof C08PacketPlayerBlockPlacement) {
      C08PacketPlayerBlockPlacement c08 = (C08PacketPlayerBlockPlacement) e.getPacket();
      if (c08.getPlacedBlockDirection() != 255
          && sneakingFromModule
          && sneakKeyPressed.getValue()) {
        placed = true;
      }
    }
  }

  // ══════════════════════════════════════════════════════════════════════════
  //  Sneak helpers
  // ══════════════════════════════════════════════════════════════════════════

  private void pressSneak(boolean resetDelay) {
    mc.thePlayer.movementInput.sneak = true;
    sneakingFromModule = true;
    if (resetDelay) unsneakStartTick = -1;
    repressSneak();
  }

  private void tryReleaseSneak(boolean resetDelay) {
    int existed = mc.thePlayer.ticksExisted;
    if (unsneakStartTick == -1 && sneakJumpStartTick == -1) {
      unsneakStartTick = existed;
      double raw = (unsneakDelay.getValue() - 50) / 50.0;
      int base = (int) raw;
      unsneakDelayTicks = base + (Math.random() < (raw - base) ? 1 : 0);
    }

    // Jump sneak timer still active → keep pressing
    if (sneakJumpStartTick != -1 && existed - sneakJumpStartTick < sneakJumpDelayTicks) {
      pressSneak(false);
      return;
    }
    // Unsneak delay timer still active → keep pressing
    if (unsneakStartTick != -1 && existed - unsneakStartTick < unsneakDelayTicks) {
      pressSneak(false);
      return;
    }

    releaseSneak(resetDelay);
  }

  private void releaseSneak(boolean resetDelay) {
    if (!sneakKeyPressed.getValue()) {
      // Normal mode: just let go
      mc.thePlayer.movementInput.sneak = false;
    } else if (sneakingFromModule && isManualSneak() && (placed || !mc.thePlayer.onGround)) {
      // "Sneak key pressed" mode: force-release the underlying keybinding
      KeyBinding.setKeyBindState(mc.gameSettings.keyBindSneak.getKeyCode(), false);
      mc.thePlayer.movementInput.sneak = false;
      forceRelease = true;
    } else if (forceRelease) {
      mc.thePlayer.movementInput.sneak = false;
    }

    sneakingFromModule = false;
    placed = false;
    if (resetDelay) resetUnsneak();
  }

  private void repressSneak() {
    if (forceRelease && isManualSneak()) {
      KeyBinding.setKeyBindState(mc.gameSettings.keyBindSneak.getKeyCode(), true);
      mc.thePlayer.movementInput.sneak = true;
    }
    forceRelease = false;
  }

  private void clearSneak() {
    sneakingFromModule = false;
    resetUnsneak();
    if (sneakKeyPressed.getValue()) repressSneak();
  }

  private void resetUnsneak() {
    unsneakStartTick = -1;
    sneakJumpStartTick = -1;
    sneakJumpDelayTicks = -1;
    unsneakDelayTicks = -1;
  }

  private boolean isManualSneak() {
    return KeyBindUtil.isKeyDown(mc.gameSettings.keyBindSneak.getKeyCode());
  }

  // ══════════════════════════════════════════════════════════════════════════
  //  Corner-based edge detection
  //
  //  For each corner of the player hitbox (±0.3 from center) we:
  //    1. Find what block (if any) is under that corner at the CURRENT position
  //    2. Compute how far the SIMULATED position is from that block's edge
  //    3. Return the minimum distance across all corners
  //
  //  A result > threshold means the player is about to walk off that block.
  // ══════════════════════════════════════════════════════════════════════════

  private double computeEdgeOffset(Vec3 simulatedPos, Vec3 currentPos) {
    int floorY = MathHelper.floor_double(simulatedPos.yCoord - 0.01);
    double best = Double.NaN;

    for (double[] corner : CORNERS) {
      int bx = MathHelper.floor_double(currentPos.xCoord + corner[0]);
      int bz = MathHelper.floor_double(currentPos.zCoord + corner[1]);

      BlockPos blockPos = new BlockPos(bx, floorY, bz);
      if (mc.theWorld.getBlockState(blockPos).getBlock() instanceof BlockAir) continue;

      // Distance from simulated position centre to the nearer block edge in X
      double offX = Math.abs(simulatedPos.xCoord - (bx + (simulatedPos.xCoord < bx + 0.5 ? 0 : 1)));
      // Distance from simulated position centre to the nearer block edge in Z
      double offZ = Math.abs(simulatedPos.zCoord - (bz + (simulatedPos.zCoord < bz + 0.5 ? 0 : 1)));

      boolean xDiff = MathHelper.floor_double(simulatedPos.xCoord) != bx;
      boolean zDiff = MathHelper.floor_double(simulatedPos.zCoord) != bz;

      double cornerDist;
      if (xDiff) {
        cornerDist = zDiff ? Math.max(offX, offZ) : offX;
      } else {
        cornerDist = zDiff ? offZ : 0;
      }

      if (Double.isNaN(best) || cornerDist < best) {
        best = cornerDist;
      }
    }

    return best;
  }

  @Override
  public String[] getSuffix() {
    float offset = edgeOffset.getValue();
    if (offset == Math.rint(offset)) {
      return new String[] {Integer.toString((int) offset)};
    }
    return new String[] {String.format("%.2f", offset)};
  }
}
