package miau.module.modules.combat.killaura.autoblocks;

import miau.Miau;
import miau.enums.BlinkModules;
import miau.module.modules.combat.KillAura;

public class BlinkAutoBlock extends AutoBlockMode {
  public BlinkAutoBlock(KillAura parent) {
    super("BLINK", parent);
  }

  @Override
  public boolean processBlock(boolean attack, boolean block) {
    boolean swap = false;
    if (parent.getTarget() != null) {
      if (!Miau.playerStateManager.digging && !Miau.playerStateManager.placing) {
        switch (parent.blockTick) {
          case 0:
            if (!parent.isPlayerBlocking()) {
              swap = true;
            }
            parent.blockTick = 1;
            break;
          case 1:
            if (parent.isPlayerBlocking()) {
              parent.stopBlock();
              // attack = false;
              parent.cancelAttack = true;
            }
            if (parent.attackDelayMS <= 50L) {
              parent.blockTick = 0;
            }
            break;
          default:
            parent.blockTick = 0;
        }
      }
      Miau.blinkManager.setBlinkState(true, BlinkModules.AUTO_BLOCK);
      parent.isBlocking = true;
      parent.fakeBlockState = true;
      parent.blinkReset = true;
    } else {
      Miau.blinkManager.setBlinkState(false, BlinkModules.AUTO_BLOCK);
      parent.isBlocking = false;
      parent.fakeBlockState = false;
    }
    return swap;
  }
}
