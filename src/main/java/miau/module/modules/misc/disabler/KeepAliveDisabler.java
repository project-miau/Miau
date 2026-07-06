package miau.module.modules.misc.disabler;

import miau.event.impl.PacketEvent;
import miau.event.types.EventType;
import miau.module.modules.misc.Disabler;
import net.minecraft.network.play.client.C00PacketKeepAlive;

/** KeepAlive disabler: cancels all C00 (keep alive) packets Ported from OpenRise (Rise 6) */
public class KeepAliveDisabler extends DisablerMode {

  public KeepAliveDisabler(String name, Disabler parent) {
    super(name, parent);
  }

  @Override
  public void onPacket(PacketEvent event) {
    if (event.getType() == EventType.SEND) {
      if (event.getPacket() instanceof C00PacketKeepAlive) {
        event.setCancelled(true);
      }
    }
  }
}
