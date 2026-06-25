package myau.module.modules.movement;

import java.util.ArrayList;
import java.util.List;
import myau.Myau;
import myau.event.EventTarget;
import myau.event.impl.LivingUpdateEvent;
import myau.event.impl.StrafeEvent;
import myau.event.types.Priority;
import myau.mixin.IAccessorEntity;
import myau.module.Module;
import myau.module.modules.movement.speeds.*;
import myau.module.modules.player.Scaffold;
import myau.property.Property;
import myau.property.properties.ModeProperty;
import myau.util.player.MoveUtil;
import net.minecraft.client.Minecraft;

public class Speed extends Module {
  private static final Minecraft mc = Minecraft.getMinecraft();

  public final List<SpeedMode> modes = new ArrayList<>();

  public final ModeProperty mode =
      new ModeProperty(
          "Mode",
          0,
          new String[] {
            register(new DefaultSpeed("DEFAULT", this)),
            register(new LegitSpeed("LEGIT", this)),
            register(new LowHopSpeed("LowHop", this))
          });

  public Speed() {
    super("Speed", false);
  }

  private String register(SpeedMode m) {
    this.modes.add(m);
    return m.getName();
  }

  public SpeedMode getActiveMode() {
    return modes.stream()
        .filter(m -> m.getName().equals(mode.getModeString()))
        .findFirst()
        .orElse(modes.get(0));
  }

  @Override
  public List<Property<?>> getAdditionalProperties() {
    List<Property<?>> props = new ArrayList<>();
    for (SpeedMode m : modes) {
      for (java.lang.reflect.Field field : m.getClass().getDeclaredFields()) {
        field.setAccessible(true);
        try {
          Object obj = field.get(m);
          if (obj instanceof Property<?>) {
            Property<?> prop = (Property<?>) obj;
            java.util.function.BooleanSupplier original = prop.getVisibleChecker();
            prop.setVisibleChecker(
                () -> this.getActiveMode() == m && (original == null || original.getAsBoolean()));
            props.add(prop);
          }
        } catch (Exception e) {
        }
      }
    }
    return props;
  }

  public boolean canBoost() {
    Scaffold scaffold = (Scaffold) Myau.moduleManager.modules.get(Scaffold.class);
    return !scaffold.isEnabled()
        && MoveUtil.isForwardPressed()
        && mc.thePlayer.getFoodStats().getFoodLevel() > 6
        && !mc.thePlayer.isSneaking()
        && !mc.thePlayer.isInWater()
        && !mc.thePlayer.isInLava()
        && !((IAccessorEntity) mc.thePlayer).getIsInWeb();
  }

  @Override
  public void onEnabled() {
    getActiveMode().onEnable();
  }

  @Override
  public void onDisabled() {
    getActiveMode().onDisable();
  }

  @EventTarget(Priority.LOW)
  public void onStrafe(StrafeEvent event) {
    if (this.isEnabled()) {
      getActiveMode().onStrafe(event);
    }
  }

  @EventTarget(Priority.LOW)
  public void onLivingUpdate(LivingUpdateEvent event) {
    if (this.isEnabled()) {
      getActiveMode().onLivingUpdate(event);
    }
  }

  @Override
  public String[] getSuffix() {
    return new String[] {this.mode.getModeString()};
  }
}
