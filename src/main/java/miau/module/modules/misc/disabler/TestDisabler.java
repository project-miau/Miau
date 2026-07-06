package miau.module.modules.misc.disabler;

import io.netty.buffer.Unpooled;
import miau.event.impl.PacketEvent;
import miau.event.types.EventType;
import miau.module.modules.misc.Disabler;
import net.minecraft.network.PacketBuffer;
import net.minecraft.network.play.client.C17PacketCustomPayload;

/** Test disabler: corrupts custom payload channel data ("eyser") Ported from OpenRise (Rise 6) */
public class TestDisabler extends DisablerMode {

  public TestDisabler(String name, Disabler parent) {
    super(name, parent);
  }

  @Override
  public void onPacket(PacketEvent event) {
    if (event.getType() == EventType.SEND) {
      if (event.getPacket() instanceof C17PacketCustomPayload) {
        C17PacketCustomPayload wrapper = (C17PacketCustomPayload) event.getPacket();
        ((miau.mixin.IAccessorC17PacketCustomPayload) wrapper)
            .setData(createPacketBuffer("eyser", false));
      }
    }
  }

  private PacketBuffer createPacketBuffer(String data, boolean string) {
    if (string) {
      return new PacketBuffer(Unpooled.buffer()).writeString(data);
    } else {
      return new PacketBuffer(Unpooled.wrappedBuffer(data.getBytes()));
    }
  }
}
