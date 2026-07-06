package miau.module.modules.render;

import static org.lwjgl.opengl.GL11.GL_ONE_MINUS_SRC_ALPHA;
import static org.lwjgl.opengl.GL11.GL_SRC_ALPHA;

import miau.event.EventManager;
import miau.event.impl.ShaderEvent;
import miau.module.Module;
import miau.property.properties.BooleanProperty;
import miau.property.properties.IntProperty;
import miau.ui.clickgui.ClickGui;
import miau.util.render.RenderUtil;
import miau.util.shader.KawaseBloom;
import miau.util.shader.KawaseBlur;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.shader.Framebuffer;

public class PostProcessing extends Module {
  private static final Minecraft mc = Minecraft.getMinecraft();
  public final BooleanProperty blur = new BooleanProperty("Blur", true);
  private final IntProperty blurIterations = new IntProperty("Blur Iterations", 2, 1, 8);
  private final IntProperty blurOffset = new IntProperty("Blur Offset", 3, 1, 10);

  public final BooleanProperty bloom = new BooleanProperty("Bloom", true);
  private final IntProperty bloomIterations = new IntProperty("Bloom Iterations", 2, 1, 8);
  private final IntProperty bloomOffset = new IntProperty("Bloom Offset", 3, 1, 10);

  private Framebuffer stencilFramebuffer = new Framebuffer(1, 1, false);

  public PostProcessing() {
    super("PostProcessing", false);
  }

  public void stuffToBlur(boolean isBloom) {
    ScaledResolution sr = new ScaledResolution(mc);

    if (mc.currentScreen instanceof ClickGui) {
      // We will let ClickGui implement drawForEffects or just fire an event
      ClickGui clickGui = (ClickGui) mc.currentScreen;
      clickGui.drawForEffects(isBloom);
    }

    // Fire ShaderEvent so modules can draw their rects to blur
    RenderUtil.resetColor();
    EventManager.call(new ShaderEvent(isBloom));
    RenderUtil.resetColor();
  }

  public void blurScreen() {
    if (!this.isEnabled()) return;

    if (blur.getValue()) {
      stencilFramebuffer = RenderUtil.createFrameBuffer(stencilFramebuffer);
      stencilFramebuffer.framebufferClear();
      stencilFramebuffer.bindFramebuffer(false);

      stuffToBlur(false);

      stencilFramebuffer.unbindFramebuffer();
      KawaseBlur.renderBlur(
          stencilFramebuffer.framebufferTexture,
          blurIterations.getValue().intValue(),
          blurOffset.getValue().intValue());
    }

    if (bloom.getValue()) {
      stencilFramebuffer = RenderUtil.createFrameBuffer(stencilFramebuffer);
      stencilFramebuffer.framebufferClear();
      stencilFramebuffer.bindFramebuffer(false);

      stuffToBlur(true);

      stencilFramebuffer.unbindFramebuffer();
      KawaseBloom.renderBlur(
          stencilFramebuffer.framebufferTexture,
          bloomIterations.getValue().intValue(),
          bloomOffset.getValue().intValue());
    }

    // Restore GL state for subsequent font/HUD rendering
    mc.getFramebuffer().bindFramebuffer(true);
    GlStateManager.enableBlend();
    GlStateManager.blendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
    GlStateManager.disableAlpha();
    GlStateManager.color(1.0f, 1.0f, 1.0f, 1.0f);
  }
}
