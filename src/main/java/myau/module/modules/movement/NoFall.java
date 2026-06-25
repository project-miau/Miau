package myau.module.modules.movement;

import com.google.common.base.CaseFormat;
import java.util.ArrayList;
import java.util.List;
import myau.event.EventTarget;
import myau.event.impl.PacketEvent;
import myau.event.impl.TickEvent;
import myau.event.types.EventType;
import myau.event.types.Priority;
import myau.module.Module;
import myau.module.modules.movement.nofalls.*;
import myau.property.Property;
import myau.property.properties.FloatProperty;
import myau.property.properties.IntProperty;
import myau.property.properties.ModeProperty;
import myau.util.network.ServerUtil;
import myau.util.time.TimerUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.network.play.server.S08PacketPlayerPosLook;

public class NoFall extends Module {
  private static final Minecraft mc = Minecraft.getMinecraft();
  public final TimerUtil packetDelayTimer = new TimerUtil();
  private final TimerUtil scoreboardResetTimer = new TimerUtil();

  public final List<NoFallMode> modes = new ArrayList<>();

  public final ModeProperty mode =
      new ModeProperty(
          "Mode",
          0,
          new String[] {
            register(new PacketNoFall("PACKET", this)),
            register(new BlinkNoFall("BLINK", this)),
            register(new NoGroundNoFall("NO_GROUND", this)),
            register(new SpoofNoFall("SPOOF", this)),
            register(new LegitNoFall("LEGIT", this))
          });

  public final FloatProperty distance = new FloatProperty("distance", 3.0F, 0.0F, 20.0F);
  public final IntProperty delay = new IntProperty("delay", 0, 0, 10000);

  public NoFall() {
    super("NoFall", false);
  }

  private String register(NoFallMode m) {
    this.modes.add(m);
    return m.getName();
  }

  public NoFallMode getActiveMode() {
    return modes.stream()
        .filter(m -> m.getName().equals(mode.getModeString()))
        .findFirst()
        .orElse(modes.get(0));
  }

  @Override
  public List<Property<?>> getAdditionalProperties() {
    List<Property<?>> props = new ArrayList<>();
    for (NoFallMode m : modes) {
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

  public boolean canTrigger() {
    return this.scoreboardResetTimer.hasTimeElapsed(3000)
        && this.packetDelayTimer.hasTimeElapsed(this.delay.getValue().longValue());
  }

  @Override
  public void onEnabled() {
    getActiveMode().onEnable();
  }

  @Override
  public void onDisabled() {
    getActiveMode().onDisable();
  }

  @EventTarget(Priority.HIGH)
  public void onPacket(PacketEvent event) {
    if (event.getType() == EventType.RECEIVE
        && event.getPacket() instanceof S08PacketPlayerPosLook) {
      this.onDisabled();
    } else if (this.isEnabled()) {
      getActiveMode().onPacket(event);
    }
  }

  @EventTarget(Priority.HIGHEST)
  public void onTick(TickEvent event) {
    if (this.isEnabled() && event.getType() == EventType.PRE) {
      if (ServerUtil.hasPlayerCountInfo()) {
        this.scoreboardResetTimer.reset();
      }
      getActiveMode().onTick(event);
    }
  }

  @Override
  public void verifyValue(String mode) {
    if (this.isEnabled()) {
      this.onDisabled();
    }
  }

  @Override
  public String[] getSuffix() {
    return new String[] {
      CaseFormat.UPPER_UNDERSCORE.to(CaseFormat.UPPER_CAMEL, this.mode.getModeString())
    };
  }
}
