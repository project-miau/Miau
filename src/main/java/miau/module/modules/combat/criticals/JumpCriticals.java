package miau.module.modules.combat.criticals;

import miau.event.impl.AttackEvent;
import miau.module.modules.combat.Criticals;
import net.minecraft.entity.EntityLivingBase;

public class JumpCriticals extends CriticalsMode {
  public JumpCriticals(String name, Criticals parent) {
    super(name, parent);
  }

  @Override
  public void onAttack(AttackEvent event) {
    if (event.getTarget() instanceof EntityLivingBase) {
      if (mc.thePlayer.onGround
          && !mc.thePlayer.isOnLadder()
          && !mc.thePlayer.isInWater()
          && !mc.thePlayer.isInLava()
          && mc.thePlayer.ridingEntity == null) {
        mc.thePlayer.jump();
      }
    }
  }
}
