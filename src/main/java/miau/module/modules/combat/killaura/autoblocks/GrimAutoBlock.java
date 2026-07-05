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
    if (parent.hasValidTarget()) {
      int currentItem = mc.thePlayer.inventory.currentItem;
      int nextSlot = (currentItem + 1) % 9;
      PacketUtil.sendPacket(new C09PacketHeldItemChange(nextSlot));
      PacketUtil.sendPacket(new C09PacketHeldItemChange(currentItem));

      if (mc.thePlayer.getHeldItem() != null) {
        PacketUtil.sendPacket(new C08PacketPlayerBlockPlacement(mc.thePlayer.getHeldItem()));
      }

      Miau.blinkManager.setBlinkState(false, BlinkModules.AUTO_BLOCK);
      parent.isBlocking = true;
      parent.fakeBlockState = false;
    } else {
      parent.isBlocking = false;
      parent.fakeBlockState = false;
    }
    return false;
  }
}
