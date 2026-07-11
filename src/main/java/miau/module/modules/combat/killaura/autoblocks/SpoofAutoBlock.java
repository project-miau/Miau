package miau.module.modules.combat.killaura.autoblocks;

import miau.Miau;
import miau.enums.BlinkModules;
import miau.mixin.IAccessorPlayerControllerMP;
import miau.module.modules.combat.KillAura;
import miau.util.network.PacketUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.network.play.client.C09PacketHeldItemChange;

public class SpoofAutoBlock extends AutoBlockMode {
  private static final Minecraft mc = Minecraft.getMinecraft();

  public SpoofAutoBlock(KillAura parent) {
    super("SPOOF", parent);
  }

  @Override
  public boolean processBlock(boolean attack, boolean block) {
    boolean swap = false;
    if (parent.getTarget() != null) {
      int item = ((IAccessorPlayerControllerMP) mc.playerController).getCurrentPlayerItem();
      if (Miau.playerStateManager.digging
          || Miau.playerStateManager.placing
          || mc.thePlayer.inventory.currentItem != item
          || parent.isPlayerBlocking() && parent.blockTick != 0
          || parent.nextSwing > 0L && parent.nextSwing <= 50L) {
        parent.blockTick = 0;
      } else {
        int slot = parent.findEmptySlot(item);
        PacketUtil.sendPacket(new C09PacketHeldItemChange(slot));
        PacketUtil.sendPacket(new C09PacketHeldItemChange(item));
        swap = true;
        parent.blockTick = 1;
      }
      Miau.blinkManager.setBlinkState(false, BlinkModules.AUTO_BLOCK);
      parent.isBlocking = true;
      parent.fakeBlockState = false;
    } else {
      Miau.blinkManager.setBlinkState(false, BlinkModules.AUTO_BLOCK);
      parent.isBlocking = false;
      parent.fakeBlockState = false;
    }
    return swap;
  }
}
