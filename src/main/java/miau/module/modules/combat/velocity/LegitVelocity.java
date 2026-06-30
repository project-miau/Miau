package miau.module.modules.combat.velocity;

import miau.event.impl.PacketEvent;
import miau.event.impl.UpdateEvent;
import miau.module.modules.combat.Velocity;
import miau.property.properties.IntProperty;
import miau.property.properties.PercentProperty;
import net.minecraft.network.play.server.S12PacketEntityVelocity;

public class LegitVelocity extends VelocityMode {
  private boolean hasReceivedVelocity = false;
  private int legitSmartJumpCount = 0;

  public final IntProperty legitJumpLimit = new IntProperty("legit-jump-limit", 2, 1, 5);
  public final PercentProperty chance = new PercentProperty("chance", 100);

  public LegitVelocity(String name, Velocity parent) {
    super(name, parent);
  }

  @Override
  public void onPacket(PacketEvent event) {
    if (event.getType() == miau.event.types.EventType.RECEIVE
        && event.getPacket() instanceof S12PacketEntityVelocity) {
      S12PacketEntityVelocity packet = (S12PacketEntityVelocity) event.getPacket();
      if (packet.getEntityID() == mc.thePlayer.getEntityId()) {
        hasReceivedVelocity = true;
      }
    }
  }

  @Override
  public void onUpdate(UpdateEvent event) {
    if (event.getType() == miau.event.types.EventType.POST) {
      if (hasReceivedVelocity) {
        if (mc.thePlayer.onGround
            && mc.thePlayer.hurtTime >= 8
            && mc.thePlayer.isSprinting()
            && !parent.isInLiquidOrWeb()) {
          if (legitSmartJumpCount >= legitJumpLimit.getValue()) {
            legitSmartJumpCount = 0;
            hasReceivedVelocity = false;
          } else {
            legitSmartJumpCount++;
            mc.thePlayer.movementInput.jump = true;
          }
        } else if (mc.thePlayer.hurtTime <= 1) {
          hasReceivedVelocity = false;
          legitSmartJumpCount = 0;
        }
      }
    }
  }
}
