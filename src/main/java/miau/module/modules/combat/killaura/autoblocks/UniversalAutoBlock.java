package miau.module.modules.combat.killaura.autoblocks;

import miau.Miau;
import miau.component.PingSpoofComponent;
import miau.mixin.IAccessorPlayerControllerMP;
import miau.module.modules.combat.KillAura;
import net.minecraft.client.Minecraft;
import net.minecraft.util.MovingObjectPosition;

/**
 * Universal AutoBlock — ported from Rise 6.2.4 KillAura.
 *
 * <p>Blocks/unblocks in a 5-tick cycle using PingSpoof blink to make the blocking pattern appear
 * legitimate. Tick sequence:
 *
 * <ul>
 *   <li>Tick 1: Start PingSpoof blink delay (delays blink-category packets)
 *   <li>Tick 2: Send C08 block packet
 *   <li>Tick 3: Send C07 unblock packet + dispatch blink
 *   <li>Tick 4: Attack allowed
 *   <li>Tick 5+: (wraps back to tick 2)
 * </ul>
 *
 * <p>Rise reference methods ported from KillAura.java:
 *
 * <ul>
 *   <li>{@code preBlock()} → {@link #processBlock}
 *   <li>{@code postBlock()} → {@link #onPostUpdate}
 *   <li>{@code cantPreBlock()} → automatic via blockTick=0 reset in parent
 *   <li>{@code clickDelayBlock()} → handled by {@link #cancelAttack} in parent
 * </ul>
 */
public class UniversalAutoBlock extends AutoBlockMode {

  private static final Minecraft mc = Minecraft.getMinecraft();

  public UniversalAutoBlock(KillAura parent) {
    super("UNIVERSAL", parent);
  }

  @Override
  public boolean processBlock(boolean attack, boolean block) {
    boolean swap = false;

    // ── If mining an interactable block, reset the cycle ────────────
    // Rise: if (curBlockDamageMP != 0 && mouseOver.type == BLOCK) → reset
    if (mc.objectMouseOver != null
        && mc.objectMouseOver.typeOfHit == MovingObjectPosition.MovingObjectType.BLOCK
        && ((IAccessorPlayerControllerMP) mc.playerController).getCurBlockDamageMP() != 0) {
      parent.blockTick = 0;
      parent.isBlocking = false;
      parent.fakeBlockState = false;
      return false;
    }

    parent.blockTick++;

    // Rise: if (blockTicks > 5) blockTicks = 2;
    if (parent.blockTick > 5) {
      parent.blockTick = 2;
    }

    // ── Start PingSpoof blink for all blink-category packets ───────
    // Rise: PingSpoofComponent.spoof(99999, false, false, false, false, true);
    PingSpoofComponent.spoof(99999, false, false, false, false, true);

    switch (parent.blockTick) {
      case 2:
        // Rise: this.block(false, true) → sends C08 + interacts with entity
        if (!parent.isPlayerBlocking()
            && !Miau.playerStateManager.digging
            && !Miau.playerStateManager.placing) {
          swap = true;
        }
        // Rise: delay = 500 when blockTicks < 4 → prevent attack during cycle
        parent.cancelAttack = true;
        break;

      case 3:
        // Rise: this.unblock(false) → sends C07 release
        if (parent.isPlayerBlocking()) {
          parent.stopBlock();
        }
        parent.cancelAttack = true;
        break;

      default:
        // Tick 1 or 4+ — allow attack (Rise: blockTicks >= 4 → delay = -1)
        parent.cancelAttack = false;
        break;
    }

    parent.isBlocking = true;
    parent.fakeBlockState = false;

    return swap;
  }

  @Override
  public void onPostUpdate() {
    // Rise postBlock(): if (blockTicks == 2) → PingSpoofComponent.dispatch()
    if (parent.blockTick == 2) {
      PingSpoofComponent.dispatch();
    }
  }
}
