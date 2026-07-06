package miau.ui.clickgui.opal.properties;

import miau.property.properties.ModeProperty;
import miau.ui.clickgui.opal.*;
import miau.util.font.Font;
import miau.util.font.FontRepository;

public final class OpalModePropertyComponent extends OpalPropertyPanel {

  private final ModeProperty property;
  private OpalAnimation hoverAnimation;

  public OpalModePropertyComponent(final ModeProperty property) {
    this.property = property;
  }

  @Override
  public void render(int mouseX, int mouseY, float delta) {
    setHeight(17);
    super.render(mouseX, mouseY, delta);

    Font font = FontRepository.getFont("productsans-medium", 14);
    if (font != null) {
      font.draw(property.getName().toLowerCase(), x + 5, y + 10.5F - 4, 0xFFFFFFFF);
    }

    // Mode chips
    String[] modes = property.getModes();
    if (modes == null) return;

    float currentX = x + width - 5;
    for (int i = modes.length - 1; i >= 0; i--) {
      String mode = modes[i];
      boolean isSelected = i == property.getValue();

      Font modeFont = FontRepository.getFont("productsans-medium", 12);
      if (modeFont == null) continue;

      float modeWidth = modeFont.width(mode) + 8;
      currentX -= modeWidth;
      float chipX = currentX;
      float chipY = y + 2F;
      float chipH = 13;

      int chipColor = isSelected ? OpalColorUtil.getClientAccent() : 0xff2a2a3a;
      OpalRenderUtil.roundedRect(chipX, chipY, modeWidth, chipH, 4, chipColor);

      int textColor = isSelected ? 0xFFFFFFFF : 0xFF808080;
      modeFont.draw(mode.toLowerCase(), chipX + 4, chipY + 7.5F - 4, textColor);

      currentX -= 2;
    }
  }

  @Override
  public void mouseClicked(double mouseX, double mouseY, int button) {
    if (button != 0) return;

    String[] modes = property.getModes();
    if (modes == null) return;

    Font modeFont = FontRepository.getFont("productsans-medium", 12);
    if (modeFont == null) return;

    float currentX = x + width - 5;
    for (int i = modes.length - 1; i >= 0; i--) {
      String mode = modes[i];
      float modeWidth = modeFont.width(mode) + 8;
      currentX -= modeWidth;

      if (OpalHoverUtility.isHovering(currentX, y + 2F, modeWidth, 13, mouseX, mouseY)) {
        property.setValue(i);
        return;
      }
      currentX -= 2;
    }
  }
}
