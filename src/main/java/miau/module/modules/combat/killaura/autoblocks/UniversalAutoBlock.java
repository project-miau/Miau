package miau.module.modules.combat.killaura.autoblocks;

import miau.Miau;
import miau.component.PingSpoofComponent;
import miau.enums.BlinkModules;
import miau.module.modules.combat.KillAura;
import miau.property.properties.IntProperty;

public class UniversalAutoBlock extends AutoBlockMode {

  public final IntProperty delay = new IntProperty("delay", 150, 50, 500);

  public UniversalAutoBlock(KillAura parent) {
    super("UNIVERSAL", parent);
  }

  @Override
  public boolean processBlock(boolean attack, boolean block) {
    boolean swap = false;

    if (parent.hasValidTarget()) {
      if (!Miau.playerStateManager.digging && !Miau.playerStateManager.placing) {

        if (parent.blockTick == 0) {
          // Phase 1: Enable PingSpoof for regular category (C0F/C00)
          // Delaying transaction packets desyncs anticheat tick-state tracking
          // while C02 (attack) and C08 (block) go through immediately
          PingSpoofComponent.spoof(delay.getValue(), true, false, false, false, false, false);
          parent.blockTick = 1;
          swap = true;

        } else {
          // Phase 2: Disable PingSpoof, flush delayed transactions
          PingSpoofComponent.disable();
          parent.blockTick = 0;
          swap = true;
        }

        Miau.blinkManager.setBlinkState(false, BlinkModules.AUTO_BLOCK);
        parent.isBlocking = true;
        parent.fakeBlockState = true;

      } else {
        parent.blockTick = 0;
        swap = false;
      }

    } else {
      PingSpoofComponent.disable();
      Miau.blinkManager.setBlinkState(false, BlinkModules.AUTO_BLOCK);
      parent.isBlocking = false;
      parent.fakeBlockState = false;
      parent.blockTick = 0;
    }

    return swap;
  }
}
