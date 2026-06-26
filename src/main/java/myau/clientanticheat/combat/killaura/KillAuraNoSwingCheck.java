package myau.clientanticheat.combat.killaura;

import java.util.HashMap;
import java.util.Map;
import myau.clientanticheat.CheckBuffer;
import myau.clientanticheat.ClientAntiCheatContext;
import myau.clientanticheat.PlayerCheckData;
import net.minecraft.entity.player.EntityPlayer;

public class KillAuraNoSwingCheck {
  private final Map<String, CheckBuffer> buffers = new HashMap<>();
  private final Map<String, Integer> lastTargetHurtTime = new HashMap<>();

  public void check(EntityPlayer player, PlayerCheckData data, ClientAntiCheatContext context) {
    String name = player.getName();
    if (name == null || data == null || data.recentlyTeleported()) return;

    CheckBuffer buffer = this.buffers.computeIfAbsent(name, key -> new CheckBuffer());

    if (data.nearestTarget != null && data.nearestTargetDistance < 4.5D) {
      EntityPlayer target = data.nearestTarget;
      int prevHurtTime = this.lastTargetHurtTime.getOrDefault(name, 0);
      this.lastTargetHurtTime.put(name, target.hurtTime);

      boolean targetJustHurt = prevHurtTime == 0 && target.hurtTime > 0;

      if (targetJustHurt) {
        boolean noSwing =
            player.swingProgress == 0.0F && player.prevSwingProgress == 0.0F && !data.swinging;

        if (noSwing) {
          if (buffer.flag(2.0D, 4.0D)) {
            context.receiveSignal(name, "KillAura (No Swing)");
            buffer.reset();
          }
        } else {
          buffer.decay(0.5D);
        }
      }
    } else {
      buffer.decay(0.1D);
    }
  }

  public void reset() {
    this.buffers.clear();
    this.lastTargetHurtTime.clear();
  }
}
