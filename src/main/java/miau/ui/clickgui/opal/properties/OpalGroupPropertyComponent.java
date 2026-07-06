package miau.ui.clickgui.opal.properties;

import java.util.ArrayList;
import java.util.List;
import miau.property.Property;
import miau.ui.clickgui.opal.*;
import miau.util.font.Font;
import miau.util.font.FontRepository;

public final class OpalGroupPropertyComponent extends OpalPropertyPanel {

  private final Property<?> property;
  private final List<OpalPropertyPanel> children = new ArrayList<>();
  private boolean collapsed;

  // We need to access sub-properties of a group via module values,
  // so this stores a reference to the containing property for name matching.
  public OpalGroupPropertyComponent(final Property<?> property) {
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

    // Collapse arrow
    Font iconFont = FontRepository.getFont("materialicons-regular", 16);
    if (iconFont != null) {
      String icon = collapsed ? "\uE5CF" : "\uE5CE"; // expand more / expand less
      iconFont.draw(icon, x + width - 16, y + 8.5F - 4, 0xFFFFFFFF);
    }
  }

  @Override
  public void mouseClicked(double mouseX, double mouseY, int button) {
    if (button == 0 && OpalHoverUtility.isHovering(x, y, width, height, mouseX, mouseY)) {
      collapsed = !collapsed;
    }
  }
}
