package miau.util.shader;

import static org.lwjgl.opengl.GL20.glUniform1;

import java.nio.FloatBuffer;
import miau.util.math.MathUtil;
import miau.util.render.RenderUtil;
import miau.util.render.StencilUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.shader.Framebuffer;
import org.lwjgl.BufferUtils;

/** Stencil-based Gaussian blur - ported from Tenacity 5.1 */
public class GaussianBlur {

  private static final Minecraft mc = Minecraft.getMinecraft();

  private static final ShaderUtils gaussianBlur =
      new ShaderUtils("miau:shaders/gaussian.frag", "miau:shaders/vertex.vsh");

  private static Framebuffer framebuffer = new Framebuffer(1, 1, false);

  private static void setupUniforms(float dir1, float dir2, float radius) {
    gaussianBlur.setUniformi("textureIn", 0);
    gaussianBlur.setUniformf(
        "texelSize", 1.0F / (float) mc.displayWidth, 1.0F / (float) mc.displayHeight);
    gaussianBlur.setUniformf("direction", dir1, dir2);
    gaussianBlur.setUniformf("radius", radius);

    final FloatBuffer weightBuffer = BufferUtils.createFloatBuffer(256);
    for (int i = 0; i <= radius; i++) {
      weightBuffer.put(MathUtil.calculateGaussianValue(i, radius / 2));
    }

    weightBuffer.rewind();
    glUniform1(gaussianBlur.getUniform("weights"), weightBuffer);
  }

  public static void startBlur() {
    StencilUtil.initStencilToWrite();
  }

  public static void endBlur(float radius, float compression) {
    StencilUtil.readStencilBuffer(1);

    framebuffer = RenderUtil.createFrameBuffer(framebuffer);

    framebuffer.framebufferClear();
    framebuffer.bindFramebuffer(false);
    gaussianBlur.init();
    setupUniforms(compression, 0, radius);

    RenderUtil.bindTexture(mc.getFramebuffer().framebufferTexture);
    ShaderUtils.drawQuads();
    framebuffer.unbindFramebuffer();
    gaussianBlur.unload();

    mc.getFramebuffer().bindFramebuffer(false);
    gaussianBlur.init();
    setupUniforms(0, compression, radius);

    RenderUtil.bindTexture(framebuffer.framebufferTexture);
    ShaderUtils.drawQuads();
    gaussianBlur.unload();

    StencilUtil.uninitStencilBuffer();
    RenderUtil.resetColor();
    GlStateManager.bindTexture(0);
  }
}
