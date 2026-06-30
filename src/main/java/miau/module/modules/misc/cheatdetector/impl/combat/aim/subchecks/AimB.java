package miau.module.modules.misc.cheatdetector.impl.combat.aim.subchecks;

import miau.module.modules.misc.cheatdetector.Check;
import net.minecraft.entity.player.EntityPlayer;

public class AimB extends Check {

  @Override
  public String getName() {
    return "Aim B";
  }

  @Override
  public void onUpdate(EntityPlayer player) {
    if (player.rotationPitch > 90 || player.rotationPitch < -90) {
      flag(player, "Invalid pitch");
    }
  }
}
