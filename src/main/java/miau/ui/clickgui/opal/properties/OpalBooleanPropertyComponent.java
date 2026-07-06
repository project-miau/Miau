package miau.ui.clickgui.opal.properties;

import miau.property.properties.BooleanProperty;
import miau.ui.clickgui.opal.*;
import miau.util.animation.Easing;
import miau.util.font.Font;
import miau.util.font.FontRepository;

public final class OpalBooleanPropertyComponent extends OpalPropertyPanel {

  private final BooleanProperty property;
  private OpalAnimation toggleAnimation;

  public OpalBooleanPropertyComponent(BooleanProperty property) {
    this.property = property;
  }

  @Override
  public void init() {
    toggleAnimation = null;
  }

  @Override
  public void render(int mouseX, int mouseY, float delta) {
    super.render(mouseX, mouseY, delta);

    Font font = FontRepository.getFont("productsans-medium", 14);
    if (font != null) {
      font.draw(property.getName().toLowerCase(), x + 5, y + 10.5F - 4, 0xFFFFFFFF);
    }

    // Toggle switch
    float destination = property.getValue() ? 1.0F : 0.0F;
    if (toggleAnimation == null) {
      toggleAnimation = new OpalAnimation(Easing.EASE_OUT_SINE, 150);
      toggleAnimation.setValue(destination);
    } else {
      toggleAnimation.run(destination);
    }

    int color1 =
        OpalColorUtil.interpolateColors(
            0xff3c3c3c, OpalColorUtil.getClientAccent(), toggleAnimation.getValue());
    int color2 = OpalColorUtil.darker(color1, 0.4F);

    float switchX = x + 88;
    float switchY = y + 3.8F;
    float sw = 20;
    float sh = 10;

    OpalRenderUtil.roundedRectGradient(switchX, switchY, sw, sh, sh / 2, color1, color2, 90);
    float knobOffset = 1 + toggleAnimation.getValue() * 9.5F;
    OpalRenderUtil.roundedRectGradient(
        switchX + knobOffset,
        switchY + 1,
        sh - 2,
        sh - 2,
        (sh - 2) / 2,
        0xFFFFFFFF,
        OpalColorUtil.darker(0xFFFFFFFF, 0.1F),
        90);
  }

  @Override
  public void mouseClicked(double mouseX, double mouseY, int button) {
    if (button == 0 && OpalHoverUtility.isHovering(x, y, width, height, mouseX, mouseY)) {
      property.setValue(!property.getValue());
    }
  }
}
