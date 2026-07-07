package miau.module.modules.misc.cheatdetector.impl;

import miau.module.modules.misc.cheatdetector.Check;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemBucketMilk;
import net.minecraft.item.ItemFood;
import net.minecraft.item.ItemPotion;
import net.minecraft.item.ItemStack;

public class KillauraCheck extends Check {
  private int useItemTicks = 0;
  private long lastEatTick = 0;
  private int violationLevel = 0;

  @Override
  public String getName() {
    return "Killaura";
  }

  @Override
  public void onUpdate(EntityPlayer player) {
    if (player.ridingEntity != null) return;
    
    long tick = mc.theWorld.getTotalWorldTime();
    ItemStack heldItem = player.getHeldItem();
    boolean isUsingItem = player.isUsingItem();
    boolean isConsumable = (heldItem != null && isConsumable(heldItem.getItem()));
    boolean isAttacking = (player.swingProgressInt > 0);
    
    if (isUsingItem && isConsumable) {
      useItemTicks++;
    } else {
      if (useItemTicks > 0) {
        lastEatTick = tick;
      }
      useItemTicks = 0;
    } 
    
    long sinceLastEat = tick - lastEatTick;
    
    if (isAttacking && useItemTicks > 6 && sinceLastEat < 33 && isConsumable) {
      violationLevel++;
      if (violationLevel >= 8) {
        flag(player, "");
        violationLevel = 0;
      }
    } else {
      if (violationLevel > 0) {
        violationLevel--;
      }
    } 
  }
  
  private boolean isConsumable(Item item) {
    return (item instanceof ItemFood || item instanceof ItemPotion || item instanceof ItemBucketMilk);
  }
}
