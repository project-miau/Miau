package myau.module.modules.movement.nofalls;

import myau.event.impl.PacketEvent;
import myau.event.impl.TickEvent;
import myau.module.modules.movement.NoFall;
import net.minecraft.client.Minecraft;

public abstract class NoFallMode {
  protected static final Minecraft mc = Minecraft.getMinecraft();
  protected final String name;
  protected final NoFall parent;

  public NoFallMode(String name, NoFall parent) {
    this.name = name;
    this.parent = parent;
  }

  public String getName() {
    return name;
  }

  public void onEnable() {}

  public void onDisable() {}

  public void onPacket(PacketEvent event) {}

  public void onTick(TickEvent event) {}
}
