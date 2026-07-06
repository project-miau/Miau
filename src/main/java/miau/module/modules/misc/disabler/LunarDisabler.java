package miau.module.modules.misc.disabler;

import miau.event.impl.PacketEvent;
import miau.event.impl.TickEvent;
import miau.event.types.EventType;
import miau.mixin.IAccessorC03PacketPlayer;
import miau.module.modules.misc.Disabler;
import miau.util.network.PacketUtil;
import net.minecraft.network.Packet;
import net.minecraft.network.play.client.C00PacketKeepAlive;
import net.minecraft.network.play.client.C03PacketPlayer;
import net.minecraft.network.play.client.C0FPacketConfirmTransaction;
import org.apache.commons.lang3.RandomUtils;

/**
 * Lunar disabler: position packet rotation spoof, cancels transaction/keepalive Ported from
 * OpenRise (Rise 6)
 */
public class LunarDisabler extends DisablerMode {

  public LunarDisabler(String name, Disabler parent) {
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

      // Cancel keepalive and transactions
      if (packet instanceof C0FPacketConfirmTransaction || packet instanceof C00PacketKeepAlive) {
        event.setCancelled(true);
      }

      // Spoof rotations on position packets
      if (packet instanceof C03PacketPlayer.C04PacketPlayerPosition) {
        C03PacketPlayer.C04PacketPlayerPosition posPacket =
            (C03PacketPlayer.C04PacketPlayerPosition) packet;
        event.setCancelled(true);
        float spoofedYaw =
            mc.thePlayer.rotationYaw
                + (mc.thePlayer.ticksExisted % 2 == 0
                    ? RandomUtils.nextFloat(0.05F, 0.1F)
                    : -RandomUtils.nextFloat(0.05F, 0.1F));
        PacketUtil.sendPacketNoEvent(
            new C03PacketPlayer.C06PacketPlayerPosLook(
                ((IAccessorC03PacketPlayer) posPacket).getX(),
                ((IAccessorC03PacketPlayer) posPacket).getY(),
                ((IAccessorC03PacketPlayer) posPacket).getZ(),
                spoofedYaw,
                mc.thePlayer.rotationPitch,
                ((IAccessorC03PacketPlayer) posPacket).isOnGround()));
      }
    }
  }
}
