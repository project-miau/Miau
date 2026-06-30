package miau.module.modules.combat.killaura.autoblocks;

import miau.Miau;
import miau.enums.BlinkModules;
import miau.module.modules.combat.KillAura;
import miau.util.network.PacketUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.network.play.client.C07PacketPlayerDigging;
import net.minecraft.network.play.client.C08PacketPlayerBlockPlacement;
import net.minecraft.util.BlockPos;
import net.minecraft.util.EnumFacing;

public class WatchdogAutoBlock extends AutoBlockMode {

  private static final Minecraft mc = Minecraft.getMinecraft();
  private boolean hasBlocked = false;

  public WatchdogAutoBlock(KillAura parent) {
    super("Watchdog", parent);
  }

  @Override
  public boolean processBlock(boolean attack, boolean block) {
    boolean swap = false;

    if (parent.hasValidTarget()) {
      if (!Miau.playerStateManager.digging && !Miau.playerStateManager.placing) {

        if (!hasBlocked) {
          if (mc.thePlayer.getHeldItem() != null
              && mc.thePlayer.getHeldItem().getItem() instanceof net.minecraft.item.ItemSword) {
            PacketUtil.sendPacket(new C08PacketPlayerBlockPlacement(mc.thePlayer.getHeldItem()));
            mc.thePlayer.setItemInUse(
                mc.thePlayer.getHeldItem(), mc.thePlayer.getHeldItem().getMaxItemUseDuration());
            parent.blockingState = true;
          }
          hasBlocked = true;
        }

        parent.blockTick = 1;
        swap = true;

      } else {
        hasBlocked = false;
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
      hasBlocked = false;
      Miau.blinkManager.setBlinkState(false, BlinkModules.AUTO_BLOCK);
      parent.isBlocking = false;
      parent.fakeBlockState = false;
      parent.blockTick = 0;
    }

    return swap;
  }
}
