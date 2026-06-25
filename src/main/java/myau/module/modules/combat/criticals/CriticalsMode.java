package myau.module.modules.combat.criticals;

import myau.event.impl.AttackEvent;
import myau.event.impl.PacketEvent;
import myau.event.impl.UpdateEvent;
import myau.module.modules.combat.Criticals;
import net.minecraft.client.Minecraft;

public abstract class CriticalsMode {
  protected static final Minecraft mc = Minecraft.getMinecraft();
  protected final String name;
  protected final Criticals parent;

  public CriticalsMode(String name, Criticals parent) {
    this.name = name;
    this.parent = parent;
  }

  public String getName() {
    return name;
  }

  public void onEnable() {}

  public void onDisable() {}

  public void onUpdate(UpdateEvent event) {}

  public void onAttack(AttackEvent event) {}

  public void onPacket(PacketEvent event) {}
}
