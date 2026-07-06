package miau.module.modules.combat.killaura.target;

import java.util.ArrayList;
import java.util.List;
import miau.module.modules.combat.KillAura;
import miau.util.player.RotationUtil;
import miau.util.player.TeamUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.EntityLivingBase;

public class TargetManager {
  private static final Minecraft mc = Minecraft.getMinecraft();
  private final KillAura killAura;

  public TargetManager(KillAura killAura) {
    this.killAura = killAura;
  }

  public List<EntityLivingBase> getValidTargets() {
    ArrayList<EntityLivingBase> targets = new ArrayList<>();
    if (mc.theWorld == null) return targets;

    for (net.minecraft.entity.Entity entity : mc.theWorld.loadedEntityList) {
      if (entity instanceof EntityLivingBase) {
        EntityLivingBase livingBase = (EntityLivingBase) entity;
        // Accessing isValidTarget and isInRange through reflection or making them public
        // For this refactor, assuming we made them public in KillAura
        if (this.killAura.isValidTarget(livingBase) && this.killAura.isInRange(livingBase)) {
          targets.add(livingBase);
        }
      }
    }
    return targets;
  }

  public AttackData findBestTarget(List<EntityLivingBase> targets) {
    if (targets.isEmpty()) return null;

    if (targets.stream().anyMatch(this.killAura::isInSwingRange)) {
      targets.removeIf(e -> !this.killAura.isInSwingRange(e));
    }
    if (targets.stream().anyMatch(this.killAura::isInAttackRange)) {
      targets.removeIf(e -> !this.killAura.isInAttackRange(e));
    }
    if (targets.stream().anyMatch(this.killAura::isPlayerTarget)) {
      targets.removeIf(e -> !this.killAura.isPlayerTarget(e));
    }

    targets.sort(
        (e1, e2) -> {
          int sortBase = 0;
          switch (this.killAura.sort.getValue()) {
            case 1:
              sortBase = Float.compare(TeamUtil.getHealthScore(e1), TeamUtil.getHealthScore(e2));
              break;
            case 2:
              sortBase = Integer.compare(e1.hurtResistantTime, e2.hurtResistantTime);
              break;
            case 3:
              sortBase =
                  Float.compare(RotationUtil.angleToEntity(e1), RotationUtil.angleToEntity(e2));
              break;
          }
          return sortBase != 0
              ? sortBase
              : Double.compare(
                  RotationUtil.distanceToEntity(e1), RotationUtil.distanceToEntity(e2));
        });

    if (this.killAura.mode.getValue() == 1 && targets.size() > 1) {
      targets.sort(
          (e1, e2) -> {
            LastAttackData data1 = this.killAura.targetMap.get(e1.getEntityId());
            LastAttackData data2 = this.killAura.targetMap.get(e2.getEntityId());
            double score1 = -((e1.getHealth() * 25.0D) + (data1 == null ? 0 : data1.getTime()));
            double score2 = -((e2.getHealth() * 25.0D) + (data2 == null ? 0 : data2.getTime()));
            return Double.compare(score1, score2);
          });
    }

    if (this.killAura.mode.getValue() == 1 && this.killAura.hitRegistered) {
      this.killAura.hitRegistered = false;
      this.killAura.switchTick = 0;
    }
    if (this.killAura.mode.getValue() == 0 || this.killAura.switchTick >= targets.size()) {
      this.killAura.switchTick = 0;
    }

    return new AttackData(targets.get(this.killAura.switchTick));
  }
}
