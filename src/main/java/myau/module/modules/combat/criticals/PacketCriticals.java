package myau.module.modules.combat.criticals;

import myau.event.impl.AttackEvent;
import myau.module.modules.combat.Criticals;
import myau.util.network.PacketUtil;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.network.play.client.C03PacketPlayer;

public class PacketCriticals extends CriticalsMode {
  private final double[] offsets = new double[] {0.0625, 0};

  public PacketCriticals(String name, Criticals parent) {
    super(name, parent);
  }

  @Override
  public void onAttack(AttackEvent event) {
    if (event.getTarget() instanceof EntityLivingBase) {
      if (parent.timer.hasTimeElapsed((long) parent.delay.getValue())
          && myau.util.player.PlayerTracker.onGroundTicks > 2) {
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
