package miau.module.modules.movement;

import miau.component.PingSpoofComponent;
import miau.event.EventTarget;
import miau.event.impl.HitSlowDownEvent;
import miau.event.impl.TickEvent;
import miau.event.types.EventType;
import miau.module.Module;
import miau.property.properties.BooleanProperty;
import miau.property.properties.IntProperty;
import miau.property.properties.ModeProperty;
import miau.property.properties.PercentProperty;
import miau.util.network.PacketUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.network.play.client.C0BPacketEntityAction;

public class KeepSprint extends Module {
  private static final Minecraft mc = Minecraft.getMinecraft();

  public final ModeProperty mode = new ModeProperty("mode", 0, new String[] {"NORMAL", "SPOOF"});
  public final IntProperty delay = new IntProperty("delay", 150, 50, 500);
  public final PercentProperty slowdown = new PercentProperty("slowdown", 0);
  public final BooleanProperty groundOnly = new BooleanProperty("ground-only", false);
  public final BooleanProperty reachOnly = new BooleanProperty("reach-only", false);

  public KeepSprint() {
    super("KeepSprint", false);
  }

  @Override
  public void onDisabled() {
    PingSpoofComponent.disable();
  }

  @EventTarget
  public void onTick(TickEvent event) {
    if (event.getType() != EventType.PRE) return;
    if (!this.isEnabled() || !this.shouldKeepSprint()) return;

    if (!mc.thePlayer.isSprinting()) {
      PacketUtil.sendPacket(
          new C0BPacketEntityAction(mc.thePlayer, C0BPacketEntityAction.Action.START_SPRINTING));
    }

    if (this.mode.getValue() == 1 /* SPOOF */) {
      // Enable PingSpoof for regular category to confuse anticheat timing
      // This makes sprint state transitions harder to detect
      PingSpoofComponent.spoof(this.delay.getValue(), true, false, false, false, false, false);
      PingSpoofComponent.enabled = true;
    }
  }

  @EventTarget
  public void onHitSlowDown(HitSlowDownEvent event) {
    if (this.isEnabled() && this.shouldKeepSprint()) {
      event.setSprint(true);
      double multiplier = 1.0 - this.slowdown.getValue().doubleValue() / 100.0;
      event.setSlowDown(0.6 + 0.4 * multiplier);
    }
  }

  public boolean shouldKeepSprint() {
    if (this.groundOnly.getValue() && !mc.thePlayer.onGround) {
      return false;
    } else {
      return !this.reachOnly.getValue()
          || mc.objectMouseOver.hitVec.distanceTo(mc.getRenderViewEntity().getPositionEyes(1.0F))
              > 3.0;
    }
  }
}
