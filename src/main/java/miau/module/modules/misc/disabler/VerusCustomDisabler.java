package miau.module.modules.misc.disabler;

import miau.event.impl.PacketEvent;
import miau.event.impl.TickEvent;
import miau.event.types.EventType;
import miau.module.modules.misc.Disabler;
import miau.property.properties.BooleanProperty;
import miau.util.network.PacketUtil;
import miau.util.time.TimerUtil;
import net.minecraft.network.Packet;
import net.minecraft.network.play.client.C0BPacketEntityAction;

/**
 * Verus Custom disabler with toggle buttons for movement and sprint. Ported from OpenRise (Rise 6)
 *
 * <p>Toggle buttons: - Movement: Movement-based disabler with auto-toggle - Sprint: Sprint packet
 * cancel + spoof
 */
public class VerusCustomDisabler extends DisablerMode {

  public final BooleanProperty movementDisabler = new BooleanProperty("Movement", false);
  public final BooleanProperty sprintDisabler = new BooleanProperty("Sprint", false);

  private int disable;
  private final TimerUtil timer = new TimerUtil();

  public VerusCustomDisabler(String name, Disabler parent) {
    super(name, parent);
  }

  @Override
  public void onTick(TickEvent event) {
    if (event.getType() != EventType.PRE) return;
    if (mc.thePlayer == null || mc.theWorld == null) return;

    // Sprint spoof: alternate START/STOP every 5 ticks
    if (sprintDisabler.getValue() && isMoving() && mc.thePlayer.ticksExisted > 50) {
      if (mc.thePlayer.ticksExisted % 5 == 0) {
        if (mc.thePlayer.ticksExisted % 10 == 0) {
          PacketUtil.sendPacketNoEvent(
              new C0BPacketEntityAction(
                  mc.thePlayer, C0BPacketEntityAction.Action.START_SPRINTING));
        } else {
          PacketUtil.sendPacketNoEvent(
              new C0BPacketEntityAction(mc.thePlayer, C0BPacketEntityAction.Action.STOP_SPRINTING));
        }
      }
    }

    // Movement disabler: decrement disable counter
    if (movementDisabler.getValue()) {
      disable++;
    }
  }

  @Override
  public void onPacket(PacketEvent event) {
    if (event.getType() == EventType.SEND) {
      final Packet<?> packet = event.getPacket();

      if (sprintDisabler.getValue() && packet instanceof C0BPacketEntityAction) {
        C0BPacketEntityAction c0b = ((C0BPacketEntityAction) packet);
        if (c0b.getAction() == C0BPacketEntityAction.Action.START_SPRINTING
            || c0b.getAction() == C0BPacketEntityAction.Action.STOP_SPRINTING) {
          event.setCancelled(true);
        }
      }
    }
  }

  private boolean isMoving() {
    return mc.thePlayer != null
        && (mc.thePlayer.movementInput.moveForward != 0
            || mc.thePlayer.movementInput.moveStrafe != 0);
  }
}
