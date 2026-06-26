package myau.module.modules.combat.velocity;

import myau.event.impl.PacketEvent;
import myau.event.impl.UpdateEvent;
import myau.module.modules.combat.Velocity;
import myau.property.properties.FloatProperty;
import myau.property.properties.IntProperty;
import net.minecraft.network.play.server.S12PacketEntityVelocity;

public class IntaveReduceVelocity extends VelocityMode {
  private boolean hasReceivedVelocity = false;
  private int intaveTick = 0;
  private int intaveDamageTick = 0;

  public final FloatProperty intaveReduceFactor =
      new FloatProperty("intave-reduce-factor", 0.6F, 0.6F, 1.0F);
  public final IntProperty intaveReduceHurtTime =
      new IntProperty("intave-reduce-hurt-time", 9, 1, 10);

  public IntaveReduceVelocity(String name, Velocity parent) {
    super(name, parent);
  }

  @Override
  public void onPacket(PacketEvent event) {
    if (event.getType() == myau.event.types.EventType.RECEIVE
        && event.getPacket() instanceof S12PacketEntityVelocity) {
      S12PacketEntityVelocity packet = (S12PacketEntityVelocity) event.getPacket();
      if (packet.getEntityID() == mc.thePlayer.getEntityId()) {
        hasReceivedVelocity = true;
      }
    }
  }

  @Override
  public void onUpdate(UpdateEvent event) {
    if (event.getType() == myau.event.types.EventType.POST) {
      if (hasReceivedVelocity) {
        intaveTick++;
        if (mc.thePlayer.hurtTime == 2) {
          intaveDamageTick++;
          if (mc.thePlayer.onGround && intaveTick % 2 == 0 && intaveDamageTick <= 10) {
            mc.thePlayer.jump();
            intaveTick = 0;
          }
          hasReceivedVelocity = false;
        }
      }
    }
  }
}
