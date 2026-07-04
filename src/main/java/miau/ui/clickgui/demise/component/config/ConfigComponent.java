package miau.ui.clickgui.demise.component.config;

import java.awt.*;
import java.io.File;
import java.util.Objects;
import miau.config.Config;
import miau.ui.clickgui.demise.IComponent;
import miau.util.client.ChatUtil;
import miau.util.demise.RoundedUtils;
import miau.util.font.FontRepository;

public class ConfigComponent implements IComponent {
  private float x, y;
  private boolean isHovered, saveHovered, deleteHovered;
  private Color interpolatedColor = new Color(20, 20, 20, 150);
  private Color interpolatedColor1 = new Color(0, 0, 0, 0);
  public boolean visible;
  private float slideProgress = 0f;
  private String name;

  public ConfigComponent(String name) {
    this.name = name;
  }

  public void initCategory() {
    slideProgress = 0;
  }

  public void render(boolean shader) {
    float width = 375;
    slideProgress = animate(slideProgress, visible ? 1 : 0, 0.1f);
    float slideOffset = (width / 4) * (1.0f - slideProgress);

    if (!shader) {
      if (isHovered) {
        interpolatedColor = interpolateColorC(interpolatedColor, new Color(35, 35, 35, 190), 0.1f);
      } else {
        interpolatedColor = interpolateColorC(interpolatedColor, new Color(20, 20, 20, 150), 0.1f);
      }

      String currentConfig = "default";
      if (Objects.equals(currentConfig, name)) {
        interpolatedColor1 =
            interpolateColorC(interpolatedColor1, new Color(50, 50, 50, 150), 0.1f);
      } else {
        interpolatedColor1 = interpolateColorC(interpolatedColor1, new Color(0, 0, 0, 0), 0.1f);
      }

      RoundedUtils.drawRound(x + slideOffset, y, width, 30, 8, interpolatedColor);
      RoundedUtils.drawRound(x + slideOffset, y, width, 30, 8, interpolatedColor1);

      FontRepository.getFont("Inter Regular", 20f)
          .draw(name, (double) (x + 7 + slideOffset), (double) (y + 11), Color.white.getRGB());

      float saveWidth = FontRepository.getFont("Inter Regular", 14f).getStringWidth("save");
      float deleteWidth = FontRepository.getFont("Inter Regular", 14f).getStringWidth("delete");

      Color saveColor = saveHovered ? new Color(255, 255, 255) : new Color(179, 179, 179);
      Color deleteColor = deleteHovered ? new Color(255, 255, 255) : new Color(179, 179, 179);

      FontRepository.getFont("Inter Regular", 14f)
          .draw(
              "save",
              (double) (x + width - 10 - deleteWidth - 5 - saveWidth + slideOffset),
              (double) (y + 13),
              saveColor.getRGB());
      FontRepository.getFont("Inter Regular", 14f)
          .draw(
              "delete",
              (double) (x + width - 10 - deleteWidth + slideOffset),
              (double) (y + 13),
              deleteColor.getRGB());
    } else {
      RoundedUtils.drawRound(x + slideOffset, y, width, 30, 8, Color.black);
    }
  }

  @Override
  public void drawScreen(int mouseX, int mouseY) {
    float width = 375;
    float slideOffset = (width / 4) * (1.0f - slideProgress);
    float saveWidth = FontRepository.getFont("Inter Regular", 14f).getStringWidth("save");
    float deleteWidth = FontRepository.getFont("Inter Regular", 14f).getStringWidth("delete");

    this.isHovered =
        isHovered(
            x + slideOffset, y, width - 15 - deleteWidth - 15 - saveWidth, 30, mouseX, mouseY);
    this.saveHovered =
        isHovered(
            x + width - 10 - deleteWidth - 5 - saveWidth + slideOffset,
            y,
            saveWidth,
            30,
            mouseX,
            mouseY);
    this.deleteHovered =
        isHovered(x + width - 10 - deleteWidth + slideOffset, y, deleteWidth, 30, mouseX, mouseY);
  }

  @Override
  public void mouseClicked(int mouseX, int mouseY, int mouseButton) {
    if (visible && mouseButton == 0) {
      if (isHovered) {
        Config config = new Config(name, true);
        if (config.file.exists()) {
          config.load();
          ChatUtil.display("Loaded config " + name);
        } else {
          ChatUtil.display("Failed to load config " + name + "!");
        }
      } else if (saveHovered) {
        Config config = new Config(name, true);
        config.save();
        ChatUtil.display("Saved config " + name);
      } else if (deleteHovered) {
        File configFile = new File("config", name + ".json");
        if (!configFile.exists()) {
          ChatUtil.display("Config does not exist: " + name);
          return;
        }
        String message =
            configFile.delete() ? "Removed config: " + name : "Failed to remove config: " + name;
        ChatUtil.display(message);
      }
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

  public boolean isVisible() {
    return visible;
  }

  public void setVisible(boolean visible) {
    this.visible = visible;
  }

  private Color interpolateColorC(Color color1, Color color2, float amount) {
    amount = Math.min(1, Math.max(0, amount));
    return new Color(
        (int) (color1.getRed() + (color2.getRed() - color1.getRed()) * amount),
        (int) (color1.getGreen() + (color2.getGreen() - color1.getGreen()) * amount),
        (int) (color1.getBlue() + (color2.getBlue() - color1.getBlue()) * amount),
        (int) (color1.getAlpha() + (color2.getAlpha() - color1.getAlpha()) * amount));
  }

  private float animate(float current, float target, float speed) {
    return current + (target - current) / Math.max(1, speed * 10);
  }

  private boolean isHovered(
      float x, float y, float width, float height, float mouseX, float mouseY) {
    return mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= y + height;
  }
}
