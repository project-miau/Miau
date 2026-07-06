package miau.ui.clickgui.opal.properties;

import miau.property.properties.FloatProperty;
import miau.ui.clickgui.opal.*;
import miau.util.animation.Easing;
import miau.util.font.Font;
import miau.util.font.FontRepository;
import miau.util.math.MathUtil;

public final class OpalNumberPropertyComponent extends OpalPropertyPanel {

  private final FloatProperty property;
  private boolean dragging;
  private OpalAnimation dragAnimation;
  private String suffix;
  private final boolean integerMode;

  public OpalNumberPropertyComponent(final FloatProperty property) {
    this(property, false);
  }

  public OpalNumberPropertyComponent(final FloatProperty property, boolean integerMode) {
    this.property = property;
    this.suffix = null;
    this.integerMode = integerMode;
  }

  public void setSuffix(String suffix) {
    this.suffix = suffix;
  }

  @Override
  public void render(int mouseX, int mouseY, float delta) {
    setHeight(15);
    super.render(mouseX, mouseY, delta);

    Font font = FontRepository.getFont("productsans-medium", 14);
    if (font != null) {
      font.draw(property.getName().toLowerCase(), x + 5, y + 8.5F - 4, 0xFFFFFFFF);
    }

    float sliderWidth = width - 12;
    float sliderHeight = 2.5F;
    float sliderX = x + 6;
    float sliderY = y + 13F;

    if (dragging && mouseX != -1) {
      float percent = Math.min(1, Math.max(0, (float) ((mouseX - sliderX) / sliderWidth)));
      float newValue = (float) MathUtil.lerp(property.getMin(), property.getMax(), percent);
      property.setValue(newValue);
    }

    float range = property.getMax() - property.getMin();
    float widthPercent = range > 0 ? (property.getValue() - property.getMin()) / range : 0;
    float destination = sliderWidth * Math.max(0, Math.min(1, widthPercent));

    if (dragAnimation == null) {
      dragAnimation = new OpalAnimation(Easing.LINEAR, 50);
      dragAnimation.setValue(destination);
    } else {
      dragAnimation.run(destination);
    }

    // Track background
    OpalRenderUtil.roundedRect(
        sliderX, sliderY, sliderWidth, sliderHeight, sliderHeight / 2f, 0xff373737);

    float dragAnim = dragAnimation.getValue();
    if (dragAnim > 1) {
      int color = OpalColorUtil.getClientAccent();
      OpalRenderUtil.roundedRectGradient(
          sliderX,
          sliderY,
          dragAnim,
          sliderHeight,
          sliderHeight / 2f,
          color,
          OpalColorUtil.darker(color, 0.5F),
          90);
    }

    // Knob
    OpalRenderUtil.roundedRectGradient(
        sliderX + dragAnim - 1,
        sliderY - 1.3f,
        2,
        5,
        1,
        0xFFFFFFFF,
        OpalColorUtil.darker(0xFFFFFFFF, 0.1F),
        90);

    // Value text
    Number value = property.getValue();
    String valueString;
    if (integerMode) {
      valueString = String.valueOf(Math.round(value.doubleValue()));
    } else if (value.doubleValue() == value.intValue()) {
      valueString = String.valueOf(value.intValue());
    } else {
      valueString = String.format("%.1f", value.doubleValue());
    }

    if (suffix != null) {
      valueString += suffix;
    }

    if (font != null) {
      float textWidth = font.width(valueString);
      font.draw(
          valueString,
          sliderX + dragAnim - textWidth / 2,
          y + 22f - 4,
          OpalColorUtil.applyOpacity(0xFFFFFFFF, 0.8F));
    }
  }

  @Override
  public void mouseClicked(double mouseX, double mouseY, int button) {
    if (OpalHoverUtility.isHovering(x, y, width, height, mouseX, mouseY) && button == 0)
      dragging = true;
  }

  @Override
  public void mouseReleased(double mouseX, double mouseY, int button) {
    if (dragging && button == 0) dragging = false;
  }
}
