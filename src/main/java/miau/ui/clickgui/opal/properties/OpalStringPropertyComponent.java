package miau.ui.clickgui.opal.properties;

import miau.property.Property;
import miau.ui.clickgui.opal.*;
import miau.util.font.Font;
import miau.util.font.FontRepository;

public final class OpalStringPropertyComponent extends OpalPropertyPanel {

  private final Property<String> property;
  private boolean selected;

  public OpalStringPropertyComponent(final Property<?> property) {
    @SuppressWarnings("unchecked")
    Property<String> p = (Property<String>) property;
    this.property = p;
  }

  @Override
  public void render(int mouseX, int mouseY, float delta) {
    setHeight(17);
    super.render(mouseX, mouseY, delta);

    Font font = FontRepository.getFont("productsans-medium", 14);
    if (font != null) {
      font.draw(property.getName().toLowerCase(), x + 5, y + 10.5F - 4, 0xFFFFFFFF);
    }

    // Text field background
    float fieldX = x + width / 2;
    float fieldWidth = width / 2 - 10;
    float fieldY = y + 2.5F;
    float fieldH = 12;

    int bgColor = selected ? 0xff3a3a4a : 0xff22222e;
    OpalRenderUtil.roundedRect(fieldX, fieldY, fieldWidth, fieldH, 3, bgColor);

    String display = property.getValue();
    if (display == null || display.isEmpty()) display = "...";

    Font smallFont = FontRepository.getFont("productsans-regular", 12);
    if (smallFont != null) {
      smallFont.draw(display, fieldX + 3, fieldY + 7F - 4, selected ? 0xFFFFFFFF : 0xFF808080);
    }

    // Draw cursor if selected
    if (selected) {
      if (smallFont != null) {
        float cursorX = fieldX + 3 + smallFont.width(display);
        OpalRenderUtil.rect(cursorX, fieldY + 1, 1, fieldH - 2, 0xFFFFFFFF);
      }
    }
  }

  @Override
  public void mouseClicked(double mouseX, double mouseY, int button) {
    if (button == 0) {
      float fieldX = x + width / 2;
      float fieldWidth = width / 2 - 10;
      selected = OpalHoverUtility.isHovering(fieldX, y + 2.5F, fieldWidth, 12, mouseX, mouseY);
      if (selected) {
        OpalClickGui.typingString = true;
      }
    }
  }

  @Override
  public void keyPressed(int keyCode) {
    if (!selected) return;
    org.lwjgl.input.Keyboard keyboard = null;
    if (keyCode == 28) { // ENTER
      selected = false;
      OpalClickGui.typingString = false;
      return;
    }
    if (keyCode == 14) { // BACKSPACE
      String value = property.getValue();
      if (value != null && !value.isEmpty()) {
        property.setValue(value.substring(0, value.length() - 1));
      }
      return;
    }
  }

  @Override
  public void charTyped(char typedChar, int modifiers) {
    if (!selected) return;
    if (typedChar >= ' ' && typedChar <= '~') {
      String value = property.getValue();
      if (value != null) {
        property.setValue(value + typedChar);
      }
    }
  }
}
