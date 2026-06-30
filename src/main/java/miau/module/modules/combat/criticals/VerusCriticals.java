package miau.module.modules.combat.criticals;

import miau.event.impl.AttackEvent;
import miau.module.modules.combat.Criticals;
import miau.util.network.PacketUtil;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.network.play.client.C03PacketPlayer;

public class VerusCriticals extends CriticalsMode {
  private final double[] offsets = new double[] {0.0625, 0};

  public VerusCriticals(String name, Criticals parent) {
    super(name, parent);
  }

  @Override
  public void onAttack(AttackEvent event) {
    if (event.getTarget() instanceof EntityLivingBase) {
      if (parent.timer.hasTimeElapsed((long) parent.delay.getValue())
          && miau.util.player.PlayerTracker.onGroundTicks > 2
          && mc.thePlayer.hurtTime != 0) {
        for (double offset : offsets) {
          PacketUtil.sendPacket(
              new C03PacketPlayer.C04PacketPlayerPosition(
                  mc.thePlayer.posX, mc.thePlayer.posY + offset, mc.thePlayer.posZ, false));
        }
        mc.thePlayer.onCriticalHit(event.getTarget());
        parent.timer.reset();
      }
    }
  }
}
