package miau.module.modules.misc.disabler;

import miau.event.impl.StrafeEvent;
import miau.module.modules.misc.Disabler;
import miau.util.network.PacketUtil;
import net.minecraft.network.play.client.C18PacketSpectate;

/**
 * Spectate disabler: sends spectate self packets to confuse anti-cheat Ported from OpenRise (Rise
 * 6)
 */
public class SpectateDisabler extends DisablerMode {

  public SpectateDisabler(String name, Disabler parent) {
    super(name, parent);
  }

  @Override
  public void onStrafe(StrafeEvent event) {
    if (mc.thePlayer == null || mc.theWorld == null) return;

    PacketUtil.sendPacketNoEvent(new C18PacketSpectate(mc.thePlayer.getUniqueID()));
  }
}
