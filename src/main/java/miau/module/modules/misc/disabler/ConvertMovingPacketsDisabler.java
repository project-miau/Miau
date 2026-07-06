package miau.module.modules.misc.disabler;

import miau.event.impl.PacketEvent;
import miau.event.types.EventType;
import miau.mixin.IAccessorC03PacketPlayer;
import miau.module.modules.misc.Disabler;
import net.minecraft.network.play.client.C03PacketPlayer;

/**
 * Convert moving packets: converts C06 (pos/look) to C04 (position only) Ported from OpenRise (Rise
 * 6) No toggle buttons needed - always active when mode selected.
 */
public class ConvertMovingPacketsDisabler extends DisablerMode {

  public ConvertMovingPacketsDisabler(String name, Disabler parent) {
    super(name, parent);
  }

  @Override
  public void onPacket(PacketEvent event) {
    if (event.getType() == EventType.SEND) {
      if (event.getPacket() instanceof C03PacketPlayer.C06PacketPlayerPosLook) {
        C03PacketPlayer.C06PacketPlayerPosLook packet =
            (C03PacketPlayer.C06PacketPlayerPosLook) event.getPacket();
        event.setCancelled(true);
        miau.util.network.PacketUtil.sendPacketNoEvent(
            new C03PacketPlayer.C04PacketPlayerPosition(
                ((IAccessorC03PacketPlayer) packet).getX(),
                ((IAccessorC03PacketPlayer) packet).getY(),
                ((IAccessorC03PacketPlayer) packet).getZ(),
                ((IAccessorC03PacketPlayer) packet).isOnGround()));
      }
    }
  }
}
