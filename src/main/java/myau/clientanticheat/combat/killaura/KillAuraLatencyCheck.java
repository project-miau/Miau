package myau.clientanticheat.combat.killaura;

import java.util.HashMap;
import java.util.Map;
import myau.clientanticheat.CheckBuffer;
import myau.clientanticheat.ClientAntiCheatContext;
import myau.clientanticheat.PlayerCheckData;
import net.minecraft.entity.player.EntityPlayer;

public class KillAuraLatencyCheck {
  private final Map<String, CheckBuffer> buffers = new HashMap<>();

  public void check(EntityPlayer player, PlayerCheckData data, ClientAntiCheatContext context) {
    String name = player.getName();
    if (name == null || data == null || data.recentlyTeleported()) return;

    CheckBuffer buffer = this.buffers.computeIfAbsent(name, key -> new CheckBuffer());

    boolean swingingDuringFreeze =
        data.startedSwinging() && data.totalDelta < 0.005D && data.yawDelta > 0.0F;
    boolean rapidSwingingBurst =
        data.startedSwinging() && data.burstTicks > 0 && data.totalDelta > 0.8D;

    if (swingingDuringFreeze) {
      if (buffer.flag(1.0D, 4.0D)) {
        context.receiveSignal(name, "KillAura (Latency Alignment)");
        buffer.reset();
      }
    } else if (rapidSwingingBurst) {
      if (buffer.flag(1.5D, 5.0D)) {
        context.receiveSignal(name, "KillAura (Burst Latency)");
        buffer.reset();
      }
    } else {
      buffer.decay(0.1D);
    }
  }

  public void reset() {
    this.buffers.clear();
  }
}
