package myau.clientanticheat.player.scaffold;

import java.util.HashMap;
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
  private final Map<String, Long> lastFlag = new HashMap<>();

  public void check(
      EntityPlayer player, World world, PlayerCheckData data, ClientAntiCheatContext context) {
    String name = player.getName();
    if (name == null || data == null) return;

    CheckBuffer flickBuffer = this.flickBuffers.computeIfAbsent(name, key -> new CheckBuffer());
    CheckBuffer snapBuffer = this.snapBuffers.computeIfAbsent(name, key -> new CheckBuffer());

    ItemStack held = player.getHeldItem();
    boolean holdingBlock = held != null && held.getItem() instanceof ItemBlock;
    if (!holdingBlock || this.isExempt(player, data)) {
      flickBuffer.decay(0.5D);
      snapBuffer.decay(0.5D);
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

    boolean failed = flickBuffer.get() > 4.0D || snapBuffer.get() > 3.0D;
    if (failed) {
      long now = System.currentTimeMillis();
      long last = this.lastFlag.getOrDefault(name, 0L);
      if (now - last > 2500L) {
        if (flickBuffer.get() > 4.0D) context.receiveSignal(name, "Scaffold (Rotation Flick)");
        else if (snapBuffer.get() > 3.0D) context.receiveSignal(name, "Scaffold (Angle Snap)");
        this.lastFlag.put(name, now);
        flickBuffer.reset();
        snapBuffer.reset();
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
    this.lastFlag.clear();
  }
}
