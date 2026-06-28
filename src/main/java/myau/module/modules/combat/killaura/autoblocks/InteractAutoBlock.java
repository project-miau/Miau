package myau.module.modules.combat.killaura.autoblocks;

import myau.Myau;
import myau.enums.BlinkModules;
import myau.module.modules.combat.KillAura;

public class InteractAutoBlock extends AutoBlockMode {
  public InteractAutoBlock(KillAura parent) {
    super("INTERACT", parent);
  }

  @Override
  public boolean processBlock(boolean attack, boolean block) {
    boolean swap = false;
    if (parent.hasValidTarget()) {
      if (!parent.isPlayerBlocking()
          && !Myau.playerStateManager.digging
          && !Myau.playerStateManager.placing) {
        swap = true;
      }
      Myau.blinkManager.setBlinkState(false, BlinkModules.AUTO_BLOCK);
      parent.isBlocking = true;
      parent.fakeBlockState = false;
    } else {
      Myau.blinkManager.setBlinkState(false, BlinkModules.AUTO_BLOCK);
      parent.isBlocking = false;
      parent.fakeBlockState = false;
    }
    return swap;
  }
}
