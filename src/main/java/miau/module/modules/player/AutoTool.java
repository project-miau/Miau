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
import miau.util.player.SlotUtil;
import net.minecraft.block.Block;
import net.minecraft.client.Minecraft;
import net.minecraft.util.BlockPos;

/**
 * Core logic ported from OpenRise AutoTool. Switches to the best tool when mining a block using
 * SlotUtil.findTool(BlockPos) (relies on vanilla getStrVsBlock — no double-counting of efficiency
 * enchantment).
 *
 * <p>Extra Miau features preserved: - spoofItem: visually shows the original item in first-person
 * while auto-tool is active - previousSlot tracking for spoofItem support in MixinItemRenderer
 */
public class AutoTool extends Module {

  private static final Minecraft mc = Minecraft.getMinecraft();

  private int blockBreak;
  private BlockPos blockPos;
  public int previousSlot = -1;

  public final BooleanProperty spoofItem = new BooleanProperty("spoof-item", false);

  public AutoTool() {
    super("AutoTool", false);
  }

  @EventTarget(Priority.HIGHEST)
  public void onBlockDamage(BlockDamageEvent event) {
    if (event.getPlayer() != mc.thePlayer
        || mc.thePlayer.getDistanceSq(
                event.getBlockPos().getX(), event.getBlockPos().getY(), event.getBlockPos().getZ())
            > 5 * 5) {
      return;
    }

    Block block = mc.theWorld.getBlockState(event.getBlockPos()).getBlock();
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
  public void onPreUpdate(UpdateEvent event) {
    if (event.getType() == EventType.PRE) {
      this.update();
    }
  }

  public void update() {
    if (mc.objectMouseOver == null || blockBreak <= 0 || blockPos == null) {
      return;
    }

    blockBreak--;

    int index = SlotUtil.findTool(blockPos);
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
    blockPos = null;
    resetSlot();
  }
}
