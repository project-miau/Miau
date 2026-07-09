package miau.module.modules.misc.cheatdetector.impl.world.placementanalysis;

import miau.module.modules.misc.cheatdetector.Check;
import net.minecraft.entity.player.EntityPlayer;

public class ScaffoldSneakCheck extends Check {
  @Override
  public String getName() {
    return "ScaffoldSneakCheck";
  }

  private boolean wasSneaking = false;
  private int sneakTicks = 0;
  private int unsneakTicks = 0;

  @Override
  public void onUpdate(EntityPlayer player) {
    if (player.isSneaking()) {
      sneakTicks++;
      if (!wasSneaking
          && unsneakTicks > 0
          && unsneakTicks < 3
          && player.inventory.getCurrentItem() != null
          && player.inventory.getCurrentItem().getItem() instanceof net.minecraft.item.ItemBlock) {
        flag(player, "Eagle/Scaffold (Unsneak ticks: " + unsneakTicks + ")");
      }
      unsneakTicks = 0;
    } else {
      unsneakTicks++;
      if (wasSneaking
          && sneakTicks > 0
          && sneakTicks < 3
          && player.inventory.getCurrentItem() != null
          && player.inventory.getCurrentItem().getItem() instanceof net.minecraft.item.ItemBlock) {
        flag(player, "Eagle/Scaffold (Sneak ticks: " + sneakTicks + ")");
      }
      sneakTicks = 0;
    }
    wasSneaking = player.isSneaking();
  }
}
