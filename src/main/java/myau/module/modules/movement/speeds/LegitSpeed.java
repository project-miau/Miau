package myau.module.modules.movement.speeds;

import myau.event.impl.LivingUpdateEvent;
import myau.module.modules.movement.Speed;
import myau.util.player.MoveUtil;

public class LegitSpeed extends SpeedMode {
  public LegitSpeed(String name, Speed parent) {
    super(name, parent);
  }

  @Override
  public void onLivingUpdate(LivingUpdateEvent event) {
    if (parent.canBoost()) {
      if (mc.thePlayer.onGround && MoveUtil.isForwardPressed()) {
        mc.thePlayer.jump();
      }
      mc.thePlayer.setSprinting(mc.thePlayer.movementInput.moveForward > 0.8F);
    }
  }
}
