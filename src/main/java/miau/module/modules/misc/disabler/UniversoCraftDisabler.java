package miau.module.modules.misc.disabler;

import miau.event.impl.PacketEvent;
import miau.event.types.EventType;
import miau.module.modules.misc.Disabler;
import net.minecraft.network.Packet;
import net.minecraft.network.play.client.C02PacketUseEntity;
import net.minecraft.network.play.client.C03PacketPlayer;
import net.minecraft.network.play.client.C0FPacketConfirmTransaction;

/**
 * UniversoCraft disabler: corrupts transaction UID after respawn/join Ported from OpenRise (Rise 6)
 *
 * <p>Note: Original OpenRise checked S07PacketRespawn but since Miau's mixin only fires SEND for
 * outgoing client packets, we detect via player ticks instead.
 */
public class UniversoCraftDisabler extends DisablerMode {

  private boolean disabling;

  public UniversoCraftDisabler(String name, Disabler parent) {
    super(name, parent);
  }

  @Override
  public void onPacket(PacketEvent event) {
    if (event.getType() == EventType.SEND) {
      Packet<?> packet = event.getPacket();

      // Detect early-game state by player ticks
      if (packet instanceof C03PacketPlayer && mc.thePlayer.ticksExisted <= 10) {
        disabling = true;
      } else if (packet instanceof C02PacketUseEntity) {
        disabling = false;
      }

      // Corrupt transaction UIDs while in disabling state
      if (packet instanceof C0FPacketConfirmTransaction
          && disabling
          && mc.thePlayer.ticksExisted < 350) {
        C0FPacketConfirmTransaction transaction = (C0FPacketConfirmTransaction) packet;
        ((miau.mixin.IAccessorC0FPacketConfirmTransaction) transaction)
            .setUid(
                (short) (mc.thePlayer.ticksExisted % 2 == 0 ? Short.MIN_VALUE : Short.MAX_VALUE));
      }
    }
  }
}
