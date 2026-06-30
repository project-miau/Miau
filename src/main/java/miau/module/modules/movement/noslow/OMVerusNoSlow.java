package miau.module.modules.movement.noslow;

import miau.event.impl.UpdateEvent;
import miau.event.types.EventType;
import miau.module.modules.movement.NoSlow;
import miau.util.network.PacketUtil;
import net.minecraft.network.play.client.C07PacketPlayerDigging;
import net.minecraft.network.play.client.C08PacketPlayerBlockPlacement;
import net.minecraft.util.BlockPos;
import net.minecraft.util.EnumFacing;

public class OMVerusNoSlow extends NoSlowMode {

  public OMVerusNoSlow(String name, NoSlow parent) {
    super(name, parent);
  }

  @Override
  public void onUpdate(UpdateEvent event) {
    if (this.getParent().isSwordActive()) {
      if (event.getType() == EventType.PRE) {
        PacketUtil.sendPacket(
            new C07PacketPlayerDigging(
                C07PacketPlayerDigging.Action.RELEASE_USE_ITEM, BlockPos.ORIGIN, EnumFacing.DOWN));
      } else {
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
}
