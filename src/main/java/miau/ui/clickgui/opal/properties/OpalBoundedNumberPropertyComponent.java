package miau.ui.clickgui.opal.properties;

import miau.property.properties.FloatProperty;
import miau.ui.clickgui.opal.*;
import miau.util.animation.Easing;
import miau.util.font.Font;
import miau.util.font.FontRepository;
import miau.util.math.MathUtil;

public final class OpalBoundedNumberPropertyComponent extends OpalPropertyPanel {

  private final FloatProperty property;
  private boolean draggingMin, draggingMax;
  private OpalAnimation minAnim, maxAnim, barHoverAnim;

  public OpalBoundedNumberPropertyComponent(final FloatProperty property) {
    this.property = property;
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
    float sliderX = x + 6;
    float sliderY = y + 13F;

    // Calculate positions
    float range = property.getMax() - property.getMin();
    float minPercent = (property.getValue() - property.getMin()) / range;
    float maxPercent = (property.getSecondValue() - property.getMin()) / range;

    float minPos = sliderWidth * Math.max(0, Math.min(1, minPercent));
    float maxPos = sliderWidth * Math.max(0, Math.min(1, maxPercent));

    if (draggingMin && mouseX != -1) {
      float percent = Math.min(1, Math.max(0, (mouseX - sliderX) / sliderWidth));
      float newVal = (float) MathUtil.lerp(property.getMin(), property.getMax(), percent);
      if (newVal < property.getSecondValue()) {
        property.setValue(newVal);
      }
    }

    if (draggingMax && mouseX != -1) {
      float percent = Math.min(1, Math.max(0, (mouseX - sliderX) / sliderWidth));
      float newVal = (float) MathUtil.lerp(property.getMin(), property.getMax(), percent);
      if (newVal > property.getValue()) {
        property.setSecondValue(newVal);
      }
    }

    // Animate
    if (minAnim == null) {
      minAnim = new OpalAnimation(Easing.LINEAR, 50);
      minAnim.setValue(minPos);
      maxAnim = new OpalAnimation(Easing.LINEAR, 50);
      maxAnim.setValue(maxPos);
    }
    minAnim.run(minPos);
    maxAnim.run(maxPos);

    // Track
    float sliderHeight = 2F;
    OpalRenderUtil.roundedRect(
        sliderX, sliderY, sliderWidth, sliderHeight, sliderHeight / 2f, 0xff373737);

    // Active bar between min and max
    int accent = OpalColorUtil.getClientAccent();
    float minV = minAnim.getValue();
    float maxV = maxAnim.getValue();
    if (maxV - minV > 0.5F) {
      OpalRenderUtil.roundedRectGradient(
          sliderX + minV,
          sliderY,
          maxV - minV,
          sliderHeight,
          sliderHeight / 2f,
          accent,
          OpalColorUtil.darker(accent, 0.5F),
          90);
    }

    // Knobs
    OpalRenderUtil.roundedRectGradient(
        sliderX + minV - 0.8F,
        sliderY - 1.3f,
        1.6F,
        4.6F,
        0.8F,
        0xFFFFFFFF,
        OpalColorUtil.darker(0xFFFFFFFF, 0.1F),
        90);
    OpalRenderUtil.roundedRectGradient(
        sliderX + maxV - 0.8F,
        sliderY - 1.3f,
        1.6F,
        4.6F,
        0.8F,
        0xFFFFFFFF,
        OpalColorUtil.darker(0xFFFFFFFF, 0.1F),
        90);

    // Value text
    String minStr = String.format("%.0f", property.getValue());
    String maxStr = String.format("%.0f", property.getSecondValue());
    String rangeStr = minStr + " - " + maxStr;

    if (font != null) {
      float textWidth = font.width(rangeStr);
      font.draw(
          rangeStr,
          x + width - textWidth - 5,
          y + 8.5F - 4,
          OpalColorUtil.applyOpacity(0xFFFFFFFF, 0.6F));
    }
  }

  @Override
  public void mouseClicked(double mouseX, double mouseY, int button) {
    if (!OpalHoverUtility.isHovering(x, y, width, height, mouseX, mouseY) || button != 0) return;

    float rangePercent = (float) ((mouseX - (x + 6)) / (width - 12));
    // Check which knob is closer
    float minVal =
        (property.getValue() - property.getMin()) / (property.getMax() - property.getMin());
    float maxVal =
        (property.getSecondValue() - property.getMin()) / (property.getMax() - property.getMin());
    float distMin = Math.abs(rangePercent - minVal);
    float distMax = Math.abs(rangePercent - maxVal);

    if (distMin <= distMax) {
      draggingMin = true;
    } else {
      draggingMax = true;
    }
  }

  @Override
  public void mouseReleased(double mouseX, double mouseY, int button) {
    if (button == 0) {
      draggingMin = false;
      draggingMax = false;
    }
  }
}
