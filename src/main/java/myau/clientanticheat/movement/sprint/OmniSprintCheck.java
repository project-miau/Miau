package myau.clientanticheat.movement.sprint;

import java.util.HashMap;
import java.util.Map;
import myau.clientanticheat.CheckBuffer;
import myau.clientanticheat.ClientAntiCheatContext;
import myau.clientanticheat.PlayerCheckData;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.MathHelper;

public class OmniSprintCheck {
  private final Map<String, CheckBuffer> buffers = new HashMap<>();

  public void check(EntityPlayer player, PlayerCheckData data, ClientAntiCheatContext context) {
    String name = player.getName();
    if (name == null || data == null) return;

    CheckBuffer buffer = this.buffers.computeIfAbsent(name, key -> new CheckBuffer());

    if (isExempt(player, data)) {
      buffer.decay(0.5D);
      return;
    }

    if (player.isSprinting() && data.horizontalDelta > 0.1D) {
      float velocityYaw =
          (float)
                  (MathHelper.atan2(player.posZ - player.prevPosZ, player.posX - player.prevPosX)
                      * 180.0D
                      / Math.PI)
              - 90.0F;
      float playerYaw = player.rotationYaw;

      float diff = Math.abs(MathHelper.wrapAngleTo180_float(velocityYaw - playerYaw));

      if (diff > 75.0F) {
        if (buffer.flag(1.0D, 4.0D)) {
          context.receiveSignal(name, "OmniSprint");
          buffer.reset();
        }
      } else {
        buffer.decay(0.2D);
      }
    } else {
      buffer.decay(0.4D);
    }
  }

  private boolean isExempt(EntityPlayer player, PlayerCheckData data) {
    return player.isDead
        || player.hurtTime > 0
        || player.ticksExisted < 40
        || data.recentlyTeleported()
        || player.isInWater()
        || player.isInLava()
        || player.isOnLadder()
        || player.isRiding()
        || player.capabilities.isFlying;
  }

  public void reset() {
    this.buffers.clear();
  }
}
