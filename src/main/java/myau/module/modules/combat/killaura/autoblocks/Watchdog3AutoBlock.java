package myau.module.modules.combat.killaura.autoblocks;

import java.util.concurrent.ThreadLocalRandom;
import myau.Myau;
import myau.enums.BlinkModules;
import myau.module.modules.combat.KillAura;
import myau.util.network.PacketUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.network.play.client.C07PacketPlayerDigging;
import net.minecraft.network.play.client.C08PacketPlayerBlockPlacement;
import net.minecraft.network.play.client.C09PacketHeldItemChange;
import net.minecraft.util.BlockPos;
import net.minecraft.util.EnumFacing;

public class Watchdog3AutoBlock extends AutoBlockMode {

  private static final Minecraft mc = Minecraft.getMinecraft();

  public Watchdog3AutoBlock(KillAura parent) {
    super("Watchdog 3", parent);
  }

  @Override
  public boolean processBlock(boolean attack, boolean block) {
    boolean swap = false;

    if (parent.hasValidTarget()) {
      if (!Myau.playerStateManager.digging && !Myau.playerStateManager.placing) {

        if (parent.blockTick == 0) {
          if (mc.thePlayer.getHeldItem() != null
              && mc.thePlayer.getHeldItem().getItem() instanceof net.minecraft.item.ItemSword) {
            // C09 slot spoof to reset block animation server-side
            int slotId = ThreadLocalRandom.current().nextInt(7) + 2;
            if (slotId == mc.thePlayer.inventory.currentItem) {
              slotId = (slotId + 1) % 9;
            }
            PacketUtil.sendPacket(new C09PacketHeldItemChange(slotId));
            PacketUtil.sendPacket(new C09PacketHeldItemChange(mc.thePlayer.inventory.currentItem));
            // C08 block
            PacketUtil.sendPacket(new C08PacketPlayerBlockPlacement(mc.thePlayer.getHeldItem()));
            mc.thePlayer.setItemInUse(
                mc.thePlayer.getHeldItem(), mc.thePlayer.getHeldItem().getMaxItemUseDuration());
            parent.blockingState = true;
          }
          parent.blockTick = 1;

        } else {
          // Stay blocked, no C07
          parent.blockTick = 0;
        }

        swap = true;

      } else {
        parent.blockTick = 0;
        swap = false;
      }

      Myau.blinkManager.setBlinkState(false, BlinkModules.AUTO_BLOCK);
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
      Myau.blinkManager.setBlinkState(false, BlinkModules.AUTO_BLOCK);
      parent.isBlocking = false;
      parent.fakeBlockState = false;
      parent.blockTick = 0;
    }

    return swap;
  }
}
