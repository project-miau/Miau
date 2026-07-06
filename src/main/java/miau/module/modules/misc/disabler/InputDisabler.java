package miau.module.modules.misc.disabler;

import miau.event.impl.StrafeEvent;
import miau.module.modules.misc.Disabler;
import miau.util.network.PacketUtil;
import net.minecraft.network.play.client.C0CPacketInput;

/** Input disabler: sends C0C input packets with spoofed values Ported from OpenRise (Rise 6) */
public class InputDisabler extends DisablerMode {

  public InputDisabler(String name, Disabler parent) {
    super(name, parent);
  }

  @Override
  public void onStrafe(StrafeEvent event) {
    if (mc.thePlayer == null || mc.theWorld == null) return;

    PacketUtil.sendPacketNoEvent(new C0CPacketInput(0.98f, 0, true, true));
  }
}
