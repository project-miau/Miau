package miau.ui.clickgui.opal.properties;

import miau.property.Property;
import miau.ui.clickgui.opal.*;
import miau.util.font.Font;
import miau.util.font.FontRepository;

// Note: MultipleBooleanPropertyComponent would normally handle array/chip selection.
// Since Miau doesn't have this property type natively, this is a placeholder
// that can be extended for multi-select properties.
public final class OpalMultipleBooleanPropertyComponent extends OpalPropertyPanel {

  private final Property<?> property;

  public OpalMultipleBooleanPropertyComponent(final Property<?> property) {
    this.property = property;
  }

  @Override
  public void render(int mouseX, int mouseY, float delta) {
    setHeight(17);
    super.render(mouseX, mouseY, delta);

    Font font = FontRepository.getFont("productsans-medium", 14);
    if (font != null) {
      font.draw(property.getName().toLowerCase() + " [...]", x + 5, y + 10.5F - 4, 0xFFFFFFFF);
    }
  }
}
