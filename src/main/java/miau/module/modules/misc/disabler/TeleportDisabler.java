package miau.module.modules.misc.disabler;

import miau.event.impl.TickEvent;
import miau.event.types.EventType;
import miau.module.modules.misc.Disabler;
import miau.property.properties.BooleanProperty;
import miau.property.properties.IntProperty;
import miau.property.properties.ModeProperty;
import miau.util.network.PacketUtil;
import net.minecraft.network.play.client.C03PacketPlayer;

/**
 * Teleport disabler: force server teleport with direction/delay options. Ported from OpenRise (Rise
 * 6)
 *
 * <p>Properties: - Direction: Up, Down, Horizontal - Delay: Ticks between teleport packets (2-100,
 * default 20) - Math Ground: Round Y to ground - Ground State: Ground state flag
 */
public class TeleportDisabler extends DisablerMode {

  public final ModeProperty direction =
      new ModeProperty("Direction", 0, new String[] {"Up", "Down", "Horizontal"});
  public final IntProperty delay = new IntProperty("Delay", 20, 2, 100);
  public final BooleanProperty mathGround = new BooleanProperty("Math Ground", false);
  public final BooleanProperty groundState = new BooleanProperty("Ground State", false);

  public TeleportDisabler(String name, Disabler parent) {
    super(name, parent);
  }

  @Override
  public void onTick(TickEvent event) {
    if (event.getType() != EventType.PRE) return;
    if (mc.thePlayer == null || mc.theWorld == null) return;

    if (mc.thePlayer.ticksExisted % delay.getValue() == 0) {
      double x = mc.thePlayer.posX;
      double y = mc.thePlayer.posY;
      double z = mc.thePlayer.posZ;

      switch (direction.getValue()) {
        case 0: // Up
          y += 1024 + (Math.random() * 1024);
          break;
        case 1: // Down
          y -= 1024 + (Math.random() * 1024);
          break;
        case 2: // Horizontal
          x += 1024 + (Math.random() * 1024);
          z -= 1024 + (Math.random() * 1024);
          break;
      }

      if (mathGround.getValue()) {
        y = Math.round(y / 0.015625) * 0.015625;
      }

      PacketUtil.sendPacketNoEvent(
          new C03PacketPlayer.C04PacketPlayerPosition(x, y, z, groundState.getValue()));
    }
  }
}
