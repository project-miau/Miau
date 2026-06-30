package miau.module.modules.misc.disabler;

import miau.event.impl.PacketEvent;
import miau.event.impl.TickEvent;
import miau.module.modules.misc.Disabler;
import net.minecraft.client.Minecraft;

public abstract class DisablerMode {
  protected static final Minecraft mc = Minecraft.getMinecraft();
  protected final String name;
  protected final Disabler parent;

  public DisablerMode(String name, Disabler parent) {
    this.name = name;
    this.parent = parent;
  }

  public String getName() {
    return name;
  }

  public void onEnable() {}

  public void onDisable() {}

  public void onTick(TickEvent event) {}

  public void onPacket(PacketEvent event) {}
}
