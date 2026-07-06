package miau.ui.clickgui.opal.properties;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;
import miau.module.Module;
import miau.ui.clickgui.opal.*;

public final class OpalPropertyProvider extends OpalPanelComponent {

  private final List<OpalPropertyPanel> propertyPanelList = new ArrayList<>();
  private final Module module;
  private final BooleanSupplier expanded, lastPropertyListProvider;

  public OpalPropertyProvider(
      final Module module,
      final BooleanSupplier expanded,
      final BooleanSupplier lastPropertyListProvider) {
    this.module = module;
    this.expanded = expanded;
    this.initProperties();
    this.updateHasProperties();
    this.lastPropertyListProvider = lastPropertyListProvider;
  }

  private void initProperties() {
    List<miau.property.Property<?>> props = module.getValues();
    if (props == null) return;
    for (final miau.property.Property<?> property : props) {
      if (!property.isVisible()) continue;
      final OpalPropertyPanel clickGUIComponent = OpalPropertyPanel.createFor(property);
      if (clickGUIComponent != null) {
        this.propertyPanelList.add(clickGUIComponent);
      }
    }
  }

  public boolean isHasProperties() {
    if (!this.updated) {
      this.updateHasProperties();
      this.updated = true;
    }
    return hasProperties;
  }

  private boolean hasProperties, updated;

  private void updateHasProperties() {
    List<miau.property.Property<?>> props = module.getValues();
    if (props == null) {
      this.hasProperties = false;
      return;
    }
    this.hasProperties = props.stream().anyMatch(p -> p.isVisible());
  }

  private boolean isClosed() {
    return !this.expanded.getAsBoolean();
  }

  private float extraHeight;

  @Override
  public void init() {
    if (this.isClosed()) return;
    propertyPanelList.forEach(OpalPropertyPanel::init);
  }

  @Override
  public void close() {
    if (this.isClosed()) return;
    propertyPanelList.forEach(OpalPropertyPanel::close);
  }

  @Override
  public void render(int mouseX, int mouseY, float delta) {
    if (this.isClosed()) return;

    float extraHeight = 0;

    int lastVisibleIndex = -1;
    for (int i = propertyPanelList.size() - 1; i >= 0; i--) {
      if (!propertyPanelList.get(i).isHidden()) {
        lastVisibleIndex = i;
        break;
      }
    }

    for (int i = 0; i < propertyPanelList.size(); i++) {
      final OpalPropertyPanel propertyPanel = propertyPanelList.get(i);

      if (propertyPanel.isHidden()) continue;
      propertyPanel.setX(x);
      propertyPanel.setY(y + extraHeight);
      propertyPanel.setWidth(width);

      propertyPanel.lastProperty =
          lastPropertyListProvider != null
              && lastPropertyListProvider.getAsBoolean()
              && i == lastVisibleIndex;

      propertyPanel.render(mouseX, mouseY, delta);

      extraHeight += propertyPanel.getHeight();
    }

    this.extraHeight = extraHeight;
  }

  @Override
  public void keyPressed(int keyCode) {
    if (this.isClosed()) return;
    propertyPanelList.forEach(panel -> panel.keyPressed(keyCode));
  }

  @Override
  public void charTyped(char chr, int modifiers) {
    if (this.isClosed()) return;
    propertyPanelList.forEach(panel -> panel.charTyped(chr, modifiers));
  }

  public float getExtraHeight() {
    return extraHeight;
  }

  @Override
  public void mouseClicked(double mouseX, double mouseY, int button) {
    if (this.isClosed()) return;
    for (final OpalPropertyPanel propertyPanel : this.propertyPanelList) {
      if (propertyPanel.isHidden()) continue;
      propertyPanel.mouseClicked(mouseX, mouseY, button);
    }
  }

  @Override
  public void mouseScrolled(
      double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
    if (this.isClosed()) return;
    for (final OpalPropertyPanel propertyPanel : this.propertyPanelList) {
      if (propertyPanel.isHidden()) continue;
      propertyPanel.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }
  }

  @Override
  public void mouseReleased(double mouseX, double mouseY, int button) {
    if (this.isClosed()) return;
    for (final OpalPropertyPanel propertyPanel : this.propertyPanelList) {
      if (propertyPanel.isHidden()) continue;
      propertyPanel.mouseReleased(mouseX, mouseY, button);
    }
  }
}
