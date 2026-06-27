package myau.module.modules.combat.killaura.autoblocks;

import myau.Myau;
import myau.enums.BlinkModules;
import myau.mixin.IAccessorPlayerControllerMP;
import myau.module.modules.combat.KillAura;
import myau.util.network.PacketUtil;
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
    if (parent.hasValidTarget()) {
      int item = ((IAccessorPlayerControllerMP) mc.playerController).getCurrentPlayerItem();
      if (Myau.playerStateManager.digging
          || Myau.playerStateManager.placing
          || mc.thePlayer.inventory.currentItem != item
          || parent.isPlayerBlocking() && parent.blockTick != 0
          || parent.attackDelayMS > 0L && parent.attackDelayMS <= 50L) {
        parent.blockTick = 0;
      } else {
        int slot = parent.findEmptySlot(item);
        PacketUtil.sendPacket(new C09PacketHeldItemChange(slot));
        PacketUtil.sendPacket(new C09PacketHeldItemChange(item));
        swap = true;
        parent.blockTick = 1;
      }
      Myau.blinkManager.setBlinkState(false, BlinkModules.AUTO_BLOCK);
      parent.isBlocking = true;
      parent.fakeBlockState = false;
    } else {
      Myau.blinkManager.setBlinkState(false, BlinkModules.AUTO_BLOCK);
      parent.isBlocking = false;
      parent.fakeBlockState = false;
    }
    return swap;
  }
}
