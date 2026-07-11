package miau.module.modules.combat.velocity;

import java.util.ArrayList;
import java.util.List;
import miau.event.impl.PacketEvent;
import miau.event.impl.UpdateEvent;
import miau.module.modules.combat.Velocity;
import miau.property.properties.IntProperty;
import net.minecraft.network.Packet;
import net.minecraft.network.play.server.S00PacketKeepAlive;
import net.minecraft.network.play.server.S12PacketEntityVelocity;
import net.minecraft.network.play.server.S32PacketConfirmTransaction;

public class DelayVelocity extends VelocityMode {
  private boolean hasVelocity = false;
  private boolean processing = false;
  private int delayingTicks = 0;
  private final List<Packet<?>> packets = new ArrayList<>();

  public final IntProperty maxDelayTicks = new IntProperty("max-delay-ticks", 3, 1, 20);

  public DelayVelocity(String name, Velocity parent) {
    super(name, parent);
  }

  @Override
  public void onEnable() {
    hasVelocity = false;
    processing = false;
    delayingTicks = 0;
    packets.clear();
  }

  @Override
  public void onDisable() {
    packets.clear();
  }

  @Override
  public void onUpdate(UpdateEvent event) {
    if (event.getType() == miau.event.types.EventType.POST) {
      if (hasVelocity) {
        if (mc.thePlayer.onGround
            || mc.thePlayer.fallDistance > 0.5f
            || delayingTicks >= maxDelayTicks.getValue()) {
          processing = true;
          for (Packet packet : packets) {
            try {
              packet.processPacket(mc.getNetHandler());
            } catch (Exception e) {
              e.printStackTrace();
            }
          }
          packets.clear();
          processing = false;
          hasVelocity = false;
          delayingTicks = 0;
        } else {
          delayingTicks++;
        }
      }
    }
  }

  @Override
  public void onPacket(PacketEvent event) {
    if (event.getType() == miau.event.types.EventType.RECEIVE) {
      if (event.getPacket() instanceof S12PacketEntityVelocity) {
        S12PacketEntityVelocity s12 = (S12PacketEntityVelocity) event.getPacket();
        if (s12.getEntityID() == mc.thePlayer.getEntityId()) {
          if (!processing) {
            hasVelocity = true;
            event.setCancelled(true);
            packets.add(s12);
          }
        }
      } else if (!processing) {
        if (event.getPacket() instanceof S32PacketConfirmTransaction) {
          if (hasVelocity) {
            packets.add(event.getPacket());
            event.setCancelled(true);
          }
        } else if (event.getPacket() instanceof S00PacketKeepAlive) {
          if (hasVelocity) {
            if (mc.thePlayer.ticksExisted % 3 == 0) {
              packets.add(event.getPacket());
            }
            event.setCancelled(true);
          }
        }
      }
    }
  }
}
