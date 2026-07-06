package miau.ui.clickgui.opal;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import miau.Miau;
import miau.module.Module;
import miau.ui.clickgui.opal.panels.OpalCategoryPanel;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.ScaledResolution;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.GL11;

public class OpalClickGui extends GuiScreen {

  private final List<OpalCategoryPanel> categoryPanelList = new ArrayList<>();
  public static boolean displayingBinds, selectingBind, typingString;

  public OpalClickGui() {
    LinkedHashMap<String, List<Module>> categoryMap = Miau.moduleManager.getModulesByCategory();
    int index = 0;
    for (Map.Entry<String, List<Module>> entry : categoryMap.entrySet()) {
      // Skip "Search" and "Themes" special categories in Opal mode
      if (entry.getKey().equalsIgnoreCase("Search") || entry.getKey().equalsIgnoreCase("Themes"))
        continue;
      categoryPanelList.add(new OpalCategoryPanel(entry.getKey(), entry.getValue(), index));
      index++;
    }
  }

  @Override
  public void initGui() {
    super.initGui();
    for (OpalCategoryPanel panel : categoryPanelList) {
      panel.init();
    }
  }

  @Override
  public void onGuiClosed() {
    if (selectingBind) return;
    for (OpalCategoryPanel panel : categoryPanelList) {
      panel.close();
    }
    // Toggle off ClickGUI module
    miau.module.modules.render.ClickGUI guiModule =
        (miau.module.modules.render.ClickGUI)
            Miau.moduleManager.getModule(miau.module.modules.render.ClickGUI.class);
    if (guiModule != null) {
      guiModule.setEnabled(false);
    }
  }

  @Override
  public void drawScreen(int mouseX, int mouseY, float partialTicks) {
    super.drawScreen(mouseX, mouseY, partialTicks);

    displayingBinds = Keyboard.isKeyDown(Keyboard.KEY_TAB);

    GL11.glPushMatrix();
    GL11.glEnable(GL11.GL_BLEND);
    GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
    GL11.glDisable(GL11.GL_TEXTURE_2D);

    ScaledResolution sr = new ScaledResolution(mc);

    int categoryAmount = categoryPanelList.size();
    for (int i = 0; i < categoryAmount; i++) {
      OpalCategoryPanel panel = categoryPanelList.get(i);

      float y = 25;
      float width = 110;
      float spacing = 10;
      float height = 20;

      float totalWidth = categoryAmount * width + (categoryAmount - 1) * spacing;
      float startX = (sr.getScaledWidth() - totalWidth) / 2;
      float x = startX + i * (width + spacing);

      panel.setDimensions(x, y, width, height);
      panel.render(mouseX, mouseY, partialTicks);
    }

    GL11.glEnable(GL11.GL_TEXTURE_2D);
    GL11.glDisable(GL11.GL_BLEND);
    GL11.glPopMatrix();
  }

  @Override
  public void mouseClicked(int mouseX, int mouseY, int mouseButton) throws IOException {
    super.mouseClicked(mouseX, mouseY, mouseButton);
    for (OpalCategoryPanel panel : categoryPanelList) {
      panel.mouseClicked(mouseX, mouseY, mouseButton);
    }
  }

  @Override
  public void mouseReleased(int mouseX, int mouseY, int state) {
    super.mouseReleased(mouseX, mouseY, state);
    for (OpalCategoryPanel panel : categoryPanelList) {
      panel.mouseReleased(mouseX, mouseY, state);
    }
  }

  @Override
  public void handleMouseInput() throws IOException {
    super.handleMouseInput();
    int wheel = Mouse.getDWheel();
    if (wheel != 0) {
      int mouseX = Mouse.getEventX() * this.width / mc.displayWidth;
      int mouseY = this.height - Mouse.getEventY() * this.height / mc.displayHeight - 1;
      ScaledResolution sr = new ScaledResolution(mc);
      for (OpalCategoryPanel panel : categoryPanelList) {
        if (mouseX >= panel.getX()
            && mouseX <= panel.getX() + panel.getWidth()
            && mouseY >= panel.getY()
            && mouseY <= panel.getY() + panel.getHeight()) {
          panel.mouseScrolled(mouseX, mouseY, 0, wheel / 120.0);
        }
      }
    }
  }

  @Override
  protected void keyTyped(char typedChar, int keyCode) throws IOException {
    if (keyCode == Keyboard.KEY_ESCAPE) {
      if (!selectingBind) {
        mc.displayGuiScreen(null);
      }
      return;
    }
    for (OpalCategoryPanel panel : categoryPanelList) {
      panel.keyPressed(keyCode);
      panel.charTyped(typedChar, keyCode);
    }
  }

  @Override
  public boolean doesGuiPauseGame() {
    return false;
  }
}
