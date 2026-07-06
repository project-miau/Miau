package miau.module.modules.misc.disabler;

import miau.event.impl.TickEvent;
import miau.event.types.EventType;
import miau.module.modules.misc.Disabler;

/** Sprint disabler: cancel sprint state in motion events Ported from OpenRise (Rise 6) */
public class SprintDisabler extends DisablerMode {

  public SprintDisabler(String name, Disabler parent) {
    super(name, parent);
  }

  @Override
  public void onTick(TickEvent event) {
    if (event.getType() != EventType.PRE) return;
    if (mc.thePlayer == null || mc.theWorld == null) return;

    mc.thePlayer.setSprinting(false);
  }
}
