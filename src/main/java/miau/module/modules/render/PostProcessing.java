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
  public final BooleanProperty bloom = new BooleanProperty("Bloom", false);
  private final IntProperty blurIterations = new IntProperty("Blur Iterations", 2, 1, 8);
  private final IntProperty blurOffset = new IntProperty("Blur Offset", 3, 1, 10);
  private final IntProperty bloomIterations = new IntProperty("Bloom Iterations", 3, 1, 8);
  private final IntProperty bloomOffset = new IntProperty("Bloom Offset", 2, 1, 10);

  private Framebuffer bloomFramebuffer = new Framebuffer(1, 1, false);
  private Framebuffer blurFramebuffer = new Framebuffer(1, 1, false);

  public PostProcessing() {
    super("PostProcessing", false);
  }

  public void stuffToDraw(int pass) {
    ScaledResolution sr = new ScaledResolution(mc);

    if (mc.currentScreen instanceof ClickGui) {
      ClickGui clickGui = (ClickGui) mc.currentScreen;
      clickGui.drawForEffects(pass == ShaderEvent.BLOOM_PASS);
    }

    RenderUtil.resetColor();
    EventManager.call(new ShaderEvent(pass));
    RenderUtil.resetColor();
  }

  public void blurScreen() {
    if (!this.isEnabled()) return;

    // ── Bloom pass ──
    if (bloom.getValue()) {
      bloomFramebuffer = RenderUtil.createFrameBuffer(bloomFramebuffer);
      bloomFramebuffer.framebufferClear();
      bloomFramebuffer.bindFramebuffer(false);

      stuffToDraw(ShaderEvent.BLOOM_PASS);

      bloomFramebuffer.unbindFramebuffer();
      KawaseBloom.renderBlur(
          bloomFramebuffer.framebufferTexture,
          bloomIterations.getValue().intValue(),
          bloomOffset.getValue().intValue());
    }

    // ── Blur pass ──
    if (blur.getValue()) {
      blurFramebuffer = RenderUtil.createFrameBuffer(blurFramebuffer);
      blurFramebuffer.framebufferClear();
      blurFramebuffer.bindFramebuffer(false);

      stuffToDraw(ShaderEvent.BLUR_PASS);

      blurFramebuffer.unbindFramebuffer();
      KawaseBlur.renderBlur(
          blurFramebuffer.framebufferTexture,
          blurIterations.getValue().intValue(),
          blurOffset.getValue().intValue());
    }

    // Restore GL state for subsequent font/HUD rendering
    mc.getFramebuffer().bindFramebuffer(true);
    GlStateManager.enableBlend();
    GlStateManager.blendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
    GlStateManager.disableAlpha();
    GlStateManager.color(1.0f, 1.0f, 1.0f, 1.0f);
  }
}
