package miau.module.modules.ghost;

import java.util.HashMap;
import java.util.Map;
import miau.Miau;
import miau.event.EventTarget;
import miau.event.impl.LeftClickMouseEvent;
import miau.event.impl.PacketEvent;
import miau.event.impl.RightClickMouseEvent;
import miau.event.impl.UpdateEvent;
import miau.event.types.EventType;
import miau.event.types.Priority;
import miau.module.Module;
import miau.property.properties.BooleanProperty;
import miau.property.properties.FloatProperty;
import miau.property.properties.IntProperty;
import miau.util.player.MoveUtil;
import miau.util.player.RayCastUtil;
import miau.util.world.BlockUtil;
import net.minecraft.block.Block;
import net.minecraft.block.BlockAir;
import net.minecraft.block.BlockFire;
import net.minecraft.block.BlockLiquid;
import net.minecraft.client.Minecraft;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.network.play.client.C03PacketPlayer;
import net.minecraft.util.BlockPos;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.MathHelper;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.Vec3;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

public class Clutch extends Module {

  private static final Minecraft mc = Minecraft.getMinecraft();
  private static final Map<String, Integer> BLOCK_SCORE = new HashMap<>();

  static {
    BLOCK_SCORE.put("obsidian", 0);
    BLOCK_SCORE.put("end_stone", 1);
    BLOCK_SCORE.put("planks", 2);
    BLOCK_SCORE.put("log", 2);
    BLOCK_SCORE.put("log2", 2);
    BLOCK_SCORE.put("glass", 3);
    BLOCK_SCORE.put("stained_glass", 3);
    BLOCK_SCORE.put("hardened_clay", 4);
    BLOCK_SCORE.put("stained_hardened_clay", 4);
    BLOCK_SCORE.put("stone", 5);
    BLOCK_SCORE.put("wool", 5);
  }

  // ── Blocks the player can fall through ──
  private static boolean canPlaceThrough(Block block) {
    return block instanceof BlockAir || block instanceof BlockLiquid || block instanceof BlockFire;
  }

  private static final double HW = 0.3;
  private static final double[][] CORNERS = {{-HW, -HW}, {HW, -HW}, {-HW, HW}, {HW, HW}};

  // ── Properties ──
  public final FloatProperty reach = new FloatProperty("Reach", 4.5f, 0.5f, 6.0f);
  public final FloatProperty speed = new FloatProperty("Speed", 8f, 0f, 100f);
  public final FloatProperty snapbackSpeed = new FloatProperty("Snapback Speed", 12f, 0f, 100f);
  public final IntProperty maxDistance = new IntProperty("Max distance", 10, 0, 20);
  public final FloatProperty rotationTolerance =
      new FloatProperty("Rotation Tolerance", 25f, 20f, 100f);
  public final BooleanProperty simulateFuture =
      new BooleanProperty("Simulate future position", true);
  public final IntProperty selectKeybind = new IntProperty("Select Keybind", 0, 0, 0);

  // ── Internal state ──
  private float serverYaw;
  private float serverPitch;
  private BlockPos placePos; // BlockPos where the new block goes
  private BlockPos hitBlockPos; // BlockPos of the support block being clicked
  private EnumFacing hitSide; // Face of the support block being clicked
  private Vec3 hitVec; // Exact hit point on the support block face
  private boolean placeQueued;
  private boolean placing;
  private boolean slotWasSwapped;
  private boolean autoClickerWasOn;
  private int prevSlot = -1;
  private int plannedSlot = -1;
  private float aimYaw;
  private float aimPitch;
  private BlockPos targetBlockPos;
  private EnumFacing targetSide;
  private boolean hasAim;
  private boolean resetting;
  private int lastPlacedX = -999;
  private int lastPlacedY = -999;
  private int lastPlacedZ = -999;
  private int clutchBlocksPlaced = 0;

  public Clutch() {
    super("Clutch", false);
  }

  @Override
  public void onEnabled() {
    if (mc.thePlayer != null) {
      serverYaw = mc.thePlayer.rotationYaw;
      serverPitch = mc.thePlayer.rotationPitch;
    }
    hasAim = false;
    resetting = false;
    clutchBlocksPlaced = 0;
  }

  @Override
  public void onDisabled() {
    disablePlacing();
  }

  @EventTarget(Priority.LOWEST)
  public void onUpdate(UpdateEvent event) {
    if (event.getType() != EventType.PRE) return;
    if (!this.isEnabled()) return;

    boolean pressed = selectKeybind.getValue() != 0 && Keyboard.isKeyDown(selectKeybind.getValue());
    if (!pressed || mc.currentScreen != null) {
      clearAim();
      disablePlacing();
      return;
    }

    Vec3 pos = mc.thePlayer.getPositionVector();

    if (mc.thePlayer.onGround) clutchBlocksPlaced = 0;

    if (!needsClutch(pos)) {
      disablePlacing();
      return;
    }

    int weakSlot = pickBlockSlot();
    if (weakSlot == -1) {
      disablePlacing();
      return;
    }

    plannedSlot = weakSlot;

    Object[] tgt = clutchAim();
    if (tgt != null) {
      MovingObjectPosition mop = (MovingObjectPosition) tgt[0];
      targetBlockPos = mop.getBlockPos();
      targetSide = mop.sideHit;
      aimYaw = (float) tgt[1];
      aimPitch = (float) tgt[2];
      hasAim = true;
      resetting = false;
    }

    if (hasAim && !placing) enablePlacing();

    if (placing) {
      KeyBinding.setKeyBindState(mc.gameSettings.keyBindAttack.getKeyCode(), false);
      KeyBinding.setKeyBindState(mc.gameSettings.keyBindUseItem.getKeyCode(), false);
      equipPlannedSlot();
    }

    // ── Step 2: getRotations equivalent ──
    Float[] rots = computeRotations();
    if (rots != null) {
      event.setRotation(rots[0], rots[1], 100);
    }
    executePlacement();
  }

  private Float[] computeRotations() {
    if (resetting) {
      aimYaw = mc.thePlayer.rotationYaw;
      aimPitch = mc.thePlayer.rotationPitch;
      Float[] sm = getRotationsSmoothed(aimYaw, aimPitch, true);
      if (Math.abs(sm[0] - aimYaw) < 0.5f && Math.abs(sm[1] - aimPitch) < 0.5f) {
        resetting = false;
        return null;
      }
      return sm;
    }

    if (!hasAim) return null;

    Float[] sm = getRotationsSmoothed(aimYaw, aimPitch, false);

    if (placing && targetBlockPos != null) {
      double r = reach.getValue().doubleValue();
      MovingObjectPosition chk = raycastBlock(r, sm[0], sm[1]);

      if (chk != null && chk.typeOfHit == MovingObjectPosition.MovingObjectType.BLOCK) {
        if (chk.getBlockPos().equals(targetBlockPos) && chk.sideHit == targetSide) {
          int max = maxDistance.getValue();
          if (max == 0 || clutchBlocksPlaced < max) {
            double tol = rotationTolerance.getValue().doubleValue();
            if (Math.abs(sm[0] - serverYaw) <= tol && Math.abs(sm[1] - serverPitch) <= tol) {
              hitBlockPos = chk.getBlockPos();
              hitSide = chk.sideHit;
              hitVec = chk.hitVec;
              placePos = chk.getBlockPos().offset(chk.sideHit);
              placeQueued = true;
            }
          }
        }
      }
    }

    return sm;
  }

  // ══════════════════════════════════════════════════════════════════════════
  //  Placement execution (original onPreUpdate)
  // ══════════════════════════════════════════════════════════════════════════

  private void executePlacement() {
    if (!placeQueued) return;
    placeQueued = false;

    if (hitBlockPos == null || hitSide == null || placePos == null) return;

    Block targetBlock = BlockUtil.getBlock(placePos);
    if (!(targetBlock instanceof BlockAir) && !canPlaceThrough(targetBlock)) {
      // Block no longer placeable — someone else placed or something changed
      return;
    }

    ItemStack held = mc.thePlayer.getHeldItem();
    if (held == null || !(held.getItem() instanceof ItemBlock)) return;

    mc.playerController.onPlayerRightClick(
        mc.thePlayer,
        mc.theWorld,
        held,
        hitBlockPos,
        hitSide,
        hitVec != null
            ? hitVec
            : new Vec3(placePos.getX() + 0.5, placePos.getY() + 0.5, placePos.getZ() + 0.5));

    if (hitSide != EnumFacing.UP) clutchBlocksPlaced++;

    lastPlacedX = placePos.getX();
    lastPlacedY = placePos.getY();
    lastPlacedZ = placePos.getZ();
    mc.thePlayer.swingItem();
  }

  @EventTarget
  public void onPacket(PacketEvent event) {
    if (event.getType() != EventType.SEND) return;
    if (!this.isEnabled()) return;

    if (event.getPacket() instanceof C03PacketPlayer) {
      C03PacketPlayer c03 = (C03PacketPlayer) event.getPacket();
      if (c03.getRotating()) {
        serverYaw = c03.getYaw();
        serverPitch = c03.getPitch();
      }
    }
  }

  @EventTarget
  public void onRightClick(RightClickMouseEvent event) {
    if (placing && this.isEnabled()) event.setCancelled(true);
  }

  @EventTarget
  public void onLeftClick(LeftClickMouseEvent event) {
    if (placing && this.isEnabled()) event.setCancelled(true);
  }

  // ══════════════════════════════════════════════════════════════════════════
  //  State management
  // ══════════════════════════════════════════════════════════════════════════

  private void enablePlacing() {
    if (placing) return;
    placing = true;
    slotWasSwapped = false;
    prevSlot = mc.thePlayer.inventory.currentItem;

    // Save AutoClicker state and disable if active
    autoClickerWasOn = false;
    Module autoClicker = Miau.moduleManager.modules.get(AutoClicker.class);
    if (autoClicker != null && autoClicker.isEnabled()) {
      autoClickerWasOn = true;
      autoClicker.setEnabled(false);
    }
  }

  private void disablePlacing() {
    if (!placing) return;

    // Restore previous hotbar slot
    if (slotWasSwapped && prevSlot != -1 && prevSlot != mc.thePlayer.inventory.currentItem) {
      mc.thePlayer.inventory.currentItem = prevSlot;
    }

    placing = false;
    slotWasSwapped = false;
    prevSlot = -1;
    plannedSlot = -1;

    // Restore actual mouse key state (sync with hardware)
    if (mc.currentScreen == null) {
      KeyBinding.setKeyBindState(mc.gameSettings.keyBindAttack.getKeyCode(), Mouse.isButtonDown(0));
      KeyBinding.setKeyBindState(
          mc.gameSettings.keyBindUseItem.getKeyCode(), Mouse.isButtonDown(1));
    }

    // Re-enable AutoClicker if it was on
    if (autoClickerWasOn) {
      Module autoClicker = Miau.moduleManager.modules.get(AutoClicker.class);
      if (autoClicker != null && !autoClicker.isEnabled()) {
        autoClicker.setEnabled(true);
      }
      autoClickerWasOn = false;
    }
  }

  private void clearAim() {
    targetBlockPos = null;
    targetSide = null;
    hitBlockPos = null;
    hitSide = null;
    hitVec = null;
    placePos = null;
    lastPlacedX = lastPlacedY = lastPlacedZ = -999;
    clutchBlocksPlaced = 0;
    if (hasAim) resetting = true;
    hasAim = false;
  }

  // ══════════════════════════════════════════════════════════════════════════
  //  Clutch aiming
  // ══════════════════════════════════════════════════════════════════════════

  private boolean needsClutch(Vec3 pos) {
    int x = MathHelper.floor_double(pos.xCoord);
    int y = MathHelper.floor_double(pos.yCoord) - 1;
    int z = MathHelper.floor_double(pos.zCoord);
    Block below = BlockUtil.getBlock(new BlockPos(x, y, z));
    return canPlaceThrough(below);
  }

  /**
   * Returns {@code [MovingObjectPosition, yaw, pitch]} if a valid placement target was found, or
   * {@code null} if no suitable block is available.
   */
  private Object[] clutchAim() {
    Vec3 p = mc.thePlayer.getPositionVector();
    Vec3 eye = p.addVector(0, mc.thePlayer.getEyeHeight(), 0);
    double r = reach.getValue().doubleValue();

    boolean simulate = simulateFuture.getValue();
    Vec3 futurePos = p;
    if (simulate) {
      futurePos = simulateFuturePosition(p);
    }

    int feetX = MathHelper.floor_double(p.xCoord);
    int feetZ = MathHelper.floor_double(p.zCoord);
    int feetY = MathHelper.floor_double(p.yCoord);
    int minX = feetX - 5, maxX = feetX + 4;
    int minZ = feetZ - 5, maxZ = feetZ + 4;
    int maxY = feetY - 1;
    int minY = feetY - 4;

    // Collect candidate blocks with distance scores
    java.util.ArrayList<Object[]> cands = new java.util.ArrayList<>();
    for (int y = maxY; y >= minY; y--) {
      for (int x = minX; x <= maxX; x++) {
        for (int z = minZ; z <= maxZ; z++) {
          BlockPos bp = new BlockPos(x, y, z);
          Block block = BlockUtil.getBlock(bp);
          if (canPlaceThrough(block)) continue;

          double currentDist = distToAABB(p, bp);
          double futureDist = distToAABB(futurePos, bp);
          double score = simulate ? (currentDist * 0.3 + futureDist * 0.7) : currentDist;
          if (bp.getX() == lastPlacedX && bp.getY() == lastPlacedY && bp.getZ() == lastPlacedZ) {
            score *= 0.95;
          }
          cands.add(new Object[] {score, bp});
        }
      }
    }

    cands.sort((a, b) -> Double.compare((Double) a[0], (Double) b[0]));

    ItemStack held = inventorySlotStack(plannedSlot);
    for (Object[] cand : cands) {
      BlockPos bp = (BlockPos) cand[1];
      boolean underPlayer = isBlockUnderPlayer(bp, p);
      Object[] result = getBestRotationsToBlock(held, bp, eye, r, underPlayer);
      if (result != null) return result;
    }

    return null;
  }

  /**
   * Simulate the player's position up to 20 ticks ahead, applying gravity and horizontal movement
   * prediction.
   */
  private Vec3 simulateFuturePosition(Vec3 startPos) {
    double simX = startPos.xCoord;
    double simY = startPos.yCoord;
    double simZ = startPos.zCoord;
    double simMotionY = mc.thePlayer.motionY;
    double[] hMotion = MoveUtil.predictMovement();

    for (int t = 0; t < 20; t++) {
      // Horizontal
      simX += hMotion[0];
      simZ += hMotion[1];

      // Vertical — simplified gravity
      if (!mc.thePlayer.onGround) {
        simMotionY -= 0.08;
        simMotionY *= 0.98; // air drag (approximate)
      } else {
        simMotionY = 0;
      }
      simY += simMotionY;

      // Collision stop: if y drops far or we'd be "on ground"
      if (simY < startPos.yCoord - 2.0) break;
      if (simMotionY < 0 && simY <= Math.floor(startPos.yCoord)) {
        // Simplified ground detection
        BlockPos below =
            new BlockPos(
                MathHelper.floor_double(simX),
                MathHelper.floor_double(simY) - 1,
                MathHelper.floor_double(simZ));
        Block b = BlockUtil.getBlock(below);
        if (!canPlaceThrough(b)) break;
      }
    }

    return new Vec3(simX, simY, simZ);
  }

  private boolean isBlockUnderPlayer(BlockPos bp, Vec3 pos) {
    if (bp.getY() >= MathHelper.floor_double(pos.yCoord)) return false;
    for (double[] c : CORNERS) {
      int cx = MathHelper.floor_double(pos.xCoord + c[0]);
      int cz = MathHelper.floor_double(pos.zCoord + c[1]);
      if (bp.getX() == cx && bp.getZ() == cz) return true;
    }
    return false;
  }

  // ══════════════════════════════════════════════════════════════════════════
  //  Rotation optimisation — grid search over block face points
  // ══════════════════════════════════════════════════════════════════════════

  /**
   * Searches for the cheapest rotation that raycasts to the given support block and allows block
   * placement. Returns {@code [MovingObjectPosition, yaw, pitch]} on success, or {@code null}.
   */
  private Object[] getBestRotationsToBlock(
      ItemStack held, BlockPos bp, Vec3 eye, double reach, boolean underPlayer) {
    double INSET = 0.05, STEP = 0.2, JIT = STEP * 0.1;
    boolean faceSOUTH = Math.abs(eye.zCoord - (bp.getZ() + 1)) < Math.abs(eye.zCoord - bp.getZ());
    boolean faceEAST = Math.abs(eye.xCoord - (bp.getX() + 1)) < Math.abs(eye.xCoord - bp.getX());
    float baseYaw = normYaw(serverYaw);
    float basePit = serverPitch;
    int n = (int) Math.round(1.0 / STEP);

    java.util.ArrayList<Object[]> cands = new java.util.ArrayList<>();
    cands.add(new Object[] {0D, baseYaw, basePit});

    for (int r = 0; r <= n; r++) {
      double v = r * STEP + (Math.random() * 2 * JIT - JIT);
      if (v < 0) v = 0;
      else if (v > 1) v = 1;

      for (int c = 0; c <= n; c++) {
        double u = c * STEP + (Math.random() * 2 * JIT - JIT);
        if (u < 0) u = 0;
        else if (u > 1) u = 1;

        if (underPlayer) {
          // Top face (hardened Y)
          float[] rV =
              getRotationsWrapped(eye, bp.getX() + u, bp.getY() + 1 - INSET, bp.getZ() + v);
          double costV =
              Math.abs(wrapYawDelta(baseYaw, rV[0])) + Math.abs(rV[1] - (double) basePit);
          cands.add(new Object[] {costV, rV[0], rV[1]});
        }

        // Z face
        float[] rZ =
            getRotationsWrapped(
                eye,
                bp.getX() + u,
                bp.getY() + v,
                faceSOUTH ? bp.getZ() + 1 - INSET : bp.getZ() + INSET);
        double costZ = Math.abs(wrapYawDelta(baseYaw, rZ[0])) + Math.abs(rZ[1] - (double) basePit);
        cands.add(new Object[] {costZ, rZ[0], rZ[1]});

        // X face
        float[] rX =
            getRotationsWrapped(
                eye,
                faceEAST ? bp.getX() + 1 - INSET : bp.getX() + INSET,
                bp.getY() + v,
                bp.getZ() + u);
        double costX = Math.abs(wrapYawDelta(baseYaw, rX[0])) + Math.abs(rX[1] - (double) basePit);
        cands.add(new Object[] {costX, rX[0], rX[1]});
      }
    }

    cands.sort(
        (a, b) -> Double.compare(((Number) a[0]).doubleValue(), ((Number) b[0]).doubleValue()));

    for (Object[] candidate : cands) {
      float yawW = unwrapYaw(((Number) candidate[1]).floatValue(), serverYaw);
      float pit = ((Number) candidate[2]).floatValue();

      MovingObjectPosition ray = raycastBlock(reach, yawW, pit);
      if (ray == null || ray.typeOfHit != MovingObjectPosition.MovingObjectType.BLOCK) continue;

      if (ray.sideHit == EnumFacing.DOWN) continue;
      if (ray.sideHit == EnumFacing.UP && !underPlayer) continue;
      if (!ray.getBlockPos().equals(bp)) continue;

      // Check if the adjacent cell is placeable
      BlockPos placeCell = bp.offset(ray.sideHit);
      Block placeBlock = BlockUtil.getBlock(placeCell);
      if (!(placeBlock instanceof BlockAir) && !canPlaceThrough(placeBlock)) continue;

      return new Object[] {ray, yawW, pit};
    }

    return null;
  }

  // ══════════════════════════════════════════════════════════════════════════
  //  Item / inventory
  // ══════════════════════════════════════════════════════════════════════════

  /**
   * Pick the cheapest block from hotbar (right-to-left, highest score). Higher score = more
   * disposable = prefer to use first.
   */
  private int pickBlockSlot() {
    int best = -1;
    int bestScore = Integer.MIN_VALUE;

    for (int slot = 8; slot >= 0; --slot) {
      ItemStack s = mc.thePlayer.inventory.getStackInSlot(slot);
      if (s == null || s.stackSize == 0) continue;
      if (!(s.getItem() instanceof ItemBlock)) continue;

      Block block = ((ItemBlock) s.getItem()).getBlock();
      String blockName = block.getUnlocalizedName().replace("tile.", "");
      Integer score = BLOCK_SCORE.get(blockName);
      if (score == null) continue;

      if (score > bestScore) {
        bestScore = score;
        best = slot;
      }
    }

    return best;
  }

  private void equipPlannedSlot() {
    int cur = mc.thePlayer.inventory.currentItem;
    if (plannedSlot != -1 && plannedSlot != cur) {
      mc.thePlayer.inventory.currentItem = plannedSlot;
      slotWasSwapped = true;
    }
  }

  private ItemStack inventorySlotStack(int slot) {
    if (slot < 0 || slot > 8) return null;
    return mc.thePlayer.inventory.getStackInSlot(slot);
  }

  // ══════════════════════════════════════════════════════════════════════════
  //  Smooth rotation helpers
  // ══════════════════════════════════════════════════════════════════════════

  private Float[] getRotationsSmoothed(float targetYaw, float targetPitch, boolean snapback) {
    float curYaw = serverYaw;
    float curPitch = serverPitch;

    float dYaw = targetYaw - curYaw;
    float dPit = targetPitch - curPitch;

    if (Math.abs(dYaw) < 0.1f) curYaw = targetYaw;
    if (Math.abs(dPit) < 0.1f) curPitch = targetPitch;
    if (curYaw == targetYaw && curPitch == targetPitch) {
      return new Float[] {curYaw, curPitch};
    }

    float maxStep = snapback ? snapbackSpeed.getValue() : speed.getValue();
    float random = 20f;

    if (random > 0f) {
      float factor = 1f - (float) (Math.random() * random / 100f);
      maxStep *= factor;
    }

    float totalDelta = Math.abs(dYaw) + Math.abs(dPit);
    if (totalDelta <= maxStep) {
      curYaw = targetYaw;
      curPitch = targetPitch;
    } else {
      float scale = maxStep / totalDelta;
      curYaw += dYaw * scale;
      curPitch += dPit * scale;
    }

    return new Float[] {curYaw, curPitch};
  }

  // ══════════════════════════════════════════════════════════════════════════
  //  Ray casting (block-only)
  // ══════════════════════════════════════════════════════════════════════════

  private MovingObjectPosition raycastBlock(double distance, float yaw, float pitch) {
    Vec3 eyePos = mc.thePlayer.getPositionEyes(1.0f);
    Vec3 lookVec = RayCastUtil.getVectorForRotation(pitch, yaw);
    Vec3 targetPos =
        eyePos.addVector(
            lookVec.xCoord * distance, lookVec.yCoord * distance, lookVec.zCoord * distance);
    return mc.theWorld.rayTraceBlocks(eyePos, targetPos, false, false, true);
  }

  // ══════════════════════════════════════════════════════════════════════════
  //  Math / angle utilities
  // ══════════════════════════════════════════════════════════════════════════

  private static float normYaw(float yaw) {
    yaw = ((yaw % 360f) + 360f) % 360f;
    return (yaw > 180f) ? (yaw - 360f) : yaw;
  }

  private static float wrapYawDelta(float base, float target) {
    float d = target - base;
    while (d <= -180f) d += 360f;
    while (d > 180f) d -= 360f;
    return d;
  }

  private static float unwrapYaw(float yaw, float prevYaw) {
    return prevYaw + ((((yaw - prevYaw + 180f) % 360f) + 360f) % 360f - 180f);
  }

  private static float[] getRotationsWrapped(Vec3 eye, double tx, double ty, double tz) {
    double dx = tx - eye.xCoord;
    double dy = ty - eye.yCoord;
    double dz = tz - eye.zCoord;
    double hd = Math.sqrt(dx * dx + dz * dz);
    float yawWrapped = (float) Math.toDegrees(Math.atan2(dz, dx)) - 90f;
    yawWrapped = normYaw(yawWrapped);
    float pitch = (float) Math.toDegrees(-Math.atan2(dy, hd));
    return new float[] {yawWrapped, pitch};
  }

  private static double clamp(double v, double lo, double hi) {
    return v < lo ? lo : (v > hi ? hi : v);
  }

  private static double distToAABB(Vec3 p, BlockPos bp) {
    double minX = bp.getX(), maxX = bp.getX() + 1;
    double minY = bp.getY(), maxY = bp.getY() + 1;
    double minZ = bp.getZ(), maxZ = bp.getZ() + 1;
    double cx = clamp(p.xCoord, minX, maxX);
    double cy = clamp(p.yCoord, minY, maxY);
    double cz = clamp(p.zCoord, minZ, maxZ);
    double dx = p.xCoord - cx;
    double dy = p.yCoord - cy;
    double dz = p.zCoord - cz;
    return dx * dx + dy * dy + dz * dz;
  }
}
