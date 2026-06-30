package miau.module.modules.combat.killaura.autoblocks;

import java.util.Random;
import miau.Miau;
import miau.enums.BlinkModules;
import miau.module.modules.combat.KillAura;
import miau.module.modules.movement.NoSlow;
import miau.util.network.PacketUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.network.play.client.C09PacketHeldItemChange;

public class HypixelAutoBlock extends AutoBlockMode {
  private static final Minecraft mc = Minecraft.getMinecraft();

  public HypixelAutoBlock(KillAura parent) {
    super("HYPIXEL", parent);
  }

  @Override
  public boolean processBlock(boolean attack, boolean block) {
    boolean swap = false;
    if (parent.hasValidTarget()) {
      if (!Miau.playerStateManager.digging && !Miau.playerStateManager.placing) {
        switch (parent.blockTick) {
          case 0:
            if (!parent.isPlayerBlocking()) {
              swap = true;
            }
            parent.blockTick = 1;
            break;
          case 1:
            if (parent.isPlayerBlocking()) {
              NoSlow noSlow = (NoSlow) Miau.moduleManager.modules.get(NoSlow.class);
              if (noSlow.isEnabled() && !parent.isNoSlowAntiSwitchActive()) {
                int randomSlot = new Random().nextInt(9);
                while (randomSlot == mc.thePlayer.inventory.currentItem) {
                  randomSlot = new Random().nextInt(9);
                }
                PacketUtil.sendPacket(new C09PacketHeldItemChange(randomSlot));
                PacketUtil.sendPacket(
                    new C09PacketHeldItemChange(mc.thePlayer.inventory.currentItem));
              }
              parent.stopBlock();
              parent.cancelAttack = true;
            }
            if (parent.attackDelayMS <= 50L) {
              parent.blockTick = 0;
            }
            break;
          default:
            parent.blockTick = 0;
        }
      }
      parent.isBlocking = true;
      parent.fakeBlockState = true;
    } else {
      Miau.blinkManager.setBlinkState(false, BlinkModules.AUTO_BLOCK);
      parent.isBlocking = false;
      parent.fakeBlockState = false;
    }
    return swap;
  }
}
