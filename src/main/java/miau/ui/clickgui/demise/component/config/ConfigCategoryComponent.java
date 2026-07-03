package miau.ui.clickgui.demise.component.config;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import miau.ui.clickgui.demise.IComponent;
import miau.ui.clickgui.demise.PanelGui;
import miau.util.font.FontRepository;
import miau.util.render.RenderUtil;
import net.minecraft.util.MathHelper;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.GL11;

public class ConfigCategoryComponent implements IComponent {
  private float x, y;
  private boolean isHovered, isSelected;
  private float interpolatedX;
  private float interpolatedLineWidth;
  private float scrollOffset = 0;
  private float targetScrollOffset = 0;
  private float maxScroll = 0;
  private String name = "Configs";
  private final List<ConfigComponent> configs = new ArrayList<ConfigComponent>();

  public ConfigCategoryComponent(float x, float y) {
    this.x = x;
    this.y = y;
    this.isSelected = false;
    this.isHovered = false;
    this.interpolatedX = x;

    configs.add(new ConfigComponent("default"));
  }

  public void initCategory() {
    for (ConfigComponent cc : configs) {
      cc.initCategory();
    }
  }

  public void initGui() {
    configs.clear();
    configs.add(new ConfigComponent("default"));
  }

  public void render(boolean shader) {
    float x = this.x;

    if (isSelected) {
      x += 3;
      float width = FontRepository.getFont("Inter Regular", 18f).getStringWidth(name);
      interpolatedLineWidth = animate(interpolatedLineWidth, width, 0.05f);
    } else {
      interpolatedLineWidth = animate(interpolatedLineWidth, 0, 0.05f);
    }

    if (isHovered) {
      x += 2.5f;
    }

    if (!PanelGui.dragging) {
      interpolatedX = animate(interpolatedX, x, 0.15f);
    } else {
      interpolatedX = x;
    }

    if (!shader) {
      FontRepository.getFont("Inter Regular", 18f)
          .draw(name, (double) interpolatedX, (double) y, Color.white.getRGB());
      RenderUtil.drawRect(
          interpolatedX,
          (float) (y + FontRepository.getFont("Inter Regular", 18f).height() - 2.6f),
          interpolatedLineWidth,
          0.5f,
          Color.white.getRGB());
    }

    if (isSelected) {
      handleScroll();

      float componentStartY =
          PanelGui.posY + 17 + FontRepository.getFont("Inter Bold", 35f).height();
      float viewHeight = 250;

      float totalHeight = 0;
      for (int i = 0; i < configs.size(); i++) {
        totalHeight += 40;
      }

      maxScroll = Math.max(0, totalHeight - viewHeight);
      scrollOffset = animate(scrollOffset, targetScrollOffset, 0.1f);

      RenderUtil.scissor(
          0, componentStartY - 2, PanelGui.posX + 450, viewHeight, PanelGui.interpolatedScale);
      GL11.glEnable(GL11.GL_SCISSOR_TEST);

      float componentOffsetY = componentStartY;
      for (ConfigComponent config : configs) {
        float moduleY = componentOffsetY - scrollOffset;
        config.setX(this.x + 60);
        config.setY(moduleY);
        config.render(shader);
        config.setVisible(
            moduleY + 35 >= componentStartY && moduleY <= componentStartY + viewHeight);

        componentOffsetY += 35;
      }

      GL11.glDisable(GL11.GL_SCISSOR_TEST);
    }
  }

  @Override
  public void drawScreen(int mouseX, int mouseY) {
    for (ConfigComponent cc : configs) {
      cc.drawScreen(mouseX, mouseY);
    }
  }

  @Override
  public void mouseClicked(int mouseX, int mouseY, int mouseButton) {
    for (ConfigComponent cc : configs) {
      cc.mouseClicked(mouseX, mouseY, mouseButton);
    }
  }

  @Override
  public void keyTyped(char typedChar, int keyCode) {
    for (ConfigComponent cc : configs) {
      cc.keyTyped(typedChar, keyCode);
    }
  }

  @Override
  public void mouseReleased(int mouseX, int mouseY, int state) {
    for (ConfigComponent cc : configs) {
      cc.mouseReleased(mouseX, mouseY, state);
    }
  }

  public void handleScroll() {
    int wheel = Mouse.getDWheel();
    if (wheel != 0) {
      float scrollAmount = wheel > 0 ? -25 : 25;
      targetScrollOffset = MathHelper.clamp_float(targetScrollOffset + scrollAmount, 0, maxScroll);
    }
  }

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

  public boolean isHovered() {
    return isHovered;
  }

  public void setHovered(boolean hovered) {
    isHovered = hovered;
  }

  public boolean isSelected() {
    return isSelected;
  }

  public void setSelected(boolean selected) {
    isSelected = selected;
  }

  private float animate(float current, float target, float speed) {
    return current + (target - current) / Math.max(1, speed * 10);
  }
}
