package miau.ui.clickgui.opal.panels;

import miau.module.Module;
import miau.ui.clickgui.opal.*;
import miau.ui.clickgui.opal.properties.OpalPropertyProvider;
import miau.util.animation.Easing;
import miau.util.font.Font;
import miau.util.font.FontRepository;
import net.minecraft.client.Minecraft;
import net.minecraft.util.EnumChatFormatting;
import org.lwjgl.input.Keyboard;

public class OpalModulePanel extends OpalPanelComponent {

  private final Module module;
  private OpalAnimation hoverAnimation, toggleAnimation;
  private final OpalAnimation expandAnimation = new OpalAnimation(Easing.EASE_OUT_SINE, 125);
  private boolean lastModule, expanded, selectingBind;
  private final OpalPropertyProvider propertyProvider;

  private static final Minecraft mc = Minecraft.getMinecraft();

  public OpalModulePanel(final Module module) {
    this.module = module;
    this.propertyProvider =
        new OpalPropertyProvider(module, this::isExpandedAnimation, this::isLastModule);
  }

  private boolean isExpandedAnimation() {
    return expanded || expandAnimation.getValue() > 0F;
  }

  public boolean isExpanded() {
    return expanded;
  }

  public void setLastModule(final boolean lastModule) {
    this.lastModule = lastModule;
  }

  public boolean isLastModule() {
    return lastModule;
  }

  public Module getModule() {
    return module;
  }

  @Override
  public void render(int mouseX, int mouseY, float delta) {
    handleAnimations(mouseX, mouseY);

    final int baseColor = 0xff1e1e2d;
    final int accentColor = OpalColorUtil.getClientAccent();
    final int darkAccent = OpalColorUtil.getClientAccentDark();

    if (!lastModule) {
      OpalRenderUtil.rect(x, y, width, height, 0x66000000);
      OpalRenderUtil.rect(x, y, width, height, OpalColorUtil.applyOpacity(baseColor, 0.7F));
      OpalRenderUtil.rectGradient(
          x,
          y,
          width,
          height,
          OpalColorUtil.applyOpacity(accentColor, toggleAnimation.getValue()),
          OpalColorUtil.applyOpacity(darkAccent, toggleAnimation.getValue()),
          0);
    } else {
      OpalRenderUtil.roundedRectVarying(x, y, width, height, 0, 0, 5, 5, 0x66000000);
      OpalRenderUtil.roundedRectVarying(
          x, y, width, height, 0, 0, 5, 5, OpalColorUtil.applyOpacity(baseColor, 0.7F));
      OpalRenderUtil.roundedRectVaryingGradient(
          x,
          y,
          width,
          height,
          0,
          0,
          5,
          5,
          OpalColorUtil.applyOpacity(accentColor, toggleAnimation.getValue()),
          OpalColorUtil.applyOpacity(darkAccent, toggleAnimation.getValue()),
          0);
    }

    // Module name
    Font font = FontRepository.getFont("productsans-medium", 16);
    if (font != null) {
      int textColor = module.isEnabled() ? 0xFFFFFFFF : OpalColorUtil.darker(0xFFFFFFFF, 0.2F);
      font.draw(module.getName().toLowerCase(), x + 6, y + 12.5F - 4, textColor);
    }

    // Expand icon + bind display
    boolean hasProperties = propertyProvider.isHasProperties();
    if (hasProperties && !selectingBind && !OpalClickGui.displayingBinds) {
      float iconSize = 12;
      float iconX = x + width - 17;
      float iconY = y + 4;
      OpalRenderUtil.rotate(
          expandAnimation.getValue() * 180,
          iconX,
          iconY,
          iconSize,
          iconSize,
          () -> {
            Font iconFont = FontRepository.getFont("materialicons-regular", 24);
            if (iconFont != null) {
              String icon = "\uE5CF"; // expand_more arrow
              iconFont.draw(icon, iconX - 6, iconY - 8, 0xFFFFFFFF);
            }
          });
    }

    // Bind display
    String keyString = null;
    if (selectingBind) {
      keyString =
          EnumChatFormatting.GRAY
              + "["
              + EnumChatFormatting.WHITE
              + "..."
              + EnumChatFormatting.GRAY
              + "]";
    } else if (OpalClickGui.displayingBinds && module.getKey() != 0) {
      String key = getKeyName(module.getKey());
      keyString =
          EnumChatFormatting.GRAY
              + "["
              + EnumChatFormatting.WHITE
              + key
              + EnumChatFormatting.GRAY
              + "]";
    }

    if (keyString != null) {
      Font smallFont = FontRepository.getFont("productsans-medium", 14);
      if (smallFont != null) {
        smallFont.draw(
            keyString, x + width - smallFont.width(keyString) - 5, y + 12 - 4, 0xFFFFFFFF);
      }
    }

    if (expandAnimation.isFinished() && !isExpanded()) return;

    propertyProvider.setX(x);
    propertyProvider.setY(y + 20);
    propertyProvider.setWidth(width);
    propertyProvider.render(mouseX, mouseY, delta);
  }

  private void handleAnimations(final float mouseX, final float mouseY) {
    final float hoverFactor =
        OpalHoverUtility.isHovering(
                x,
                y,
                width,
                height - (isExpanded() ? propertyProvider.getExtraHeight() : 0),
                mouseX,
                mouseY)
            ? 0.7F
            : 0;
    if (this.hoverAnimation == null) {
      this.hoverAnimation = new OpalAnimation(Easing.EASE_OUT_SINE, 150);
      this.hoverAnimation.setValue(hoverFactor);
    } else {
      this.hoverAnimation.run(hoverFactor);
    }

    final float toggledFactor = module.isEnabled() ? 0.4F : 0;
    if (this.toggleAnimation == null) {
      this.toggleAnimation = new OpalAnimation(Easing.EASE_OUT_SINE, 150);
      this.toggleAnimation.setValue(toggledFactor);
    } else {
      this.toggleAnimation.run(toggledFactor);
    }
  }

  @Override
  public void mouseClicked(double mouseX, double mouseY, int button) {
    if (selectingBind) {
      module.setKey(button);
      selectingBind = OpalClickGui.selectingBind = false;
      return;
    }

    if (OpalHoverUtility.isHovering(
        x,
        y,
        width,
        height - (isExpanded() ? propertyProvider.getExtraHeight() : 0),
        mouseX,
        mouseY)) {
      switch (button) {
        case 0:
          module.toggle();
          break;
        case 1:
          if (!module.getValues().isEmpty()) {
            expanded = !expanded;
          }
          break;
        case 2:
          selectingBind = OpalClickGui.selectingBind = true;
          break;
      }
    }

    propertyProvider.mouseClicked(mouseX, mouseY, button);
  }

  @Override
  public void keyPressed(int keyCode) {
    if (selectingBind) {
      if (keyCode != Keyboard.KEY_ESCAPE) {
        module.setKey(keyCode);
      }
      selectingBind = OpalClickGui.selectingBind = false;
      return;
    }
    propertyProvider.keyPressed(keyCode);
  }

  @Override
  public void charTyped(char chr, int modifiers) {
    propertyProvider.charTyped(chr, modifiers);
  }

  public float getAddedHeight() {
    return this.propertyProvider.getExtraHeight();
  }

  public OpalAnimation getExpandAnimation() {
    return expandAnimation;
  }

  @Override
  public void mouseScrolled(
      double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
    propertyProvider.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
  }

  @Override
  public void mouseReleased(double mouseX, double mouseY, int button) {
    propertyProvider.mouseReleased(mouseX, mouseY, button);
  }

  @Override
  public void init() {
    propertyProvider.init();
  }

  @Override
  public void close() {
    propertyProvider.close();
  }

  private static String getKeyName(int key) {
    if (key <= 0) return "NONE";
    try {
      return Keyboard.getKeyName(key);
    } catch (Exception e) {
      return "KEY" + key;
    }
  }
}
