package myau.module.modules.movement.nofalls;

import myau.event.impl.PacketEvent;
import myau.event.types.EventType;
import myau.mixin.IAccessorC03PacketPlayer;
import myau.module.modules.movement.NoFall;
import net.minecraft.network.play.client.C03PacketPlayer;

public class NoGroundNoFall extends NoFallMode {
  public NoGroundNoFall(String name, NoFall parent) {
    super(name, parent);
  }

  @Override
  public void onPacket(PacketEvent event) {
    if (event.getType() == EventType.SEND && event.getPacket() instanceof C03PacketPlayer) {
      ((IAccessorC03PacketPlayer) event.getPacket()).setOnGround(false);
    }
  }
}
