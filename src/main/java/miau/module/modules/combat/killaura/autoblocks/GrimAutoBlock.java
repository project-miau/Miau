package miau.module.modules.combat.killaura.autoblocks;

import miau.Miau;
import miau.enums.BlinkModules;
import miau.module.modules.combat.KillAura;
import miau.util.network.PacketUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.network.play.client.C08PacketPlayerBlockPlacement;
import net.minecraft.network.play.client.C09PacketHeldItemChange;

public class GrimAutoBlock extends AutoBlockMode {
  private static final Minecraft mc = Minecraft.getMinecraft();

  public GrimAutoBlock(KillAura parent) {
    super("GRIM", parent);
  }

  @Override
  public boolean processBlock(boolean attack, boolean block) {
    boolean swap = false;
    if (parent.hasValidTarget()) {
      if (!parent.isPlayerBlocking()
          && !Miau.playerStateManager.digging
          && !Miau.playerStateManager.placing) {
        if (mc.thePlayer.getHeldItem() != null
            && mc.thePlayer.getHeldItem().getItem() instanceof net.minecraft.item.ItemSword) {
          int currentItem = mc.thePlayer.inventory.currentItem;
          int nextSlot = (currentItem + 1) % 9;
          PacketUtil.sendPacket(new C09PacketHeldItemChange(nextSlot));
          PacketUtil.sendPacket(new C09PacketHeldItemChange(currentItem));
          PacketUtil.sendPacket(new C08PacketPlayerBlockPlacement(mc.thePlayer.getHeldItem()));
        }
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
