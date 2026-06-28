package myau.module.modules.combat.killaura.autoblocks;

import myau.Myau;
import myau.enums.BlinkModules;
import myau.module.modules.combat.KillAura;
import myau.util.player.PlayerUtil;

public class NoneAutoBlock extends AutoBlockMode {
  public NoneAutoBlock(KillAura parent) {
    super("NONE", parent);
  }

  @Override
  public boolean processBlock(boolean attack, boolean block) {
    boolean swap = false;
    if (PlayerUtil.isUsingItem()) {
      parent.isBlocking = true;
      if (!parent.isPlayerBlocking()
          && !Myau.playerStateManager.digging
          && !Myau.playerStateManager.placing) {
        swap = true;
      }
    } else {
      parent.isBlocking = false;
      if (parent.isPlayerBlocking()
          && !Myau.playerStateManager.digging
          && !Myau.playerStateManager.placing) {
        parent.stopBlock();
      }
    }
    Myau.blinkManager.setBlinkState(false, BlinkModules.AUTO_BLOCK);
    parent.fakeBlockState = false;
    return swap;
  }
}
