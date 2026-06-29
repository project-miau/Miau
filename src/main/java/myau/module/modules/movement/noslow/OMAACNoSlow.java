package myau.module.modules.movement.noslow;

import myau.event.impl.RightClickMouseEvent;
import myau.event.impl.UpdateEvent;
import myau.event.types.EventType;
import myau.module.modules.movement.NoSlow;
import myau.util.network.PacketUtil;
import net.minecraft.network.play.client.C08PacketPlayerBlockPlacement;
import net.minecraft.network.play.client.C09PacketHeldItemChange;

/**
 * Rise 6 AAC NoSlow bypass.
 *
 * <p><b>Tech:</b> AAC check block cooldown nên cần C09 slot swap để reset animation state. Khác với
 * NCP: dùng C09 swap (sang slot khác rồi về) + C08 block, thay vì C07/C08 cycle. AAC detect "C08
 * block spam" qua chat, swap slot giúp reset server-side block cooldown mà AAC không chú ý tới.
 *
 * <p><b>Flow:</b>
 *
 * <pre>
 *   PRE:  C09 swap → C09 swap back → C08 block
 *   POST: (nothing)
 * </pre>
 *
 * <p>Giống Intave/NewNCP nhưng không có timer disable check.
 */
public class OMAACNoSlow extends NoSlowMode {

  public OMAACNoSlow(String name, NoSlow parent) {
    super(name, parent);
  }

  @Override
  public void onUpdate(UpdateEvent event) {
    if (this.getParent().isSwordActive()) {
      if (event.getType() == EventType.PRE) {
        int currentSlot = mc.thePlayer.inventory.currentItem;
        // Slot swap để reset animation cooldown phía server
        PacketUtil.sendPacket(new C09PacketHeldItemChange(currentSlot % 8 + 1));
        PacketUtil.sendPacket(new C09PacketHeldItemChange(currentSlot));
        if (mc.thePlayer.getHeldItem() != null
            && mc.thePlayer.getHeldItem().getItem() instanceof net.minecraft.item.ItemSword) {
          PacketUtil.sendPacket(new C08PacketPlayerBlockPlacement(mc.thePlayer.getHeldItem()));
        }
      }
    }

    if (this.getParent().isAnyActive()) {
      float multiplier = this.getParent().getMotionMultiplier();
      mc.thePlayer.movementInput.moveForward *= multiplier;
      mc.thePlayer.movementInput.moveStrafe *= multiplier;
      if (!this.getParent().canSprint()) {
        mc.thePlayer.setSprinting(false);
      }
    }
  }

  @Override
  public void onRightClick(RightClickMouseEvent event) {
    // AAC phản ứng chậm với right-click, không cần cancel
  }
}
