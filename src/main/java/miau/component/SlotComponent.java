package miau.component;

import miau.event.EventTarget;
import miau.event.impl.TickEvent;
import miau.event.types.EventType;
import miau.event.types.Priority;
import miau.util.player.IInventoryPlayerAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.item.Item;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;

public final class SlotComponent {

  private static final Minecraft mc = Minecraft.getMinecraft();

  public void setSlot(int slot) {
    if (mc.thePlayer == null) return;
    if (slot < 0 || slot > 8) return;

    IInventoryPlayerAccessor inv = (IInventoryPlayerAccessor) mc.thePlayer.inventory;
    inv.miau$setAlternativeSlot(true);
    inv.miau$setAlternativeCurrentItem(slot);
  }

  public int getItemIndex() {
    if (mc.thePlayer == null) return 0;
    IInventoryPlayerAccessor inv = (IInventoryPlayerAccessor) mc.thePlayer.inventory;
    return inv.miau$getAlternativeSlot()
        ? inv.miau$getAlternativeCurrentItem()
        : mc.thePlayer.inventory.currentItem;
  }

  public ItemStack getItemStack() {
    if (mc.thePlayer == null || mc.thePlayer.inventoryContainer == null) return null;
    int index = getItemIndex();
    return mc.thePlayer.inventoryContainer.getSlot(index + 36).getStack();
  }

  public Item getItem() {
    ItemStack stack = getItemStack();
    return stack == null ? null : stack.getItem();
  }

  public boolean isHoldingBlock() {
    return getItem() instanceof ItemBlock;
  }

  @EventTarget(Priority.HIGHEST)
  public void onTickReset(TickEvent event) {
    if (event.getType() != EventType.PRE) return;
    if (mc.thePlayer == null) return;

    IInventoryPlayerAccessor inv = (IInventoryPlayerAccessor) mc.thePlayer.inventory;
    inv.miau$setAlternativeSlot(false);
    inv.miau$setAlternativeCurrentItem(mc.thePlayer.inventory.currentItem);
  }
}
