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

public class ScaffoldRotationCheck {
  private final Map<String, CheckBuffer> stabilityBuffers = new HashMap<>();
  private final Map<String, CheckBuffer> speedBuffers = new HashMap<>();

  public void check(
      EntityPlayer player, World world, PlayerCheckData data, ClientAntiCheatContext context) {
    String name = player.getName();
    if (name == null || data == null) return;

    CheckBuffer stabilityBuffer =
        this.stabilityBuffers.computeIfAbsent(name, key -> new CheckBuffer());
    CheckBuffer speedBuffer = this.speedBuffers.computeIfAbsent(name, key -> new CheckBuffer());

    ItemStack held = player.getHeldItem();
    boolean holdingBlock = held != null && held.getItem() instanceof ItemBlock;
    if (!holdingBlock || this.isExempt(player, data)) {
      stabilityBuffer.decay(0.5D);
      speedBuffer.decay(0.5D);
      return;
    }

    boolean moving = data.horizontalDelta > 0.12D;
    boolean hasBlockBelow = this.hasSolidBelow(player, world, 1.0D);
    boolean hasRecentSupport = this.hasSolidBelow(player, world, 1.35D);
    boolean nearEdge =
        player.onGround && !this.hasSolidBelowOffset(player, world, data.deltaX, data.deltaZ);
    boolean bridgeContext =
        moving && (nearEdge || !hasBlockBelow || hasRecentSupport && data.pitch > 55.0F);

    if (bridgeContext) {
      if (data.pitchDelta > 0.0F && data.pitchDelta < 0.1F && data.yawDelta > 15.0F) {
        stabilityBuffer.flag(1.0D, 4.0D);
      } else {
        stabilityBuffer.decay(0.15D);
      }

      if (data.yawDelta > 120.0F && data.pitchDelta > 45.0F) {
        speedBuffer.flag(1.25D, 5.0D);
      } else {
        speedBuffer.decay(0.2D);
      }
    } else {
      stabilityBuffer.decay(0.4D);
      speedBuffer.decay(0.4D);
    }

    if (stabilityBuffer.get() > 3.0D) {
      context.receiveSignal(name, "Scaffold (Rotation Stability)");
      stabilityBuffer.reset();
    }
    if (speedBuffer.get() > 4.0D) {
      context.receiveSignal(name, "Scaffold (Rotation Speed)");
      speedBuffer.reset();
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
    this.stabilityBuffers.clear();
    this.speedBuffers.clear();
  }
}
