package miau.ui.clickgui.opal.panels;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import miau.Miau;
import miau.module.Module;
import miau.ui.clickgui.opal.OpalAnimation;
import miau.ui.clickgui.opal.OpalColorUtil;
import miau.ui.clickgui.opal.OpalHoverUtility;
import miau.ui.clickgui.opal.OpalPanelComponent;
import miau.ui.clickgui.opal.OpalScroller;
import miau.util.animation.Easing;
import miau.util.font.Font;
import miau.util.font.FontRepository;
import miau.util.render.RenderUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ScaledResolution;
import org.lwjgl.opengl.GL11;

public class OpalCategoryPanel extends OpalPanelComponent {

  private final OpalScroller scroller = new OpalScroller();
  private final String categoryName;
  private final List<OpalModulePanel> modulePanelList = new ArrayList<>();

  private OpalAnimation openAnimation;
  private final int panelIndex;
  private boolean closing;
  private int lastPanelIndex;
  private int totalPanels;

  private static final Minecraft mc = Minecraft.getMinecraft();

  public OpalCategoryPanel(
      final String categoryName, final List<Module> modules, final int panelIndex) {
    this.categoryName = categoryName;
    this.panelIndex = panelIndex;

    if (modules != null) {
      for (Module module : modules) {
        modulePanelList.add(new OpalModulePanel(module));
      }
    }
    modulePanelList.sort(Comparator.comparing(p -> p.getModule().getName().toLowerCase()));
  }

  public void setLastPanelInfo(int lastPanelIndex, int totalPanels) {
    this.lastPanelIndex = lastPanelIndex;
    this.totalPanels = totalPanels;
  }

  public void init() {
    if (modulePanelList.isEmpty()) {
      if (Miau.moduleManager != null) {
        LinkedHashMap<String, List<Module>> cats = Miau.moduleManager.getModulesByCategory();
        List<Module> modules = cats.get(categoryName);
        if (modules != null) {
          for (Module module : modules) {
            modulePanelList.add(new OpalModulePanel(module));
          }
          modulePanelList.sort(Comparator.comparing(p -> p.getModule().getName().toLowerCase()));
        }
      }
    }
    this.openAnimation = new OpalAnimation(Easing.EASE_OUT_SINE, 100 + (panelIndex * 80L));
    closing = false;
    for (OpalModulePanel panel : modulePanelList) {
      panel.init();
    }
  }

  public void close() {
    closing = true;
    for (OpalModulePanel panel : modulePanelList) {
      panel.close();
    }
  }

  @Override
  public void render(int mouseX, int mouseY, float delta) {
    openAnimation.run(closing ? 0 : 1);

    if (lastPanelIndex == panelIndex && openAnimation.isFinished() && closing) {
      return;
    }

    float[] currentY = {y + height};
    float totalHeight = getTotalHeight();

    float openAnimationValue = openAnimation.getValue();
    ScaledResolution sr = new ScaledResolution(mc);
    float scissorHeight = Math.min(sr.getScaledHeight() - y, totalHeight * openAnimationValue);
    float scrollOffset = scroller.getAnimation().getValue();

    // Apply global alpha via color
    int accentColor = OpalColorUtil.getClientAccent();
    int darkAccent = OpalColorUtil.getClientAccentDark();

    // Save GL state for scissor
    GL11.glEnable(GL11.GL_SCISSOR_TEST);
    float scale = sr.getScaleFactor();
    float scissorX = x * scale;
    float scissorY = (sr.getScaledHeight() - (y + scissorHeight)) * scale;
    float scissorW = width * scale;
    float scissorH = scissorHeight * scale;
    GL11.glScissor((int) scissorX, (int) scissorY, (int) scissorW, (int) Math.max(0, scissorH));

    // Draw category background - rounded rect top
    int bgColor = OpalColorUtil.applyOpacity(0xff0f0f0f, 0.85F * openAnimationValue);
    drawRoundedRectTop(x, y + scrollOffset, width, height, 5, bgColor);

    // Draw category name
    Font font = FontRepository.getFont("productsans-bold", 18);
    if (font != null) {
      String displayName = categoryName.toLowerCase();
      float textWidth = font.width(displayName);
      float textX = x + 5;
      float textY = y + scrollOffset + 13;
      font.draw(
          displayName,
          textX,
          textY - 4,
          OpalColorUtil.applyOpacity(0xFFFFFFFF, openAnimationValue));
    }

    // Draw module panels
    for (int i = 0; i < modulePanelList.size(); i++) {
      OpalModulePanel panel = modulePanelList.get(i);
      float panelHeight =
          this.height + (panel.getExpandAnimation().getValue() * panel.getAddedHeight());

      panel.setDimensions(x, currentY[0] + scrollOffset, width, panelHeight);
      panel.setLastModule(i == modulePanelList.size() - 1);

      panel.render(mouseX, mouseY, delta);

      currentY[0] += panelHeight;
    }

    GL11.glDisable(GL11.GL_SCISSOR_TEST);

    scroller.onScroll(getMaxOffset(totalHeight));
  }

  public void mouseClicked(double mouseX, double mouseY, int button) {
    for (OpalModulePanel panel : modulePanelList) {
      panel.mouseClicked(mouseX, mouseY, button);
    }
  }

  public void keyPressed(int keyCode) {
    for (OpalModulePanel panel : modulePanelList) {
      panel.keyPressed(keyCode);
    }
  }

  public void charTyped(char chr, int modifiers) {
    for (OpalModulePanel panel : modulePanelList) {
      panel.charTyped(chr, modifiers);
    }
  }

  public void mouseReleased(double mouseX, double mouseY, int button) {
    for (OpalModulePanel panel : modulePanelList) {
      panel.mouseReleased(mouseX, mouseY, button);
    }
  }

  public void mouseScrolled(
      double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
    float totalHeight = getTotalHeight();
    if (OpalHoverUtility.isHovering(x, y, width, totalHeight, mouseX, mouseY)) {
      scroller.addScroll(verticalAmount, getMaxOffset(totalHeight));
    }
    for (OpalModulePanel panel : modulePanelList) {
      panel.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }
  }

  private float getTotalHeight() {
    float totalHeight = this.height;
    for (OpalModulePanel panel : modulePanelList) {
      panel.getExpandAnimation().run(panel.isExpanded() ? 1 : 0);
      totalHeight += this.height + (panel.getExpandAnimation().getValue() * panel.getAddedHeight());
    }
    return totalHeight;
  }

  private float getMaxOffset(final float totalHeight) {
    ScaledResolution sr = new ScaledResolution(mc);
    float relativeScreenHeight = sr.getScaledHeight() - y;
    float scissorHeight = Math.min(relativeScreenHeight, totalHeight * openAnimation.getValue());
    float overflowPadding = scissorHeight == relativeScreenHeight ? y : 0;
    return Math.max(0, totalHeight - scissorHeight + overflowPadding);
  }

  private void drawRoundedRectTop(
      float x, float y, float width, float height, float radius, int color) {
    // Draw a rectangle with rounded top corners using GL triangle fan
    GL11.glEnable(GL11.GL_BLEND);
    GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
    GL11.glDisable(GL11.GL_TEXTURE_2D);

    RenderUtil.setColor(color);
    GL11.glBegin(GL11.GL_TRIANGLE_FAN);
    // Center of rounded area
    GL11.glVertex2f(x + radius, y + radius);
    // Top edge
    for (int i = 0; i <= 90; i += 5) {
      double rad = Math.toRadians(i);
      GL11.glVertex2f(
          x + radius + (float) (radius * Math.cos(Math.PI - rad)),
          y + radius + (float) (radius * Math.sin(Math.PI - rad)));
    }
    // Right edge, bottom corners, etc - simple rect for rest
    GL11.glVertex2f(x + width, y);
    GL11.glVertex2f(x + width, y + height);
    GL11.glVertex2f(x, y + height);
    GL11.glVertex2f(x, y);
    GL11.glEnd();

    GL11.glEnable(GL11.GL_TEXTURE_2D);
    GL11.glDisable(GL11.GL_BLEND);
  }
}
