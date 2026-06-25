package myau.clientanticheat.movement.blink;

import java.util.HashMap;
import java.util.Map;
import myau.clientanticheat.CheckBuffer;
import myau.clientanticheat.ClientAntiCheatContext;
import myau.clientanticheat.PlayerCheckData;
import net.minecraft.entity.player.EntityPlayer;

public class MicroBlinkCheck {
  private final Map<String, Integer> frozenTicksMap = new HashMap<>();
  private final Map<String, CheckBuffer> buffers = new HashMap<>();

  public void check(EntityPlayer player, PlayerCheckData data, ClientAntiCheatContext context) {
    String name = player.getName();
    if (name == null || data == null) return;
    CheckBuffer buffer = this.buffers.computeIfAbsent(name, key -> new CheckBuffer());

    if (isExempt(player, data)) {
      this.frozenTicksMap.remove(name);
      buffer.decay(0.1D);
      return;
    }

    boolean frozen = data.totalDelta < 0.005D && data.yawDelta < 0.05F && data.pitchDelta < 0.05F;
    if (frozen) {
      this.frozenTicksMap.put(name, this.frozenTicksMap.getOrDefault(name, 0) + 1);
      buffer.decay(0.01D);
      return;
    }

    int frozenTicks = this.frozenTicksMap.getOrDefault(name, 0);
    this.frozenTicksMap.remove(name);

    boolean catchUpBurst = data.totalDelta > 0.4D && data.totalDelta < 8.0D;

    if (catchUpBurst && frozenTicks >= 2 && frozenTicks <= 20 && data.startedSwinging()) {
      if (buffer.flag(1.5D, 4.0D)) {
        context.receiveSignal(name, "LagRange / MicroBlink");
        buffer.reset();
      }
    } else {
      buffer.decay(0.1D);
    }
  }

  private boolean isExempt(EntityPlayer player, PlayerCheckData data) {
    return player.isDead
        || player.ticksExisted < 40
        || data.recentlyTeleported()
        || player.isInWater()
        || player.isInLava();
  }

  public void reset() {
    this.frozenTicksMap.clear();
    this.buffers.clear();
  }
}
