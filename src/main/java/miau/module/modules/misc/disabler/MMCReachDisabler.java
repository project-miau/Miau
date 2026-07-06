package miau.module.modules.misc.disabler;

import miau.event.impl.PacketEvent;
import miau.event.types.EventType;
import miau.module.modules.misc.Disabler;
import net.minecraft.network.Packet;
import net.minecraft.network.play.client.C0FPacketConfirmTransaction;

/**
 * MMC Reach disabler: transaction rate limiting (only allow every 6 ticks) Ported from OpenRise
 * (Rise 6)
 */
public class MMCReachDisabler extends DisablerMode {

  public MMCReachDisabler(String name, Disabler parent) {
    super(name, parent);
  }

  @Override
  public void onPacket(PacketEvent event) {
    if (event.getType() == EventType.SEND) {
      Packet<?> packet = event.getPacket();

      if (packet instanceof C0FPacketConfirmTransaction) {
        if (mc.thePlayer.ticksExisted % 6 != 0) {
          event.setCancelled(true);
        }
      }
    }
  }
}
