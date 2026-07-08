package miau.module.modules.player.scaffold.features;

import java.util.Arrays;
import java.util.List;
import miau.event.impl.UpdateEvent;
import miau.module.modules.player.Scaffold;
import miau.module.modules.player.scaffold.ScaffoldComponent;
import miau.property.Property;
import miau.property.properties.BooleanProperty;
import miau.property.properties.ModeProperty;
import net.minecraft.client.Minecraft;
import net.minecraft.util.MathHelper;

public class KeepYFeature implements ScaffoldComponent {
  private final Scaffold scaffold;
  private final Minecraft mc = Minecraft.getMinecraft();

  public final ModeProperty keepY =
      new ModeProperty(
          "keep-y",
          0,
          new String[] {"NONE", "VANILLA", "Extra 1 Block", "TELLY", "Extra 2 Blocks", "Test"});
  public final BooleanProperty keepYonPress =
      new BooleanProperty(
          "keep-y-on-press",
          false,
          () ->
              this.keepY.getValue() == 3
                  || this.keepY.getValue() == 4
                  || this.keepY.getValue() == 5);

  public KeepYFeature(Scaffold scaffold) {
    this.scaffold = scaffold;
  }

  @Override
  public List<Property<?>> getProperties() {
    return Arrays.asList(keepY, keepYonPress);
  }

  @Override
  public void onUpdate(UpdateEvent event) {
    if (mc.thePlayer.onGround) {
      if (scaffold.stage > 0) scaffold.stage--;
      if (scaffold.stage < 0) scaffold.stage++;

      if (scaffold.stage == 0
          && this.keepY.getValue() != 0
          && (this.keepYonPress.getValue()
              ? Scaffold.mc.gameSettings.keyBindUseItem.isKeyDown()
              : !mc.gameSettings.keyBindJump.isKeyDown())) {
        scaffold.stage = 1;
      }
      scaffold.startY =
          scaffold.shouldKeepY ? scaffold.startY : MathHelper.floor_double(mc.thePlayer.posY);
      scaffold.shouldKeepY = false;
    }
  }
}
