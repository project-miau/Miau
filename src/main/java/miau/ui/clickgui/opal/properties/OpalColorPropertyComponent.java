package miau.ui.clickgui.opal.properties;

import java.awt.*;
import miau.property.properties.ColorProperty;
import miau.ui.clickgui.opal.*;
import miau.util.animation.Easing;
import miau.util.font.Font;
import miau.util.font.FontRepository;

public final class OpalColorPropertyComponent extends OpalPropertyPanel {

  private final ColorProperty property;
  private boolean extended;
  private OpalAnimation extendAnimation;
  private boolean draggingHue, draggingSatBright;
  private float hue, saturation, brightness;
  private boolean initialized;

  public OpalColorPropertyComponent(final ColorProperty property) {
    this.property = property;
  }

  @Override
  public void render(int mouseX, int mouseY, float delta) {
    super.render(mouseX, mouseY, delta);

    Font font = FontRepository.getFont("productsans-medium", 14);
    if (font != null) {
      font.draw(property.getName().toLowerCase(), x + 5, y + 10.5F - 4, 0xFFFFFFFF);
    }

    // Color preview swatch
    int colorVal = property.getValue();
    float swatchSize = 11;
    float swatchX = x + width - swatchSize - 5;
    float swatchY = y + 3;

    OpalRenderUtil.roundedRect(swatchX, swatchY, swatchSize, swatchSize, 3, colorVal | 0xFF000000);
    // Border
    OpalRenderUtil.roundedRectOutline(swatchX, swatchY, swatchSize, swatchSize, 3, 1, 0xFF555555);

    // Hex text
    String hex = String.format("#%06X", colorVal).toLowerCase();
    Font smallFont = FontRepository.getFont("productsans-regular", 12);
    if (smallFont != null) {
      smallFont.draw(
          hex,
          x + width - swatchSize - 5 - smallFont.width(hex) - 5,
          y + 10.5F - 4,
          OpalColorUtil.applyOpacity(0xFFFFFFFF, 0.6F));
    }

    // Extended picker
    if (extended) {
      renderPicker(mouseX, mouseY);
    }
  }

  private void renderPicker(float mouseX, float mouseY) {
    if (extendAnimation == null) {
      extendAnimation = new OpalAnimation(Easing.EASE_OUT_SINE, 150);
      extendAnimation.setValue(extended ? 1 : 0);
    } else {
      extendAnimation.run(extended ? 1 : 0);
    }
    float progress = extendAnimation.getValue();

    float pickerY = y + 17;
    float pickerH = 100 * progress;
    if (pickerH < 1) return;

    // HSB box
    float boxSize = 80;
    float boxX = x + 5;
    float boxY = pickerY + 15;

    // Hue bar
    float hueBarX = x + 90;
    float hueBarY = pickerY + 15;
    float hueBarW = 10;
    float hueBarH = boxSize;

    OpalRenderUtil.rainbowRect(hueBarX, hueBarY, hueBarW, hueBarH);

    // HSB gradient area
    // Draw white-to-black gradient
    OpalRenderUtil.rectGradient(boxX, boxY, boxSize, boxSize, 0xFFFFFFFF, 0xFF000000, 270);
    // Overlay hue
    int hueColor = Color.HSBtoRGB(hue, 1, 1);
    OpalRenderUtil.rectGradient(
        boxX,
        boxY,
        boxSize,
        boxSize,
        OpalColorUtil.applyOpacity(hueColor, 0.5F),
        OpalColorUtil.applyOpacity(0xFF000000, 0.8F),
        180);

    // Knob on HB area
    float knobX = boxX + saturation * boxSize;
    float knobY = boxY + (1 - brightness) * boxSize;
    int knobColor = 0xFFFFFFFF;
    OpalRenderUtil.roundedRectOutline(knobX - 2, knobY - 2, 4, 4, 4, 1, knobColor);

    // Hue picker
    float hueKnobY = hueBarY + hue * hueBarH;
    OpalRenderUtil.rect(hueBarX - 1, hueKnobY - 1, hueBarW + 2, 2, 0xFFFFFFFF);

    // Drag handling
    if (draggingHue) {
      float newHue = Math.min(1, Math.max(0, (float) (mouseY - hueBarY) / hueBarH));
      hue = newHue;
      updateColor();
    }

    if (draggingSatBright) {
      float newSat = Math.min(1, Math.max(0, (float) (mouseX - boxX) / boxSize));
      float newBri = Math.min(1, Math.max(0, 1 - (float) (mouseY - boxY) / boxSize));
      saturation = newSat;
      brightness = newBri;
      updateColor();
    }
  }

  private void updateColor() {
    int rgb = Color.HSBtoRGB(hue, saturation, brightness);
    int alpha = (property.getValue() >> 24) & 0xFF;
    int result = (alpha << 24) | (rgb & 0x00FFFFFF);
    property.setValue(result & 0x00FFFFFF);
  }

  @Override
  public void mouseClicked(double mouseX, double mouseY, int button) {
    if (button == 0 && OpalHoverUtility.isHovering(x, y, width, height, mouseX, mouseY)) {
      extended = !extended;
      return;
    }

    if (!extended) return;

    float pickerY = y + 17;
    float boxSize = 80;
    float boxX = x + 5;
    float boxY = pickerY + 15;
    float hueBarX = x + 90;
    float hueBarY = pickerY + 15;
    float hueBarW = 10;
    float hueBarH = boxSize;

    if (OpalHoverUtility.isHovering(hueBarX, hueBarY, hueBarW, hueBarH, mouseX, mouseY)) {
      draggingHue = true;
      return;
    }

    if (OpalHoverUtility.isHovering(boxX, boxY, boxSize, boxSize, mouseX, mouseY)) {
      draggingSatBright = true;
      return;
    }
  }

  @Override
  public void mouseReleased(double mouseX, double mouseY, int button) {
    if (button == 0) {
      draggingHue = false;
      draggingSatBright = false;
    }
  }
}
