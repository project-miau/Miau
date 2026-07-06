package miau.module.modules.misc.disabler;

import miau.event.impl.PacketEvent;
import miau.event.impl.TickEvent;
import miau.event.types.EventType;
import miau.mixin.IAccessorC03PacketPlayer;
import miau.module.modules.misc.Disabler;
import miau.util.network.PacketUtil;
import net.minecraft.network.Packet;
import net.minecraft.network.play.client.C03PacketPlayer;
import net.minecraft.network.play.client.C0CPacketInput;

/** Ghostly disabler: ground flag spoof + input packet spam Ported from OpenRise (Rise 6) */
public class GhostlyDisabler extends DisablerMode {

  public GhostlyDisabler(String name, Disabler parent) {
    super(name, parent);
  }

  @Override
  public void onTick(TickEvent event) {
    if (event.getType() != EventType.PRE) return;
    if (mc.thePlayer == null || mc.theWorld == null) return;

    mc.thePlayer.setSprinting(false);
  }

  @Override
  public void onPacket(PacketEvent event) {
    if (event.getType() == EventType.SEND) {
      Packet<?> packet = event.getPacket();

      if (packet instanceof C03PacketPlayer) {
        PacketUtil.sendPacketNoEvent(new C0CPacketInput());
        ((IAccessorC03PacketPlayer) packet).setOnGround(true);
      }
    }
  }
}
