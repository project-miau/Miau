package miau.module.modules.movement.noslow;

import miau.event.impl.RightClickMouseEvent;
import miau.event.impl.UpdateEvent;
import miau.event.types.EventType;
import miau.module.modules.movement.NoSlow;
import miau.util.network.PacketUtil;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemBow;
import net.minecraft.item.ItemFood;
import net.minecraft.item.ItemPotion;
import net.minecraft.item.ItemSword;
import net.minecraft.network.play.client.C09PacketHeldItemChange;

public class OMWatchdogNoSlow extends NoSlowMode {
  private int offGroundTicks;
  private boolean stop;
  private boolean disable;

  public OMWatchdogNoSlow(String name, NoSlow parent) {
    super(name, parent);
  }

  @Override
  public void onUpdate(UpdateEvent event) {
    if (event.getType() != EventType.PRE) return;

    // Block-under check — reset disable khi đang đứng trên block
    if (mc.theWorld
                .getBlockState(
                    new net.minecraft.util.BlockPos(
                        mc.thePlayer.posX, mc.thePlayer.posY - 1, mc.thePlayer.posZ))
                .getBlock()
            != Blocks.air
        && !mc.thePlayer.isUsingItem()) {
      disable = false;
    }

    // Slab check — disable no-slow khi đang ở slab
    double posY = mc.thePlayer.posY;
    if (Math.abs(posY - Math.round(posY)) > 0.03 && mc.thePlayer.onGround) {
      disable = true;
    }

    // offGroundTicks tracking cho non-sword items (food/bow/potion)
    if (mc.thePlayer.isUsingItem()
        && !(mc.thePlayer.getHeldItem() != null
            && mc.thePlayer.getHeldItem().getItem() instanceof ItemSword)) {

      if (mc.thePlayer.onGround) {
        offGroundTicks = 0;
      } else {
        offGroundTicks++;
      }

      // offGroundTicks >= 2 → bắt đầu từ đây mới có thể eat trên không thành công
      if (offGroundTicks >= 2) {
        stop = false;
      } else if (mc.thePlayer.onGround && !disable) {
        // PosY anti-flag: làm lệch vị trí Y 1E-14 để WatchDog không phát hiện
        mc.thePlayer.posY += 1E-14;
      }
    }

    // Cancel movement slowdown cho food/bow/potion khi không bị disable
    if (!disable) {
      if (this.getParent().isFoodActive()
          || this.getParent().isBowActive()
          || this.getParent().isPotionActive()) {
        mc.thePlayer.movementInput.moveForward *= 5.0f;
        mc.thePlayer.movementInput.moveStrafe *= 5.0f;
      }
    }

    // Sword NoSlow: C09 swap + cancel slowdown (theo cách Rise)
    if (this.getParent().isSwordActive()) {
      int currentSlot = mc.thePlayer.inventory.currentItem;
      PacketUtil.sendPacket(
          new C09PacketHeldItemChange(currentSlot % 7 + (int) (Math.random() * 2) + 1));
      PacketUtil.sendPacket(new C09PacketHeldItemChange(currentSlot));
      mc.thePlayer.movementInput.moveForward *= 5.0f;
      mc.thePlayer.movementInput.moveStrafe *= 5.0f;
    }
  }

  @Override
  public void onRightClick(RightClickMouseEvent event) {
    if (mc.thePlayer.getHeldItem() == null) {
      return;
    }

    if (mc.thePlayer.isUsingItem()
        || (mc.thePlayer.getHeldItem().getItem() instanceof ItemPotion
            && !ItemPotion.isSplash(mc.thePlayer.getHeldItem().getMetadata()))
        || mc.thePlayer.getHeldItem().getItem() instanceof ItemFood
        || mc.thePlayer.getHeldItem().getItem() instanceof ItemBow) {

      if (offGroundTicks < 2 && offGroundTicks != 0 && !disable) {
        event.setCancelled(true);
      } else if (mc.thePlayer.onGround) {
        mc.thePlayer.jump();
        event.setCancelled(true);
      }
    }
  }
}
