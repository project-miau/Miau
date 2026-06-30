package miau.module.modules.combat.killaura.autoblocks;

import miau.Miau;
import miau.enums.BlinkModules;
import miau.module.modules.combat.KillAura;

public class LegitAutoBlock extends AutoBlockMode {
  public LegitAutoBlock(KillAura parent) {
    super("LEGIT", parent);
  }

  @Override
  public boolean processBlock(boolean attack, boolean block) {
    boolean swap = false;
    if (parent.hasValidTarget()) {
      if (!parent.isPlayerBlocking()
          && !Miau.playerStateManager.digging
          && !Miau.playerStateManager.placing) {
        parent.setRightHold(true);
      } else {
        parent.setRightHold(false);
      }
      Miau.blinkManager.setBlinkState(false, BlinkModules.AUTO_BLOCK);
      parent.isBlocking = true;
      parent.fakeBlockState = false;
    } else {
      parent.setRightHold(false);
      Miau.blinkManager.setBlinkState(false, BlinkModules.AUTO_BLOCK);
      parent.isBlocking = false;
      parent.fakeBlockState = false;
    }
    return swap;
  }
}
