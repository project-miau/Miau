package myau.module.modules.combat.criticals;

import myau.event.impl.PacketEvent;
import myau.event.types.EventType;
import myau.mixin.IAccessorC03PacketPlayer;
import myau.module.modules.combat.Criticals;
import net.minecraft.network.play.client.C03PacketPlayer;

public class VulcanCriticals extends CriticalsMode {
  public VulcanCriticals(String name, Criticals parent) {
    super(name, parent);
  }

  @Override
  public void onPacket(PacketEvent event) {
    if (event.getType() == EventType.SEND) {
      if (event.getPacket() instanceof C03PacketPlayer) {
        if (myau.util.player.PlayerTracker.fallDistance < 1.8
            && myau.util.player.PlayerTracker.ticksSinceVelocity <= 18) {
          ((IAccessorC03PacketPlayer) event.getPacket()).setOnGround(false);
        }
      }
    }
  }
}
