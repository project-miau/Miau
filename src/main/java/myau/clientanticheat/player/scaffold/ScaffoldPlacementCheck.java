package myau.clientanticheat.player.scaffold;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;
import myau.clientanticheat.CheckBuffer;
import myau.clientanticheat.ClientAntiCheatContext;
import myau.clientanticheat.PlayerCheckData;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.util.BlockPos;
import net.minecraft.util.MathHelper;
import net.minecraft.world.World;

public class ScaffoldPlacementCheck {
  private final Map<String, CheckBuffer> flickBuffers = new HashMap<>();
  private final Map<String, CheckBuffer> snapBuffers = new HashMap<>();
  private final Map<String, CheckBuffer> microPitchBuffers = new HashMap<>();
  private final Map<String, CheckBuffer> hitboxSnapBuffers = new HashMap<>();
  private final Map<String, Long> lastFlag = new HashMap<>();
  private final Map<String, Float> lastPitch = new HashMap<>();
  private final Map<String, LinkedList<BlockPos>> lastBlocksPlaced = new HashMap<>();
  private final Map<String, Double> micropicthVl = new HashMap<>();
  private final Map<String, LinkedList<Long>> placeSpeedHistory = new HashMap<>();
  private final Map<String, Long> lastPlaceTime = new HashMap<>();
  private final Map<String, Long> lastHardFaultClick = new HashMap<>();

  private static final int ROTATION_FLICK_HISTORY = 8;

  public void check(
      EntityPlayer player, World world, PlayerCheckData data, ClientAntiCheatContext context) {
    String name = player.getName();
    if (name == null || data == null) return;

    CheckBuffer flickBuffer = this.flickBuffers.computeIfAbsent(name, key -> new CheckBuffer());
    CheckBuffer snapBuffer = this.snapBuffers.computeIfAbsent(name, key -> new CheckBuffer());
    CheckBuffer microPitchBuffer =
        this.microPitchBuffers.computeIfAbsent(name, key -> new CheckBuffer());
    CheckBuffer hitboxSnapBuffer =
        this.hitboxSnapBuffers.computeIfAbsent(name, key -> new CheckBuffer());

    ItemStack held = player.getHeldItem();
    boolean holdingBlock = held != null && held.getItem() instanceof ItemBlock;
    if (!holdingBlock || this.isExempt(player, data)) {
      flickBuffer.decay(0.5D);
      snapBuffer.decay(0.5D);
      microPitchBuffer.decay(0.3D);
      hitboxSnapBuffer.decay(0.3D);
      return;
    }

    boolean moving = data.horizontalDelta > 0.12D;
    boolean hasBlockBelow = this.hasSolidBelow(player, world, 1.0D);
    boolean hasRecentSupport = this.hasSolidBelow(player, world, 1.35D);
    boolean nearEdge =
        player.onGround && !this.hasSolidBelowOffset(player, world, data.deltaX, data.deltaZ);
    boolean bridgeContext =
        moving && (nearEdge || !hasBlockBelow || hasRecentSupport && data.pitch > 55.0F);

    if (bridgeContext && data.pitchDelta > 20.0F && Math.abs(data.pitchAcceleration) > 15.0F) {
      flickBuffer.flag(1.25D, 999.0D);
    } else {
      flickBuffer.decay(0.2D);
    }

    float divisorY = data.pitchDelta % 1.5F;
    if (bridgeContext && data.pitchDelta > 2.0F && divisorY == 0.0F) {
      snapBuffer.flag(1.5D, 999.0D);
    } else {
      snapBuffer.decay(0.25D);
    }

    float prevPitch = this.lastPitch.getOrDefault(name, data.pitch);
    this.lastPitch.put(name, data.pitch);
    float pitchDiff = Math.abs(data.pitch - prevPitch);

    LinkedList<Long> placeSpeedHist =
        this.placeSpeedHistory.computeIfAbsent(name, k -> new LinkedList<>());
    long now = System.currentTimeMillis();
    long lastPlace = this.lastPlaceTime.getOrDefault(name, 0L);
    long placeInterval = lastPlace > 0 ? now - lastPlace : 1000L;

    if (data.startedSwinging() && bridgeContext) {
      this.lastPlaceTime.put(name, now);
      placeSpeedHist.addFirst(placeInterval);
      if (placeSpeedHist.size() > ROTATION_FLICK_HISTORY) placeSpeedHist.removeLast();
    }

    if (placeSpeedHist.size() >= ROTATION_FLICK_HISTORY) {
      double avg = 0;
      for (long v : placeSpeedHist) avg += v;
      avg /= placeSpeedHist.size();

      boolean inOneLine = isOneLine(this.lastBlocksPlaced.get(name));
      double vl = this.micropicthVl.getOrDefault(name, 0.0D);

      if (pitchDiff > 3
          && pitchDiff < 20
          && data.pitch > 70
          && avg < 400
          && (inOneLine || placeInterval < 800)) {
        vl += Math.min(20, pitchDiff / 0.5);
        if (data.pitch > 89.5F) vl += 5;
        if (vl > 100) {
          microPitchBuffer.flag(2.0D, 999.0D);
          vl -= 10;
        }
      } else if (vl > 0) {
        vl *= 0.99;
        vl -= 0.01;
      }
      this.micropicthVl.put(name, vl);
    }

    if (data.startedSwinging() && bridgeContext) {
      LinkedList<BlockPos> blockHistory =
          this.lastBlocksPlaced.computeIfAbsent(name, k -> new LinkedList<>());
      BlockPos pos =
          new BlockPos(
              MathHelper.floor_double(player.posX + data.deltaX * 2),
              MathHelper.floor_double(player.posY - 1),
              MathHelper.floor_double(player.posZ + data.deltaZ * 2));
      blockHistory.addFirst(pos);
      if (blockHistory.size() > 10) blockHistory.removeLast();

      if (blockHistory.size() >= 3 && isOneLine(blockHistory)) {

        if (data.pitchDelta < 0.5F && data.pitch > 60.0F && data.pitch < 100.0F) {
          hitboxSnapBuffer.flag(1.0D, 999.0D);
        } else {
          hitboxSnapBuffer.decay(0.15D);
        }
      }
    }

    boolean failed =
        flickBuffer.get() > 4.0D
            || snapBuffer.get() > 3.0D
            || microPitchBuffer.get() > 3.0D
            || hitboxSnapBuffer.get() > 5.0D;
    if (failed) {
      long flagNow = System.currentTimeMillis();
      long last = this.lastFlag.getOrDefault(name, 0L);
      if (flagNow - last > 2500L) {
        if (flickBuffer.get() > 4.0D) context.receiveSignal(name, "Scaffold (Rotation Flick)");
        else if (snapBuffer.get() > 3.0D) context.receiveSignal(name, "Scaffold (Angle Snap)");
        else if (microPitchBuffer.get() > 3.0D)
          context.receiveSignal(name, "Scaffold (Micro Pitch)");
        else if (hitboxSnapBuffer.get() > 5.0D)
          context.receiveSignal(name, "Scaffold (Hitbox Snap)");
        this.lastFlag.put(name, flagNow);
        flickBuffer.reset();
        snapBuffer.reset();
        microPitchBuffer.reset();
        hitboxSnapBuffer.reset();
      }
    }
  }

  private boolean isExempt(EntityPlayer player, PlayerCheckData data) {
    return player.isInWater()
        || player.isInLava()
        || player.isOnLadder()
        || player.isRiding()
        || player.capabilities.isFlying
        || data.recentlyTeleported();
  }

  private boolean isOneLine(LinkedList<BlockPos> blocks) {
    if (blocks == null || blocks.size() < 3) return false;
    int lastX = 0, lastY = 0, lastZ = 0;
    boolean lockedX = false, lockedZ = false, first = true;
    for (BlockPos pos : blocks) {
      if (!first) {
        if (lastY != pos.getY()) return false;
        if (lastX == pos.getX()) lockedX = true;
        else if (lockedX) return false;
        if (lastZ == pos.getZ()) lockedZ = true;
        else if (lockedZ) return false;
      }
      lastX = pos.getX();
      lastY = pos.getY();
      lastZ = pos.getZ();
      first = false;
    }
    return lockedX || lockedZ;
  }

  private boolean hasSolidBelow(EntityPlayer player, World world, double below) {
    for (double xOffset = -0.3D; xOffset <= 0.3D; xOffset += 0.3D) {
      for (double zOffset = -0.3D; zOffset <= 0.3D; zOffset += 0.3D) {
        BlockPos pos =
            new BlockPos(
                MathHelper.floor_double(player.posX + xOffset),
                MathHelper.floor_double(player.posY - below),
                MathHelper.floor_double(player.posZ + zOffset));
        if (!world.isAirBlock(pos)) return true;
      }
    }
    return false;
  }

  private boolean hasSolidBelowOffset(
      EntityPlayer player, World world, double motionX, double motionZ) {
    BlockPos pos =
        new BlockPos(
            MathHelper.floor_double(player.posX + motionX * 2.0D),
            MathHelper.floor_double(player.posY - 1.0D),
            MathHelper.floor_double(player.posZ + motionZ * 2.0D));
    return !world.isAirBlock(pos);
  }

  public void reset() {
    this.flickBuffers.clear();
    this.snapBuffers.clear();
    this.microPitchBuffers.clear();
    this.hitboxSnapBuffers.clear();
    this.lastFlag.clear();
    this.lastPitch.clear();
    this.lastBlocksPlaced.clear();
    this.micropicthVl.clear();
    this.placeSpeedHistory.clear();
    this.lastPlaceTime.clear();
    this.lastHardFaultClick.clear();
  }
}
