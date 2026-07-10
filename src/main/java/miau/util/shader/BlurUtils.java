package miau.util.shader;

import miau.util.render.RenderUtil;
import net.minecraft.client.shader.Framebuffer;

public class BlurUtils {
  private static Framebuffer stencilFrameBufferBlur = new Framebuffer(1, 1, false);

  public static void prepareBlur() {
    stencilFrameBufferBlur = RenderUtil.createFrameBuffer(stencilFrameBufferBlur);
    stencilFrameBufferBlur.framebufferClear();
    stencilFrameBufferBlur.bindFramebuffer(false);
  }

  public static void blurEnd(int passes, float radius) {
    stencilFrameBufferBlur.unbindFramebuffer();
    KawaseBlur.renderBlur(stencilFrameBufferBlur.framebufferTexture, passes, radius);
  }
}
