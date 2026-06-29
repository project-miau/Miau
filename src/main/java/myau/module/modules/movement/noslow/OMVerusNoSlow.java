package myau.module.modules.movement.noslow;

import myau.event.impl.UpdateEvent;
import myau.event.types.EventType;
import myau.module.modules.movement.NoSlow;
import myau.util.network.PacketUtil;
import net.minecraft.network.play.client.C07PacketPlayerDigging;
import net.minecraft.network.play.client.C08PacketPlayerBlockPlacement;
import net.minecraft.util.BlockPos;
import net.minecraft.util.EnumFacing;

/**
 * Rise 6 Verus NoSlow bypass.
 *
 * <p><b>Tech:</b> Verus AC detect block spam nên cần timing chính xác giữa C08 và C07. Gửi C07
 * release ở PRE tick (trước khi move), gửi C08 block ở POST tick (sau khi move). Verus check
 * "blocking while moving" nên pattern PRE-release/POST-block giúp Verus thấy player không block khi
 * đang di chuyển.
 *
 * <p><b>Flow:</b>
 *
 * <pre>
 *   PRE:  C07 release → move normally
 *   POST: C08 block → re-block
 * </pre>
 *
 * <p>Giống NCP mode nhưng đảo chiều PRE/POST để qua mặt Verus.
 */
public class OMVerusNoSlow extends NoSlowMode {

  public OMVerusNoSlow(String name, NoSlow parent) {
    super(name, parent);
  }

  @Override
  public void onUpdate(UpdateEvent event) {
    if (this.getParent().isSwordActive()) {
      if (event.getType() == EventType.PRE) {
        // PRE: Release item để Verus thấy player không block khi moving
        PacketUtil.sendPacket(
            new C07PacketPlayerDigging(
                C07PacketPlayerDigging.Action.RELEASE_USE_ITEM, BlockPos.ORIGIN, EnumFacing.DOWN));
      } else {
        // POST: Re-block ngay sau khi move
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
