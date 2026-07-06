package miau.module.modules.misc.disabler;

import miau.event.impl.TickEvent;
import miau.event.types.EventType;
import miau.module.modules.misc.Disabler;
import miau.util.network.PacketUtil;
import net.minecraft.network.play.client.C08PacketPlayerBlockPlacement;

/**
 * Block disabler: sends block placement packets to confuse anti-cheat Ported from OpenRise (Rise 6)
 */
public class BlockDisabler extends DisablerMode {

  public BlockDisabler(String name, Disabler parent) {
    super(name, parent);
  }

  @Override
  public void onTick(TickEvent event) {
    if (event.getType() != EventType.PRE) return;
    if (mc.thePlayer == null || mc.theWorld == null) return;
    if (mc.currentScreen != null) return;

    PacketUtil.sendPacketNoEvent(new C08PacketPlayerBlockPlacement(mc.thePlayer.getHeldItem()));
  }
}
