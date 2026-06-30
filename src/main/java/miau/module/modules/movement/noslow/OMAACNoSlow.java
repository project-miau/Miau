package miau.module.modules.movement.noslow;

import miau.event.impl.RightClickMouseEvent;
import miau.event.impl.UpdateEvent;
import miau.event.types.EventType;
import miau.module.modules.movement.NoSlow;
import miau.util.network.PacketUtil;
import net.minecraft.network.play.client.C08PacketPlayerBlockPlacement;
import net.minecraft.network.play.client.C09PacketHeldItemChange;

public class OMAACNoSlow extends NoSlowMode {

  public OMAACNoSlow(String name, NoSlow parent) {
    super(name, parent);
  }

  @Override
  public void onUpdate(UpdateEvent event) {
    if (this.getParent().isSwordActive()) {
      if (event.getType() == EventType.PRE) {
        int currentSlot = mc.thePlayer.inventory.currentItem;
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
  public void onRightClick(RightClickMouseEvent event) {}
}
