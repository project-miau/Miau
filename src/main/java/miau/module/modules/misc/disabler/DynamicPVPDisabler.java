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

/**
 * DynamicPVP disabler: packet conversion + cancel transaction/keepalive Combines multiple bypass
 * methods for DynamicPVP servers Ported from OpenRise (Rise 6)
 */
public class DynamicPVPDisabler extends DisablerMode {

  public DynamicPVPDisabler(String name, Disabler parent) {
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

      // Cancel 33% of C03 player packets (movement)
      if (packet instanceof C03PacketPlayer && mc.thePlayer.ticksExisted % 3 == 0) {
        event.setCancelled(true);
        return;
      }

      // Convert C04 to C06 (add look)
      if (packet instanceof C03PacketPlayer.C04PacketPlayerPosition) {
        C03PacketPlayer.C04PacketPlayerPosition posPacket =
            (C03PacketPlayer.C04PacketPlayerPosition) packet;
        event.setCancelled(true);
        PacketUtil.sendPacketNoEvent(
            new C03PacketPlayer.C06PacketPlayerPosLook(
                ((IAccessorC03PacketPlayer) posPacket).getX(),
                ((IAccessorC03PacketPlayer) posPacket).getY(),
                ((IAccessorC03PacketPlayer) posPacket).getZ(),
                mc.thePlayer.rotationYaw,
                mc.thePlayer.rotationPitch,
                ((IAccessorC03PacketPlayer) posPacket).isOnGround()));
      }

      // Cancel transactions and keepalives
      if (packet instanceof C0FPacketConfirmTransaction || packet instanceof C00PacketKeepAlive) {
        event.setCancelled(true);
      }
    }
  }
}
