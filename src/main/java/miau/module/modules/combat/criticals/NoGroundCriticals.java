package miau.module.modules.combat.criticals;

import miau.event.impl.PacketEvent;
import miau.event.types.EventType;
import miau.mixin.IAccessorC03PacketPlayer;
import miau.module.modules.combat.Criticals;
import net.minecraft.network.play.client.C03PacketPlayer;

public class NoGroundCriticals extends CriticalsMode {
  public NoGroundCriticals(String name, Criticals parent) {
    super(name, parent);
  }

  @Override
  public void onPacket(PacketEvent event) {
    if (event.getType() == EventType.SEND) {
      if (event.getPacket() instanceof C03PacketPlayer) {
        ((IAccessorC03PacketPlayer) event.getPacket()).setOnGround(false);
      }
    }
  }
}
