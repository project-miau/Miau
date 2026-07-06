package miau.module.modules.misc.disabler;

import miau.event.impl.PacketEvent;
import miau.event.impl.TickEvent;
import miau.event.types.EventType;
import miau.module.modules.misc.Disabler;
import miau.util.network.PacketUtil;
import net.minecraft.network.play.client.C03PacketPlayer;
import net.minecraft.network.play.server.S08PacketPlayerPosLook;
import net.minecraft.util.AxisAlignedBB;

/**
 * NoRules disabler: position flag bypass by jumping into blocks. After sending a flag packet,
 * cancels the server teleport response. Ported from OpenRise (Rise 6)
 */
public class NoRulesDisabler extends DisablerMode {

  private boolean started, teleport;

  public NoRulesDisabler(String name, Disabler parent) {
    super(name, parent);
  }

  @Override
  public void onEnable() {
    started = false;
    teleport = false;
  }

  @Override
  public void onTick(TickEvent event) {
    if (event.getType() != EventType.PRE) return;
    if (mc.thePlayer == null || mc.theWorld == null) return;

    AxisAlignedBB bb = mc.thePlayer.getEntityBoundingBox().offset(0, 1, 0);

    if (started) {
      mc.thePlayer.motionY += 0.025;
    }

    if (mc.theWorld.getCollidingBoundingBoxes(mc.thePlayer, bb).isEmpty() && !started) {
      started = true;
      mc.thePlayer.jump();
      PacketUtil.sendPacketNoEvent(
          new C03PacketPlayer.C06PacketPlayerPosLook(
              mc.thePlayer.posX,
              mc.thePlayer.posY - 0.1,
              mc.thePlayer.posZ,
              mc.thePlayer.rotationYaw,
              mc.thePlayer.rotationPitch,
              false));
      teleport = true;
    }
  }

  @Override
  public void onPacket(PacketEvent event) {
    // Cancel the server teleport response
    if (event.getType() == EventType.RECEIVE
        && event.getPacket() instanceof S08PacketPlayerPosLook) {
      if (teleport) {
        event.setCancelled(true);
        teleport = false;
      }
    }
  }
}
