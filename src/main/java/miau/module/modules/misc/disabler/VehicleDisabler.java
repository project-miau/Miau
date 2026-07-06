package miau.module.modules.misc.disabler;

import miau.event.impl.TickEvent;
import miau.event.types.EventType;
import miau.module.modules.misc.Disabler;
import miau.util.network.PacketUtil;
import net.minecraft.network.play.client.C0CPacketInput;

/**
 * Vehicle disabler: send vehicle input packets to bypass vehicle checks Ported from OpenRise (Rise
 * 6)
 */
public class VehicleDisabler extends DisablerMode {

  public VehicleDisabler(String name, Disabler parent) {
    super(name, parent);
  }

  @Override
  public void onTick(TickEvent event) {
    if (event.getType() != EventType.PRE) return;
    if (mc.thePlayer == null || mc.theWorld == null) return;

    PacketUtil.sendPacketNoEvent(new C0CPacketInput());
  }
}
