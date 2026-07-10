package miau.module.modules.combat.killaura.autoblocks;

import miau.Miau;
import miau.component.PingSpoofComponent;
import miau.mixin.IAccessorPlayerControllerMP;
import miau.module.modules.combat.KillAura;
import net.minecraft.client.Minecraft;
import net.minecraft.util.MovingObjectPosition;

public class UniversalAutoBlock extends AutoBlockMode {

  private static final Minecraft mc = Minecraft.getMinecraft();

  public UniversalAutoBlock(KillAura parent) {
    super("UNIVERSAL", parent);
  }

  @Override
  public boolean processBlock(boolean attack, boolean block) {
    boolean swap = false;
    if (mc.objectMouseOver != null
        && mc.objectMouseOver.typeOfHit == MovingObjectPosition.MovingObjectType.BLOCK
        && ((IAccessorPlayerControllerMP) mc.playerController).getCurBlockDamageMP() != 0) {
      parent.blockTick = 0;
      parent.isBlocking = false;
      parent.fakeBlockState = false;
      return false;
    }

    parent.blockTick++;

    if (parent.blockTick > 5) {
      parent.blockTick = 2;
    }

    PingSpoofComponent.spoof(99999, false, false, false, false, true);

    switch (parent.blockTick) {
      case 2:
        if (!parent.isPlayerBlocking()
            && !Miau.playerStateManager.digging
            && !Miau.playerStateManager.placing) {
          swap = true;
        }
        parent.cancelAttack = true;
        break;

      case 3:
        if (parent.isPlayerBlocking()) {
          parent.stopBlock();
        }
        parent.cancelAttack = true;
        break;

      default:
        parent.cancelAttack = false;
        break;
    }

    parent.isBlocking = true;
    parent.fakeBlockState = false;

    return swap;
  }

  @Override
  public void onPostUpdate() {
    if (parent.blockTick == 2) {
      PingSpoofComponent.dispatch();
    }
  }
}
