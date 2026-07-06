package miau.module.modules.misc.disabler;

import miau.event.impl.StrafeEvent;
import miau.module.modules.misc.Disabler;
import miau.util.network.PacketUtil;
import net.minecraft.entity.player.PlayerCapabilities;
import net.minecraft.network.play.client.C13PacketPlayerAbilities;

/**
 * Abilities disabler: sends flying abilities packets to confuse anti-cheat Ported from OpenRise
 * (Rise 6)
 */
public class AbilitiesDisabler extends DisablerMode {

  public AbilitiesDisabler(String name, Disabler parent) {
    super(name, parent);
  }

  @Override
  public void onStrafe(StrafeEvent event) {
    if (mc.thePlayer == null || mc.theWorld == null) return;

    if (mc.thePlayer.ticksExisted % 5 == 0) {
      PlayerCapabilities caps = new PlayerCapabilities();
      caps.isFlying = true;
      PacketUtil.sendPacketNoEvent(new C13PacketPlayerAbilities(caps));
    }
  }
}
