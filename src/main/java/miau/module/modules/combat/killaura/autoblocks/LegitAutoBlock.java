package miau.module.modules.combat.killaura.autoblocks;

import miau.Miau;
import miau.enums.BlinkModules;
import miau.module.modules.combat.KillAura;
import miau.util.client.KeyBindUtil;
import net.minecraft.client.Minecraft;

public class LegitAutoBlock extends AutoBlockMode {
  private static final Minecraft mc = Minecraft.getMinecraft();

  public LegitAutoBlock(KillAura parent) {
    super("LEGIT", parent);
  }

  @Override
  public boolean processBlock(boolean attack, boolean block) {
    if (parent.hasValidTarget() && parent.getTarget() != null) {
      double range = mc.thePlayer.getDistanceToEntity(parent.getTarget());

      boolean shouldPress = range < 3.0 && parent.ticksSinceVelocity >= 5;

      KeyBindUtil.setKeyBindState(mc.gameSettings.keyBindUseItem.getKeyCode(), shouldPress);

      parent.blockTick++;
      if (mc.gameSettings.keyBindUseItem.isPressed() || mc.thePlayer.isUsingItem()) {
        parent.blockTick = 0;
      }

      if (parent.blockTick < 2) {
        parent.cancelAttack = true;
      }

      parent.isBlocking = true;
      parent.fakeBlockState = false;
      Miau.blinkManager.setBlinkState(false, BlinkModules.AUTO_BLOCK);
    } else {
      KeyBindUtil.setKeyBindState(mc.gameSettings.keyBindUseItem.getKeyCode(), false);
      parent.isBlocking = false;
      parent.fakeBlockState = false;
    }

    return false;
  }
}
