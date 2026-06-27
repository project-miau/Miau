package myau.module.modules.combat.killaura.autoblocks;

import myau.Myau;
import myau.enums.BlinkModules;
import myau.module.modules.combat.KillAura;

public class BlinkAutoBlock extends AutoBlockMode {
  public BlinkAutoBlock(KillAura parent) {
    super("BLINK", parent);
  }

  @Override
  public boolean processBlock(boolean attack, boolean block) {
    boolean swap = false;
    if (parent.hasValidTarget()) {
      if (!Myau.playerStateManager.digging && !Myau.playerStateManager.placing) {
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
      Myau.blinkManager.setBlinkState(true, BlinkModules.AUTO_BLOCK);
      parent.isBlocking = true;
      parent.fakeBlockState = true;
      parent.blinkReset = true;
    } else {
      Myau.blinkManager.setBlinkState(false, BlinkModules.AUTO_BLOCK);
      parent.isBlocking = false;
      parent.fakeBlockState = false;
    }
    return swap;
  }
}
