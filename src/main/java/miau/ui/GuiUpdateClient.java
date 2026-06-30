package miau.ui;

import java.awt.Desktop;
import java.io.IOException;
import java.net.URI;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;

public class GuiUpdateClient extends GuiScreen {
  private final GuiScreen parent;
  private final String currentVersion;
  private final String latestVersion;
  private final String updateUrl;

  public GuiUpdateClient(
      GuiScreen parent, String currentVersion, String latestVersion, String updateUrl) {
    this.parent = parent;
    this.currentVersion = currentVersion;
    this.latestVersion = latestVersion;
    this.updateUrl = updateUrl;
  }

  @Override
  public void initGui() {
    this.buttonList.clear();
    int centerX = this.width / 2;
    int centerY = this.height / 2;

    this.buttonList.add(new GuiButton(0, centerX - 105, centerY + 20, 100, 20, "Update"));
    this.buttonList.add(new GuiButton(1, centerX + 5, centerY + 20, 100, 20, "OK"));
  }

  @Override
  protected void actionPerformed(GuiButton button) throws IOException {
    if (button.id == 0) {
      try {
        Desktop.getDesktop().browse(new URI(this.updateUrl));
      } catch (Exception e) {
      }
    } else if (button.id == 1) {
      this.mc.displayGuiScreen(this.parent);
    }
  }

  @Override
  public void drawScreen(int mouseX, int mouseY, float partialTicks) {
    if (this.parent != null) {
      this.parent.drawScreen(0, 0, partialTicks);
    } else {
      this.drawDefaultBackground();
    }

    drawRect(0, 0, this.width, this.height, 0x80000000);

    int centerX = this.width / 2;
    int centerY = this.height / 2;

    int panelWidth = 240;
    int panelHeight = 110;
    int left = centerX - panelWidth / 2;
    int top = centerY - panelHeight / 2 - 5;
    int right = centerX + panelWidth / 2;
    int bottom = centerY + panelHeight / 2 + 5;

    drawRect(left - 2, top - 2, right + 2, bottom + 2, 0xFF2A2A2A);
    drawRect(left, top, right, bottom, 0xFF141414);

    this.drawCenteredString(
        this.fontRendererObj, "§c§lClient Outdated!", centerX, centerY - 40, 0xFFFFFF);
    this.drawCenteredString(
        this.fontRendererObj,
        "§7Your version: §c" + this.currentVersion,
        centerX,
        centerY - 20,
        0xFFFFFF);
    this.drawCenteredString(
        this.fontRendererObj,
        "§7Latest version: §a" + this.latestVersion,
        centerX,
        centerY - 5,
        0xFFFFFF);

    super.drawScreen(mouseX, mouseY, partialTicks);
  }
}
