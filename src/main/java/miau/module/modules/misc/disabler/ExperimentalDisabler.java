package miau.module.modules.misc.disabler;

import java.util.ArrayList;
import java.util.List;
import miau.event.impl.PacketEvent;
import miau.event.impl.TickEvent;
import miau.event.types.EventType;
import miau.module.modules.misc.Disabler;
import miau.util.network.PacketUtil;
import net.minecraft.network.Packet;
import net.minecraft.network.play.client.C0FPacketConfirmTransaction;

/**
 * Experimental disabler: delay transaction packets by 5 ticks. Ported from OpenRise (Rise 6)
 *
 * <p>Properties: - Ticks: Number of ticks to delay (hardcoded to 5 in Rise)
 */
public class ExperimentalDisabler extends DisablerMode {

  public List<Packet<?>> packetList = new ArrayList<>();

  public ExperimentalDisabler(String name, Disabler parent) {
    super(name, parent);
  }

  @Override
  public void onPacket(PacketEvent event) {
    if (event.getType() == EventType.SEND) {
      Packet<?> packet = event.getPacket();
      if (packet instanceof C0FPacketConfirmTransaction) {
        packetList.add(packet);
        event.setCancelled(true);
      }
    }
  }

  @Override
  public void onTick(TickEvent event) {
    if (event.getType() != EventType.PRE) return;
    if (mc.thePlayer != null && mc.thePlayer.ticksExisted % 5 == 0) {
      for (Packet<?> p : packetList) {
        PacketUtil.sendPacketNoEvent(p);
      }
      packetList.clear();
    }
  }

  @Override
  public void onDisable() {
    if (!packetList.isEmpty()) {
      for (Packet<?> p : packetList) {
        PacketUtil.sendPacketNoEvent(p);
      }
      packetList.clear();
    }
  }
}
