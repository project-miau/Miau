package miau.module.modules.combat.criticals;

import miau.event.impl.AttackEvent;
import miau.event.impl.UpdateEvent;
import miau.event.types.EventType;
import miau.module.modules.combat.Criticals;
import net.minecraft.entity.EntityLivingBase;

public class WatchdogCriticals extends CriticalsMode {
  private int ticks;

  public WatchdogCriticals(String name, Criticals parent) {
    super(name, parent);
  }

  @Override
  public void onUpdate(UpdateEvent event) {
    if (event.getType() == EventType.PRE) {
      if (mc.thePlayer.onGround) {
        ticks++;
        if (ticks == 1) {
          mc.thePlayer.setPosition(
              mc.thePlayer.posX, mc.thePlayer.posY + 0.0625D, mc.thePlayer.posZ);
        } else if (ticks == 2) {
          mc.thePlayer.setPosition(
              mc.thePlayer.posX, mc.thePlayer.posY + 0.015625D, mc.thePlayer.posZ);
        }
      } else {
        ticks = 0;
      }
    }
  }

  @Override
  public void onAttack(AttackEvent event) {
    if (event.getTarget() instanceof EntityLivingBase) {
      if (mc.thePlayer.onGround
          && !mc.thePlayer.isOnLadder()
          && parent.timer.hasTimeElapsed((long) parent.delay.getValue())) {
        mc.thePlayer.onCriticalHit(event.getTarget());
        parent.timer.reset();
      }
    }
  }
}
