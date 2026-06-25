package myau.clientanticheat.combat.killaura;

import java.util.HashMap;
import java.util.Map;
import myau.clientanticheat.CheckBuffer;
import myau.clientanticheat.ClientAntiCheatContext;
import myau.clientanticheat.PlayerCheckData;
import net.minecraft.entity.player.EntityPlayer;

public class KillAuraAngleSnap {
  private final Map<String, CheckBuffer> snapBuffers = new HashMap<>();
  private final Map<String, CheckBuffer> flickBuffers = new HashMap<>();

  public void check(EntityPlayer player, PlayerCheckData data, ClientAntiCheatContext context) {
    String name = player.getName();
    if (name == null || data == null || data.recentlyTeleported()) return;

    if (!data.startedSwinging()) {
      this.decay(name);
      return;
    }

    CheckBuffer snapBuffer = this.snapBuffers.computeIfAbsent(name, key -> new CheckBuffer());
    CheckBuffer flickBuffer = this.flickBuffers.computeIfAbsent(name, key -> new CheckBuffer());

    if (data.yawDelta > 95.0F || data.yawAcceleration > 65.0F) {
      snapBuffer.flag(1.0D, 999.0D);
    } else {
      snapBuffer.decay(0.3D);
    }

    float divisorX = data.yawDelta % 1.5F;
    float divisorY = data.pitchDelta % 1.5F;
    if (data.yawDelta > 5.0F && divisorX == 0.0F && divisorY == 0.0F) {
      flickBuffer.flag(1.0D, 5.0D);
    } else {
      flickBuffer.decay(0.25D);
    }

    if (snapBuffer.get() > 3.0D) {
      context.receiveSignal(name, "KillAura (Snap)");
      snapBuffer.reset();
    }
    if (flickBuffer.get() > 3.0D) {
      context.receiveSignal(name, "KillAura (Rotation Flick)");
      flickBuffer.reset();
    }
  }

  public void decay(String name) {
    CheckBuffer snap = this.snapBuffers.get(name);
    if (snap != null) snap.decay(0.1D);
    CheckBuffer flick = this.flickBuffers.get(name);
    if (flick != null) flick.decay(0.15D);
  }

  public void reset() {
    this.snapBuffers.clear();
    this.flickBuffers.clear();
  }
}
