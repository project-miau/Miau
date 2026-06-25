package myau.clientanticheat.movement.blink;

import java.util.HashMap;
import java.util.Map;
import myau.clientanticheat.CheckBuffer;
import myau.clientanticheat.ClientAntiCheatContext;
import myau.clientanticheat.PlayerCheckData;
import net.minecraft.entity.player.EntityPlayer;

public class FakeLagCheck {
  private final Map<String, CheckBuffer> pulseBuffers = new HashMap<>();

  public void check(EntityPlayer player, PlayerCheckData data, ClientAntiCheatContext context) {
    String name = player.getName();
    if (name == null || data == null) return;
    CheckBuffer buffer = this.pulseBuffers.computeIfAbsent(name, key -> new CheckBuffer());

    if (isExempt(player, data)) {
      buffer.decay(0.5D);
      return;
    }

    boolean pulseFluctuation =
        data.horizontalDelta > 1.25D
            && data.lastHorizontalDelta < 0.05D
            && data.horizontalDelta < 8.0D;

    if (pulseFluctuation && !player.isCollidedHorizontally && data.airTicks < 8) {
      if (buffer.flag(1.0D, 3.5D)) {
        context.receiveSignal(name, "FakeLag");
        buffer.reset();
      }
    } else {
      buffer.decay(0.2D);
    }
  }

  private boolean isExempt(EntityPlayer player, PlayerCheckData data) {
    return player.isDead
        || player.ticksExisted < 40
        || data.recentlyTeleported()
        || player.isInWater()
        || player.isInLava()
        || player.isOnLadder()
        || player.isRiding()
        || player.capabilities.isFlying;
  }

  public void reset() {
    this.pulseBuffers.clear();
  }
}
