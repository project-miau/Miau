package miau.util.shader;

import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL14.GL_MIRRORED_REPEAT;

import java.util.ArrayList;
import java.util.List;
import miau.util.render.RenderUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.shader.Framebuffer;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;

public class KawaseBlur {

  private static final Minecraft mc = Minecraft.getMinecraft();
  public static ShaderUtils kawaseDown = new ShaderUtils("kawaseDown");
  public static ShaderUtils kawaseUp = new ShaderUtils("kawaseUp");
  public static Framebuffer framebuffer = new Framebuffer(1, 1, false);
  private static int currentIterations;

  private static final List<Framebuffer> framebufferList = new ArrayList<>();

  private static void initFrameBuffers(int iterations) {
    for (Framebuffer fb : framebufferList) {
      fb.deleteFramebuffer();
    }
    framebufferList.clear();

    framebufferList.add(framebuffer = RenderUtil.createFrameBuffer(framebuffer));

    for (int i = 1; i <= iterations; i++) {
      Framebuffer currentBuffer =
          new Framebuffer(
              (int) (mc.displayWidth / Math.pow(2, i)),
              (int) (mc.displayHeight / Math.pow(2, i)),
              false);
      currentBuffer.setFramebufferFilter(GL_LINEAR);

      GL11.glBindTexture(GL_TEXTURE_2D, currentBuffer.framebufferTexture);
      GL11.glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_MIRRORED_REPEAT);
      GL11.glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_MIRRORED_REPEAT);
      GL11.glBindTexture(GL_TEXTURE_2D, 0);

      framebufferList.add(currentBuffer);
    }
  }

  private static void setupUniforms(float offset) {
    kawaseDown.setUniformf("offset", offset, offset);
    kawaseDown.setUniformi("inTexture", 0);
    kawaseDown.setUniformi("check", 0);
    kawaseDown.setUniformf(
        "halfpixel",
        1.0f / framebufferList.get(1).framebufferWidth,
        1.0f / framebufferList.get(1).framebufferHeight);
    kawaseDown.setUniformf(
        "iResolution",
        framebufferList.get(1).framebufferWidth,
        framebufferList.get(1).framebufferHeight);
  }

  public static void renderBlur(int stencilFrameBufferTexture, int iterations, float offset) {
    if (currentIterations != iterations
        || framebuffer.framebufferWidth != mc.displayWidth
        || framebuffer.framebufferHeight != mc.displayHeight) {
      initFrameBuffers(iterations);
      currentIterations = iterations;
    }

    // Downsample
    renderFBO(
        framebufferList.get(1), mc.getFramebuffer().framebufferTexture, kawaseDown, offset, 0);

    for (int i = 1; i < iterations; i++) {
      renderFBO(
          framebufferList.get(i + 1),
          framebufferList.get(i).framebufferTexture,
          kawaseDown,
          offset,
          i);
    }

    // Upsample
    for (int i = iterations; i > 1; i--) {
      renderFBO(
          framebufferList.get(i - 1),
          framebufferList.get(i).framebufferTexture,
          kawaseUp,
          offset,
          i - 1);
    }

    // Final compose
    Framebuffer lastBuffer = framebufferList.get(0);
    lastBuffer.framebufferClear();
    lastBuffer.bindFramebuffer(false);

    kawaseUp.init();
    kawaseUp.setUniformf("offset", offset, offset);
    kawaseUp.setUniformi("inTexture", 0);
    kawaseUp.setUniformi("check", 1);
    kawaseUp.setUniformi("textureToCheck", 16);
    kawaseUp.setUniformf(
        "halfpixel", 1.0f / lastBuffer.framebufferWidth, 1.0f / lastBuffer.framebufferHeight);
    kawaseUp.setUniformf("iResolution", lastBuffer.framebufferWidth, lastBuffer.framebufferHeight);

    GL13.glActiveTexture(GL13.GL_TEXTURE16);
    GL11.glBindTexture(GL_TEXTURE_2D, stencilFrameBufferTexture);
    GL13.glActiveTexture(GL13.GL_TEXTURE0);
    GL11.glBindTexture(GL_TEXTURE_2D, framebufferList.get(1).framebufferTexture);

    ShaderUtils.drawQuads();
    kawaseUp.unload();

    mc.getFramebuffer().bindFramebuffer(true);
    RenderUtil.bindTexture(framebufferList.get(0).framebufferTexture);
    RenderUtil.setAlphaLimit(0);
    GlStateManager.enableBlend();
    GlStateManager.blendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
    ShaderUtils.drawQuads();
    RenderUtil.bindTexture(0);
    GlStateManager.disableBlend();
  }

  private static void renderFBO(
      Framebuffer fb, int framebufferTexture, ShaderUtils shader, float offset, int iteration) {
    fb.framebufferClear();
    fb.bindFramebuffer(false);
    shader.init();
    GL11.glBindTexture(GL_TEXTURE_2D, framebufferTexture);
    shader.setUniformf("offset", offset, offset);
    shader.setUniformi("inTexture", 0);
    shader.setUniformi("check", 0);
    shader.setUniformf("halfpixel", 1.0f / fb.framebufferWidth, 1.0f / fb.framebufferHeight);
    shader.setUniformf("iResolution", fb.framebufferWidth, fb.framebufferHeight);
    ShaderUtils.drawQuads();
    shader.unload();
  }
}
