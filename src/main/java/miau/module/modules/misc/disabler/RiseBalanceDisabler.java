package miau.module.modules.misc.disabler;

import miau.event.impl.*;
import miau.event.types.EventType;
import miau.module.modules.misc.Disabler;
import miau.property.properties.BooleanProperty;
import miau.util.time.TimerUtil;
import net.minecraft.network.Packet;
import net.minecraft.network.play.client.C03PacketPlayer;

/**
 * RiseBalance disabler: timer balance management with toggle buttons. Ported from OpenRise (Rise 6)
 *
 * <p>Toggle buttons: - Reset On World Change: Auto-reset balance on world change - Force low timer:
 * Force timer speed when balance is low - Display: Show balance in chat every 20 ticks - Pulse:
 * Blink-based pulse when timer > 1 - Hypixel: Ping spoof for Hypixel - Only Still: Only cancel
 * packets when player is still
 */
public class RiseBalanceDisabler extends DisablerMode {

  public final BooleanProperty resetOnWorldChange =
      new BooleanProperty("Reset On World Change", false);
  public final BooleanProperty forceLowTimer = new BooleanProperty("Force low timer", false);
  public final BooleanProperty display = new BooleanProperty("Display", false);
  public final BooleanProperty pulse = new BooleanProperty("Pulse", false);
  public final BooleanProperty hypixel = new BooleanProperty("Hypixel", false);
  public final BooleanProperty onlyWhenStill = new BooleanProperty("Only Still", false);

  private double balance = 0;
  private final TimerUtil stopWatch = new TimerUtil();

  public RiseBalanceDisabler(String name, Disabler parent) {
    super(name, parent);
  }

  @Override
  public void onEnable() {
    reset();
  }

  @Override
  public void onPacket(PacketEvent event) {
    if (event.getType() == EventType.SEND) {
      Packet<?> packet = event.getPacket();

      if (packet instanceof C03PacketPlayer) {
        C03PacketPlayer c03 = (C03PacketPlayer) packet;

        // Cancel non-rotating, non-moving C03 packets
        if (!c03.getRotating()
            && !c03.isMoving()
            && (!onlyWhenStill.getValue()
                || (mc.thePlayer.posX == mc.thePlayer.lastTickPosX
                    && mc.thePlayer.posY == mc.thePlayer.lastTickPosY
                    && mc.thePlayer.posZ == mc.thePlayer.lastTickPosZ))) {
          event.setCancelled(true);
        }

        // Track balance
        if (!event.isCancelled()) {
          this.balance -= 50;
        }
        this.balance += stopWatch.getElapsedTime();
        this.stopWatch.reset();
      }
    }
  }

  @Override
  public void onRender2D(Render2DEvent event) {
    if (forceLowTimer.getValue() && this.balance < 200) {
      ((miau.mixin.IAccessorMinecraft) mc).getTimer().timerSpeed = this.balance < 0 ? 0.5f : 1;
    }
  }

  @Override
  public void onLoadWorld(LoadWorldEvent event) {
    if (this.resetOnWorldChange.getValue()) {
      this.reset();
      this.balance = 0;
    }
  }

  private void reset() {
    this.balance = 0;
    this.stopWatch.reset();
  }
}
