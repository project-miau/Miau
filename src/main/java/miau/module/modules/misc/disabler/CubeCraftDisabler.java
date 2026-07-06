package miau.module.modules.misc.disabler;

import miau.event.impl.TickEvent;
import miau.event.types.EventType;
import miau.module.modules.misc.Disabler;
import miau.util.network.PacketUtil;
import net.minecraft.network.play.client.C07PacketPlayerDigging;
import net.minecraft.util.BlockPos;
import net.minecraft.util.EnumFacing;

/** CubeCraft disabler: ground spoof + anti-kick block break Ported from OpenRise (Rise 6) */
public class CubeCraftDisabler extends DisablerMode {

  public CubeCraftDisabler(String name, Disabler parent) {
    super(name, parent);
  }

  @Override
  public void onTick(TickEvent event) {
    if (event.getType() != EventType.PRE) return;
    if (mc.thePlayer == null || mc.theWorld == null) return;

    // Ground spoof: if mid-air too long, snap to ground
    if (!mc.thePlayer.onGround && mc.thePlayer.ticksExisted % 20 > 13) {
      mc.thePlayer.motionY = -0.1;
    }

    // Anti-kick block break packet every 5 ticks
    if (mc.thePlayer.ticksExisted % 5 == 0) {
      PacketUtil.sendPacketNoEvent(
          new C07PacketPlayerDigging(
              C07PacketPlayerDigging.Action.STOP_DESTROY_BLOCK,
              new BlockPos(mc.thePlayer),
              EnumFacing.UP));
    }
  }
}
