package miau.module.modules.movement;

import miau.event.EventTarget;
import miau.event.impl.TickEvent;
import miau.event.types.EventType;
import miau.module.Module;
import miau.util.client.KeyBindUtil;
import net.minecraft.client.Minecraft;

public class AutoWalk extends Module {

  private static final Minecraft mc = Minecraft.getMinecraft();

  public AutoWalk() {
    super("AutoWalk", false);
  }

  @EventTarget
  public void onTick(TickEvent event) {
    if (!this.isEnabled()) return;

    if (event.getType() == EventType.PRE) {
      KeyBindUtil.setKeyBindState(mc.gameSettings.keyBindForward.getKeyCode(), true);
    }
  }

  @Override
  public void onDisabled() {
    KeyBindUtil.updateKeyState(mc.gameSettings.keyBindForward.getKeyCode());
  }
}
