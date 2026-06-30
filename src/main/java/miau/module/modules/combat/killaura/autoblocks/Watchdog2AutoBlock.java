package miau.module.modules.combat.killaura.autoblocks;

import java.util.concurrent.ThreadLocalRandom;
import miau.Miau;
import miau.enums.BlinkModules;
import miau.module.modules.combat.KillAura;
import miau.util.network.PacketUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.network.play.client.C07PacketPlayerDigging;
import net.minecraft.network.play.client.C08PacketPlayerBlockPlacement;
import net.minecraft.network.play.client.C09PacketHeldItemChange;
import net.minecraft.util.BlockPos;
import net.minecraft.util.EnumFacing;

public class Watchdog2AutoBlock extends AutoBlockMode {

  private static final Minecraft mc = Minecraft.getMinecraft();

  public Watchdog2AutoBlock(KillAura parent) {
    super("Watchdog 2", parent);
  }

  @Override
  public boolean processBlock(boolean attack, boolean block) {
    boolean swap = false;

    if (parent.hasValidTarget()) {
      if (!Miau.playerStateManager.digging && !Miau.playerStateManager.placing) {

        if (parent.blockTick == 0) {
          // C08 block
          if (mc.thePlayer.getHeldItem() != null
              && mc.thePlayer.getHeldItem().getItem() instanceof net.minecraft.item.ItemSword) {
            PacketUtil.sendPacket(new C08PacketPlayerBlockPlacement(mc.thePlayer.getHeldItem()));
            mc.thePlayer.setItemInUse(
                mc.thePlayer.getHeldItem(), mc.thePlayer.getHeldItem().getMaxItemUseDuration());
            parent.blockingState = true;
          }
          parent.blockTick = 1;

        } else {
          // C09 noise (random slot swap)
          int slotId = ThreadLocalRandom.current().nextInt(7) + 2;
          PacketUtil.sendPacket(new C09PacketHeldItemChange(slotId));
          PacketUtil.sendPacket(new C09PacketHeldItemChange(mc.thePlayer.inventory.currentItem));
          parent.blockTick = 0;
        }

        swap = true;

      } else {
        parent.blockTick = 0;
        swap = false;
      }

      Miau.blinkManager.setBlinkState(false, BlinkModules.AUTO_BLOCK);
      parent.isBlocking = true;
      parent.fakeBlockState = false;

    } else {
      if (parent.isPlayerBlocking()) {
        PacketUtil.sendPacket(
            new C07PacketPlayerDigging(
                C07PacketPlayerDigging.Action.RELEASE_USE_ITEM, BlockPos.ORIGIN, EnumFacing.DOWN));
        mc.thePlayer.stopUsingItem();
        parent.blockingState = false;
      }
      Miau.blinkManager.setBlinkState(false, BlinkModules.AUTO_BLOCK);
      parent.isBlocking = false;
      parent.fakeBlockState = false;
      parent.blockTick = 0;
    }

    return swap;
  }
}
