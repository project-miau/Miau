package miau.module.modules.misc.disabler;

import miau.event.impl.PacketEvent;
import miau.event.impl.TickEvent;
import miau.event.types.EventType;
import miau.module.modules.misc.Disabler;
import miau.property.properties.BooleanProperty;
import miau.util.network.PacketUtil;
import net.minecraft.network.play.client.C07PacketPlayerDigging;
import net.minecraft.network.play.client.C0BPacketEntityAction;
import net.minecraft.util.BlockPos;
import net.minecraft.util.EnumFacing;

/**
 * Vulcan anti-cheat disabler with configurable toggle buttons. Ported from OpenRise (Rise 6)
 *
 * <p>Toggle buttons: - Omni-Sprint: Spoof sprinting packets - Auto Clicker: Prevent auto-clicker
 * flags - Reach (4.5 Block): Blink-based reach flag prevention - Strafe and Jump: Movement packet
 * anti-kick - Fast Use: Fast-use flag prevention - Miscellaneous: Cancel custom payload - Keep
 * Sprint: Keep-sprint spoof
 */
public class VulcanDisabler extends DisablerMode {

  public final BooleanProperty sprint = new BooleanProperty("Omni-Sprint", true);
  public final BooleanProperty autoClicker = new BooleanProperty("Auto Clicker", true);
  public final BooleanProperty reach = new BooleanProperty("Reach (4.5 Block)", true);
  public final BooleanProperty movement = new BooleanProperty("Strafe and Jump", true);
  public final BooleanProperty fastUse = new BooleanProperty("Fast Use", true);
  public final BooleanProperty miscellaneous = new BooleanProperty("Miscellaneous", true);
  public final BooleanProperty keepSprint = new BooleanProperty("Keep Sprint", true);
  public final BooleanProperty disableOnBreak = new BooleanProperty("Disable on break", true);

  public VulcanDisabler(String name, Disabler parent) {
    super(name, parent);
  }

  @Override
  public void onTick(TickEvent event) {
    if (event.getType() != EventType.PRE) return;
    if (mc.thePlayer == null) return;

    if (disableOnBreak.getValue()
        && ((miau.mixin.IAccessorPlayerControllerMP) mc.playerController).getIsHittingBlock()) {
      return;
    }

    // Omni-Sprint: spoof both START and STOP sprinting
    if (sprint.getValue()) {
      PacketUtil.sendPacketNoEvent(
          new C0BPacketEntityAction(mc.thePlayer, C0BPacketEntityAction.Action.START_SPRINTING));
      PacketUtil.sendPacketNoEvent(
          new C0BPacketEntityAction(mc.thePlayer, C0BPacketEntityAction.Action.STOP_SPRINTING));
    }

    // Strafe and Jump: anti-kick block break every 5 ticks
    if (movement.getValue() && mc.thePlayer.ticksExisted % 5 == 0) {
      PacketUtil.sendPacketNoEvent(
          new C07PacketPlayerDigging(
              C07PacketPlayerDigging.Action.STOP_DESTROY_BLOCK,
              new BlockPos(mc.thePlayer),
              EnumFacing.UP));
    }

    // Auto Clicker: flag prevention every 100 ticks
    if (autoClicker.getValue()
        && mc.thePlayer.ticksExisted % 100 == 0
        && mc.currentScreen == null) {
      PacketUtil.sendPacketNoEvent(
          new C07PacketPlayerDigging(
              C07PacketPlayerDigging.Action.START_DESTROY_BLOCK,
              new BlockPos(mc.thePlayer),
              EnumFacing.UP));
    }

    // Fast Use: flag prevention every 7 ticks
    if (fastUse.getValue() && mc.thePlayer.ticksExisted % 7 == 0) {
      PacketUtil.sendPacketNoEvent(
          new C07PacketPlayerDigging(
              C07PacketPlayerDigging.Action.DROP_ITEM, BlockPos.ORIGIN, EnumFacing.DOWN));
    }

    // Reach: blink-based reach spoof using global BlinkComponent
    if (reach.getValue()) {
      miau.component.BlinkComponent.blinking = true;
      if (mc.thePlayer.ticksExisted % 2 == 0) {
        miau.component.BlinkComponent.dispatch();
      }
    } else {
      if (miau.component.BlinkComponent.blinking) {
        miau.component.BlinkComponent.blinking = false;
        miau.component.BlinkComponent.packets.forEach(PacketUtil::sendPacketNoEvent);
        miau.component.BlinkComponent.packets.clear();
      }
    }
  }

  @Override
  public void onDisable() {
    miau.component.BlinkComponent.blinking = false;
    miau.component.BlinkComponent.packets.forEach(PacketUtil::sendPacketNoEvent);
    miau.component.BlinkComponent.packets.clear();
  }

  @Override
  public void onPacket(PacketEvent event) {
    if (miscellaneous.getValue()) {
      if (event.getPacket() instanceof net.minecraft.network.play.client.C17PacketCustomPayload) {
        event.setCancelled(true);
      }
    }
  }
}
