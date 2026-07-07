package miau.module.modules.player;

import miau.Miau;
import miau.event.EventTarget;
import miau.event.impl.BlockBreakEvent;
import miau.event.impl.BlockDamageEvent;
import miau.event.impl.UpdateEvent;
import miau.event.types.EventType;
import miau.event.types.Priority;
import miau.module.Module;
import miau.property.properties.BooleanProperty;
import miau.util.world.BlockUtil;
import net.minecraft.block.Block;
import net.minecraft.client.Minecraft;
import net.minecraft.item.ItemStack;
import net.minecraft.util.BlockPos;

public class AutoTool extends Module {
  private static final Minecraft mc = Minecraft.getMinecraft();

  private int blockBreak;
  private BlockPos blockPos;
  public int previousSlot = -1;

  public AutoTool() {
    super("AutoTool", false);
  }

  @EventTarget(Priority.HIGHEST)
  public void onBlockDamage(BlockDamageEvent event) {
    if (mc.thePlayer == null || event.getPlayer() != mc.thePlayer) return;
    if (mc.thePlayer.getDistanceSq(
            event.getBlockPos().getX(), event.getBlockPos().getY(), event.getBlockPos().getZ())
        > 25.0D) {
      return;
    }
    Block block = BlockUtil.getBlock(event.getBlockPos());
    if (block instanceof net.minecraft.block.BlockEnderChest
        || block instanceof net.minecraft.block.BlockChest) {
      return;
    }
    blockBreak = 15;
    blockPos = event.getBlockPos();
    this.update();
  }

  @EventTarget(Priority.HIGHEST)
  public void onBlockBreak(BlockBreakEvent event) {
    blockBreak = 0;
    resetSlot();
  }

  @EventTarget(Priority.HIGHEST)
  public void onUpdate(UpdateEvent event) {
    if (event.getType() == EventType.PRE) {
      this.update();
    }
  }

  public void update() {
    if (mc.objectMouseOver == null || blockBreak <= 0 || blockPos == null) {
      return;
    }

    blockBreak--;

    Block block = BlockUtil.getBlock(blockPos);
    if (block instanceof net.minecraft.block.BlockEnderChest
        || block instanceof net.minecraft.block.BlockChest) {
      return;
    }

    int index = getTool(block);
    if (index != -1) {
      if (previousSlot == -1 && index != Miau.slotComponent.getItemIndex()) {
        previousSlot = Miau.slotComponent.getItemIndex();
      }
      Miau.slotComponent.setSlot(index);
    }
  }

  private void resetSlot() {
    if (previousSlot != -1) {
      Miau.slotComponent.setSlot(previousSlot);
    }
    previousSlot = -1;
  }

  @Override
  public void onDisabled() {
    blockBreak = 0;
    resetSlot();
  }

  public static int getTool(Block block) {
    double bestScore = 1.0D;
    int bestSlot = -1;
    for (int i = 0; i < net.minecraft.entity.player.InventoryPlayer.getHotbarSize(); ++i) {
      ItemStack stack = mc.thePlayer.inventory.getStackInSlot(i);
      if (stack != null) {
        double score = getBlockBreakingScore(stack, block);
        if (score > bestScore) {
          bestScore = score;
          bestSlot = i;
        }
      }
    }
    return bestSlot;
  }

  public static double getBlockBreakingScore(ItemStack stack, Block block) {
    float speed = stack.getStrVsBlock(block);
    if (speed > 1.0F) {
      int efficiency =
          net.minecraft.enchantment.EnchantmentHelper.getEnchantmentLevel(
              net.minecraft.enchantment.Enchantment.efficiency.effectId, stack);
      if (efficiency > 0) {
        speed += efficiency * efficiency + 1;
      }
    }
    return speed;
  }
}
