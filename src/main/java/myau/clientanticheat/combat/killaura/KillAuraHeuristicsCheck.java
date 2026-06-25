package myau.clientanticheat.combat.killaura;

import java.util.HashMap;
import java.util.Map;
import myau.clientanticheat.CheckBuffer;
import myau.clientanticheat.ClientAntiCheatContext;
import myau.clientanticheat.PlayerCheckData;
import net.minecraft.entity.player.EntityPlayer;

public class KillAuraHeuristicsCheck {
  private final Map<String, CheckBuffer> constantAimBuffers = new HashMap<>();
  private final Map<String, CheckBuffer> moduloBuffers = new HashMap<>();

  public void check(EntityPlayer player, PlayerCheckData data, ClientAntiCheatContext context) {
    String name = player.getName();
    if (name == null || data == null || data.recentlyTeleported()) return;

    if (!data.startedSwinging()) {
      this.decay(name);
      return;
    }

    CheckBuffer constantAimBuffer =
        this.constantAimBuffers.computeIfAbsent(name, key -> new CheckBuffer());
    CheckBuffer moduloBuffer = this.moduloBuffers.computeIfAbsent(name, key -> new CheckBuffer());

    float deltaYaw = data.yawDelta;
    float accelYaw = Math.abs(data.yawAcceleration);

    if (deltaYaw > 2.0F && accelYaw < 0.001F) {
      constantAimBuffer.flag(1.0D, 5.0D);
    } else {
      constantAimBuffer.decay(0.1D);
    }

    float divisorX = deltaYaw % 1.5F;
    float divisorY = data.pitchDelta % 1.5F;
    float divisorGcdX = deltaYaw % 0.05F;
    float divisorGcdY = data.pitchDelta % 0.05F;

    if (deltaYaw > 5.0F && (divisorX == 0.0F || divisorY == 0.0F)) {
      moduloBuffer.flag(1.0D, 4.0D);
    } else if (deltaYaw > 2.0F && divisorGcdX == 0.0F && divisorGcdY == 0.0F) {
      moduloBuffer.flag(0.5D, 4.0D);
    } else {
      moduloBuffer.decay(0.2D);
    }

    if (constantAimBuffer.get() > 3.0D) {
      context.receiveSignal(name, "KillAura (Constant Aim)");
      constantAimBuffer.reset();
    }
    if (moduloBuffer.get() > 3.0D) {
      context.receiveSignal(name, "KillAura (Modulo)");
      moduloBuffer.reset();
    }
  }

  public void decay(String name) {
    CheckBuffer constantAim = this.constantAimBuffers.get(name);
    if (constantAim != null) constantAim.decay(0.15D);
    CheckBuffer modulo = this.moduloBuffers.get(name);
    if (modulo != null) modulo.decay(0.15D);
  }

  public void reset() {
    this.constantAimBuffers.clear();
    this.moduloBuffers.clear();
  }
}
