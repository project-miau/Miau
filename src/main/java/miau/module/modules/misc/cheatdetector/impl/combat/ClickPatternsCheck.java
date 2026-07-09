package miau.module.modules.misc.cheatdetector.impl.combat;

import miau.event.impl.PacketEvent;
import miau.module.modules.misc.cheatdetector.Check;
import net.minecraft.entity.player.EntityPlayer;

public class ClickPatternsCheck extends Check {
  @Override
  public String getName() {
    return "ClickPatternsCheck";
  }

  private int ticksSinceSwing = 0;
  private final java.util.List<Integer> delays = new java.util.ArrayList<>();
  private boolean lastSwing = false;

  @Override
  public void onUpdate(EntityPlayer player) {
    ticksSinceSwing++;
    if (player.isSwingInProgress && !lastSwing) {
      delays.add(ticksSinceSwing);
      if (delays.size() > 15) {
        delays.remove(0);
        boolean consistent = true;
        int first = delays.get(0);
        for (int d : delays) {
          if (d != first) {
            consistent = false;
            break;
          }
        }
        if (consistent && first < 10) {
          flag(player, "Consistent clicking (Delay: " + first + " ticks)");
          delays.clear();
        }
      }
      ticksSinceSwing = 0;
    }
    lastSwing = player.isSwingInProgress;
  }

  @Override
  public void onPacket(PacketEvent event, EntityPlayer player) {}
}
