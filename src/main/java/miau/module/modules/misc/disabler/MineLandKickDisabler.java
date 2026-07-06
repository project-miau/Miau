package miau.module.modules.misc.disabler;

import miau.event.impl.MoveInputEvent;
import miau.event.impl.TickEvent;
import miau.event.types.EventType;
import miau.module.modules.misc.Disabler;

/**
 * MineLand Kick disabler: position spoof for anti-kick on MineLand Ported from OpenRise (Rise 6)
 */
public class MineLandKickDisabler extends DisablerMode {

  private boolean enabled;

  public MineLandKickDisabler(String name, Disabler parent) {
    super(name, parent);
  }

  @Override
  public void onEnable() {
    enabled = false;
  }

  @Override
  public void onTick(TickEvent event) {
    if (event.getType() != EventType.PRE) return;
    if (mc.thePlayer == null || mc.theWorld == null) return;

    if (mc.thePlayer.ticksExisted > 20
        && mc.thePlayer.ticksExisted <= 40
        && mc.thePlayer.onGround) {
      this.enabled = true;
    } else if (mc.thePlayer.ticksExisted == 100) {
      this.enabled = false;
    }

    if (this.enabled) {
      mc.thePlayer.onGround = true;
      mc.thePlayer.posX = mc.thePlayer.posX - 0.1 * (mc.thePlayer.ticksExisted % 2 == 0 ? 1 : -1);
      mc.thePlayer.posZ = mc.thePlayer.posZ - 0.1 * (mc.thePlayer.ticksExisted % 2 == 0 ? 1 : -1);
      mc.thePlayer.posY =
          mc.thePlayer.onGround ? (Math.floor(mc.thePlayer.posY) + 1) : mc.thePlayer.posY;
    }
  }

  @Override
  public void onMoveInput(MoveInputEvent event) {
    if (this.enabled) {
      if (mc.thePlayer != null) {
        mc.thePlayer.movementInput.moveForward = 0;
        mc.thePlayer.movementInput.moveStrafe = 0;
        mc.thePlayer.movementInput.jump = false;
      }
    }
  }
}
