package myau.module.modules.combat.killaura.autoblocks;

import myau.Myau;
import myau.enums.BlinkModules;
import myau.module.modules.combat.KillAura;

public class LegitAutoBlock extends AutoBlockMode {
  public LegitAutoBlock(KillAura parent) {
    super("LEGIT", parent);
  }

  @Override
  public boolean processBlock(boolean attack, boolean block) {
    boolean swap = false;
    if (parent.hasValidTarget()) {
      if (!parent.isPlayerBlocking()
          && !Myau.playerStateManager.digging
          && !Myau.playerStateManager.placing) {
        parent.setRightHold(true);
      } else {
        parent.setRightHold(false);
      }
      Myau.blinkManager.setBlinkState(false, BlinkModules.AUTO_BLOCK);
      parent.isBlocking = true;
      parent.fakeBlockState = false;
    } else {
      parent.setRightHold(false);
      Myau.blinkManager.setBlinkState(false, BlinkModules.AUTO_BLOCK);
      parent.isBlocking = false;
      parent.fakeBlockState = false;
    }
    return swap;
  }
}
