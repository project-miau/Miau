package miau.module.modules.misc.disabler;

import miau.event.impl.StrafeEvent;
import miau.module.modules.misc.Disabler;

/**
 * OmniSprint disabler: allows sprinting in all directions by rotating the player Ported from
 * OpenRise (Rise 6)
 */
public class OmniSprintDisabler extends DisablerMode {

  private float forward, strafe;

  public OmniSprintDisabler(String name, Disabler parent) {
    super(name, parent);
  }

  @Override
  public void onStrafe(StrafeEvent event) {
    if (mc.thePlayer == null || mc.theWorld == null) return;

    forward = event.getForward();
    strafe = event.getStrafe();

    // Calculate the direction yaw from input
    float yaw = mc.thePlayer.rotationYaw;
    if (forward < 0) yaw += 180;
    float f = 1;
    if (forward < 0) f = -0.5F;
    else if (forward > 0) f = 0.5F;
    if (strafe > 0) yaw -= 90 * f;
    if (strafe < 0) yaw += 90 * f;

    mc.thePlayer.rotationYaw = yaw;
  }
}
