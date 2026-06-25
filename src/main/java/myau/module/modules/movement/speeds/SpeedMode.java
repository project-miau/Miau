package myau.module.modules.movement.speeds;

import myau.event.impl.LivingUpdateEvent;
import myau.event.impl.StrafeEvent;
import myau.module.modules.movement.Speed;
import net.minecraft.client.Minecraft;

public abstract class SpeedMode {
  protected static final Minecraft mc = Minecraft.getMinecraft();
  protected final String name;
  protected final Speed parent;

  public SpeedMode(String name, Speed parent) {
    this.name = name;
    this.parent = parent;
  }

  public String getName() {
    return name;
  }

  public void onEnable() {}

  public void onDisable() {}

  public void onStrafe(StrafeEvent event) {}

  public void onLivingUpdate(LivingUpdateEvent event) {}
}
