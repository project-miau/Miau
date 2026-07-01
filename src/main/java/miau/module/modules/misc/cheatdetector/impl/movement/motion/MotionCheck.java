package miau.module.modules.misc.cheatdetector.impl.movement.motion;

import java.util.Arrays;
import java.util.List;
import miau.event.impl.PacketEvent;
import miau.module.modules.misc.cheatdetector.Check;
import miau.module.modules.misc.cheatdetector.impl.movement.motion.subchecks.MotionB;
import net.minecraft.entity.player.EntityPlayer;

public class MotionCheck extends Check {
  private final List<Check> checks = Arrays.asList(new MotionB());

  @Override
  public String getName() {
    return "Motion";
  }

  @Override
  public void onUpdate(EntityPlayer player) {
    checks.forEach(check -> check.onUpdate(player));
  }

  @Override
  public void onPacket(PacketEvent e, EntityPlayer player) {
    checks.forEach(check -> check.onPacket(e, player));
  }
}
