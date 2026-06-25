package myau.module.modules.movement;

import java.util.ArrayList;
import java.util.List;
import myau.event.EventTarget;
import myau.event.impl.PacketEvent;
import myau.event.impl.RightClickMouseEvent;
import myau.event.impl.UpdateEvent;
import myau.module.Module;
import myau.module.modules.movement.noslow.*;
import myau.property.Property;
import myau.property.properties.BooleanProperty;
import myau.property.properties.ModeProperty;
import myau.util.player.ItemUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.item.ItemPotion;

public class NoSlow extends Module {
  private static final Minecraft mc = Minecraft.getMinecraft();

  public final List<NoSlowMode> modes = new ArrayList<>();

  public final ModeProperty mode =
      new ModeProperty(
          "Mode",
          0,
          new String[] {
            register(new OMVanillaNoSlow("Vanilla", this)),
            register(new OMNCPNoSlow("NCP", this)),
            register(new OMNewNCPNoSlow("New NCP", this)),
            register(new OMWatchdogNoSlow("Watchdog", this)),
            register(new OMIntaveNoSlow("Intave", this)),
            register(new OMGrimNoSlow("Grim 1.9", this))
          });

  public final BooleanProperty swordValue = new BooleanProperty("sword", true);
  public final BooleanProperty foodValue = new BooleanProperty("food", true);
  public final BooleanProperty potionValue = new BooleanProperty("potion", true);
  public final BooleanProperty bowValue = new BooleanProperty("bow", true);
  public final BooleanProperty antiSwitch = new BooleanProperty("anti-switch", false);

  public NoSlow() {
    super("NoSlow", false);
  }

  private String register(NoSlowMode m) {
    this.modes.add(m);
    return m.getName();
  }

  public NoSlowMode getActiveMode() {
    return modes.stream()
        .filter(m -> m.getName().equals(mode.getModeString()))
        .findFirst()
        .orElse(modes.get(0));
  }

  @Override
  public List<Property<?>> getAdditionalProperties() {
    List<Property<?>> props = new ArrayList<>();
    for (NoSlowMode m : modes) {
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

  @Override
  public void onEnabled() {
    getActiveMode().onEnable();
  }

  @Override
  public void onDisabled() {
    getActiveMode().onDisable();
  }

  public boolean isSwordActive() {
    return this.swordValue.getValue() && ItemUtil.isHoldingSword();
  }

  public boolean isFoodActive() {
    return this.foodValue.getValue() && ItemUtil.isEating();
  }

  public boolean isBowActive() {
    return this.bowValue.getValue() && ItemUtil.isUsingBow();
  }

  public boolean isPotionActive() {
    return this.potionValue.getValue()
        && mc.thePlayer.isUsingItem()
        && mc.thePlayer.getHeldItem().getItem() instanceof ItemPotion;
  }

  public boolean isAntiSwitchActive() {
    if (!this.isEnabled()
        || !this.antiSwitch.getValue()
        || mc.thePlayer == null
        || mc.theWorld == null) {
      return false;
    }
    net.minecraft.item.ItemStack heldItem = mc.thePlayer.getHeldItem();
    return heldItem != null
        && heldItem.getItem() instanceof net.minecraft.item.ItemSword
        && mc.thePlayer.isUsingItem();
  }

  public boolean isAnyActive() {
    return mc.thePlayer.isUsingItem()
        && (isSwordActive() || isFoodActive() || isBowActive() || isPotionActive());
  }

  public boolean canSprint() {
    return true;
  }

  public float getMotionMultiplier() {
    if (this.mode.getModeString().equals("GRIM")) {
      return 0.35f;
    }
    return 1.0f;
  }

  @EventTarget
  public void onUpdate(UpdateEvent event) {
    if (this.isEnabled()) {
      getActiveMode().onUpdate(event);
    }
  }

  @EventTarget
  public void onPacket(PacketEvent event) {
    if (this.isEnabled()) {
      getActiveMode().onPacket(event);
    }
  }

  @EventTarget
  public void onRightClick(RightClickMouseEvent event) {
    if (this.isEnabled()) {
      getActiveMode().onRightClick(event);
    }
  }

  @Override
  public String[] getSuffix() {
    return new String[] {this.mode.getModeString()};
  }
}
