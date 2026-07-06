package miau.ui.clickgui.opal;

/**
 * Base class for all Opal clickgui panels - provides position and dimension tracking, and default
 * implementations for input handling.
 */
public class OpalPanelComponent {
  protected float x, y, width, height;

  public OpalPanelComponent() {}

  public OpalPanelComponent(float x, float y, float width, float height) {
    this.x = x;
    this.y = y;
    this.width = width;
    this.height = height;
  }

  public OpalPanelComponent(float x, float y) {
    this.x = x;
    this.y = y;
  }

  public void init() {}

  public void close() {}

  public void render(int mouseX, int mouseY, float delta) {}

  public void mouseClicked(double mouseX, double mouseY, int button) {}

  public void mouseScrolled(
      double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {}

  public void mouseReleased(double mouseX, double mouseY, int button) {}

  public void keyPressed(int keyCode) {}

  public void charTyped(char chr, int modifiers) {}

  public float getX() {
    return x;
  }

  public void setX(float x) {
    this.x = x;
  }

  public float getY() {
    return y;
  }

  public void setY(float y) {
    this.y = y;
  }

  public float getWidth() {
    return width;
  }

  public void setWidth(float width) {
    this.width = width;
  }

  public float getHeight() {
    return height;
  }

  public void setHeight(float height) {
    this.height = height;
  }

  public void setDimensions(float x, float y, float width, float height) {
    this.x = x;
    this.y = y;
    this.width = width;
    this.height = height;
  }
}
