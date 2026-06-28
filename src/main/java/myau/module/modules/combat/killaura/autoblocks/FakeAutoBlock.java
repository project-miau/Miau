package myau.module.modules.combat.killaura.autoblocks;

import myau.Myau;
import myau.enums.BlinkModules;
import myau.module.modules.combat.KillAura;
import myau.util.player.PlayerUtil;

public class FakeAutoBlock extends AutoBlockMode {
  public FakeAutoBlock(KillAura parent) {
    super("FAKE", parent);
  }

  @Override
  public boolean processBlock(boolean attack, boolean block) {
    boolean swap = false;
    Myau.blinkManager.setBlinkState(false, BlinkModules.AUTO_BLOCK);
    parent.isBlocking = false;
    parent.fakeBlockState = parent.hasValidTarget();
    if (PlayerUtil.isUsingItem()
        && !parent.isPlayerBlocking()
        && !Myau.playerStateManager.digging
        && !Myau.playerStateManager.placing) {
      swap = true;
    }
    return swap;
  }
}
