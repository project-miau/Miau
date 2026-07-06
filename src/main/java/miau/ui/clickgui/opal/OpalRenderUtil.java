package miau.ui.clickgui.opal;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;
import miau.util.render.RenderUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.GlStateManager;
import org.lwjgl.opengl.GL11;

/**
 * Port of Opal's NVGRenderer to Forge 1.8.9 fixed-function OpenGL. Provides NanoVG-style rendering
 * helpers using GL11 primitives and Miau's shader-based rounded utils.
 */
public final class OpalRenderUtil {

  private static final Minecraft mc = Minecraft.getMinecraft();

  public static float globalAlpha = 1;

  // Scissor stack for nested scissoring
  private static final List<ScissorState> scissorStack = new ArrayList<>();

  private OpalRenderUtil() {}

  // ── Rect ─────────────────────────────────────────────────────────────────

  public static void rect(float x, float y, float width, float height, int color) {
    if ((color >> 24 & 0xFF) == 0) return;
    RenderUtil.drawRect(x, y, x + width, y + height, applyGlobalAlpha(color));
  }

  // ── Rounded Rect (via RoundedUtils) ──────────────────────────────────────

  public static void roundedRect(
      float x, float y, float width, float height, float radius, int color) {
    if ((color >> 24 & 0xFF) == 0) return;
    miau.util.shader.RoundedUtils.drawRound(x, y, width, height, radius, applyGlobalAlpha(color));
  }

  // ── Rounded Rect Varying ─────────────────────────────────────────────────

  public static void roundedRectVarying(
      float x,
      float y,
      float width,
      float height,
      float radiusTL,
      float radiusTR,
      float radiusBR,
      float radiusBL,
      int color) {
    if ((color >> 24 & 0xFF) == 0) return;
    int finalColor = applyGlobalAlpha(color);
    // Use RoundedUtils with full radius as approximation, then overlay corners
    float maxRadius = Math.max(Math.max(radiusTL, radiusTR), Math.max(radiusBR, radiusBL));
    if (maxRadius <= 0) {
      rect(x, y, width, height, finalColor);
      return;
    }
    // For varying radii, we use the shader-based approach from RoundedUtils
    // with the maximum radius, then draw corner fixups
    miau.util.shader.RoundedUtils.drawRoundedRectRise(
        x,
        y,
        width,
        height,
        maxRadius,
        finalColor,
        radiusTL > 0,
        radiusTR > 0,
        radiusBR > 0,
        radiusBL > 0);
  }

  // ── Gradient Rect ────────────────────────────────────────────────────────

  public static void rectGradient(
      float x, float y, float width, float height, int color1, int color2, float angleDegrees) {
    int c1 = applyGlobalAlpha(color1);
    int c2 = applyGlobalAlpha(color2);

    if (angleDegrees == 0 || angleDegrees == 180) {
      // Horizontal gradient
      int leftColor = (angleDegrees == 0) ? c1 : c2;
      int rightColor = (angleDegrees == 0) ? c2 : c1;
      RenderUtil.drawHorizontalGradientRect(x, y, x + width, y + height, leftColor, rightColor);
    } else if (angleDegrees == 90 || angleDegrees == 270) {
      // Vertical gradient - approximate with horizontal + color swap
      // We can use the gradient shader
      float r1 = ((c1 >> 16) & 0xFF) / 255f;
      float g1 = ((c1 >> 8) & 0xFF) / 255f;
      float b1 = (c1 & 0xFF) / 255f;
      float a1 = ((c1 >> 24) & 0xFF) / 255f;
      float r2 = ((c2 >> 16) & 0xFF) / 255f;
      float g2 = ((c2 >> 8) & 0xFF) / 255f;
      float b2 = (c2 & 0xFF) / 255f;
      float a2 = ((c2 >> 24) & 0xFF) / 255f;

      GlStateManager.disableTexture2D();
      GlStateManager.enableBlend();
      GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
      GlStateManager.shadeModel(GL11.GL_SMOOTH);

      GL11.glBegin(GL11.GL_QUADS);
      // Top
      GL11.glColor4f(r1, g1, b1, a1);
      GL11.glVertex2f(x, y);
      GL11.glVertex2f(x + width, y);
      // Bottom
      GL11.glColor4f(r2, g2, b2, a2);
      GL11.glVertex2f(x + width, y + height);
      GL11.glVertex2f(x, y + height);
      GL11.glEnd();

      GlStateManager.shadeModel(GL11.GL_FLAT);
      GlStateManager.disableBlend();
      GlStateManager.enableTexture2D();
      GlStateManager.resetColor();
    } else {
      // Use the rounded gradient shader for angled gradients
      miau.util.shader.RoundedUtils.drawGradientRound(x, y, width, height, 0, c2, c1, c2, c1);
    }
  }

  // ── Rounded Gradient Rect ────────────────────────────────────────────────

  public static void roundedRectGradient(
      float x,
      float y,
      float width,
      float height,
      float radius,
      int color1,
      int color2,
      float angleDegrees) {
    int c1 = applyGlobalAlpha(color1);
    int c2 = applyGlobalAlpha(color2);

    java.awt.Color awtC1 = new Color(c1, true);
    java.awt.Color awtC2 = new Color(c2, true);

    if (angleDegrees == 0 || angleDegrees == 180) {
      miau.util.shader.RoundedUtils.drawGradientHorizontal(
          x,
          y,
          width,
          height,
          radius,
          angleDegrees == 0 ? awtC1 : awtC2,
          angleDegrees == 0 ? awtC2 : awtC1);
    } else if (angleDegrees == 90 || angleDegrees == 270) {
      miau.util.shader.RoundedUtils.drawGradientVertical(
          x,
          y,
          width,
          height,
          radius,
          angleDegrees == 90 ? awtC1 : awtC2,
          angleDegrees == 90 ? awtC2 : awtC1);
    } else {
      miau.util.shader.RoundedUtils.drawGradientCornerLR(x, y, width, height, radius, awtC1, awtC2);
    }
  }

  // ── Rounded Rect Varying Gradient ────────────────────────────────────────

  public static void roundedRectVaryingGradient(
      float x,
      float y,
      float width,
      float height,
      float radiusTL,
      float radiusTR,
      float radiusBR,
      float radiusBL,
      int color1,
      int color2,
      float angleDegrees) {
    // Fallback to regular rounded gradient + corner override
    float maxRadius = Math.max(Math.max(radiusTL, radiusTR), Math.max(radiusBR, radiusBL));
    roundedRectGradient(x, y, width, height, maxRadius, color1, color2, angleDegrees);
  }

  // ── Outline ──────────────────────────────────────────────────────────────

  public static void rectOutline(
      float x, float y, float width, float height, float thickness, int color) {
    int c = applyGlobalAlpha(color);
    // Top
    rect(x, y, width, thickness, c);
    // Right
    rect(x + width - thickness, y + thickness, thickness, height - thickness, c);
    // Bottom
    rect(x, y + height - thickness, width - thickness, thickness, c);
    // Left
    rect(x, y + thickness, thickness, height - thickness, c);
  }

  public static void roundedRectOutline(
      float x, float y, float width, float height, float radius, float thickness, int color) {
    int c = applyGlobalAlpha(color);
    java.awt.Color awtColor = new Color(c, true);
    java.awt.Color bgColor = new Color(0, 0, 0, 0);
    miau.util.shader.RoundedUtils.drawRoundOutline(
        x, y, width, height, radius, thickness, bgColor, awtColor);
  }

  // ── Rainbow / Hue Bar ────────────────────────────────────────────────────

  public static void rainbowRect(float x, float y, float width, float height) {
    GlStateManager.disableTexture2D();
    GlStateManager.enableBlend();
    GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);

    for (float i = y; i < y + height; i += 0.5f) {
      float hue = (i - y) / height;
      int rgbColor = Color.HSBtoRGB(hue, 1, 1);
      float r = ((rgbColor >> 16) & 0xFF) / 255f;
      float g = ((rgbColor >> 8) & 0xFF) / 255f;
      float b = (rgbColor & 0xFF) / 255f;

      GL11.glColor4f(r, g, b, globalAlpha);
      GL11.glBegin(GL11.GL_QUADS);
      GL11.glVertex2f(x, i);
      GL11.glVertex2f(x + width, i);
      GL11.glVertex2f(x + width, Math.min(i + 0.5f, y + height));
      GL11.glVertex2f(x, Math.min(i + 0.5f, y + height));
      GL11.glEnd();
    }

    GlStateManager.disableBlend();
    GlStateManager.enableTexture2D();
    GlStateManager.resetColor();
  }

  // ── Scissor ───────────────────────────────────────────────────────────────

  private static class ScissorState {
    float x, y, width, height;

    ScissorState(float x, float y, float width, float height) {
      this.x = x;
      this.y = y;
      this.width = width;
      this.height = height;
    }
  }

  public static void scissor(
      final float x, final float y, final float width, final float height, final Runnable content) {
    GL11.glEnable(GL11.GL_SCISSOR_TEST);
    ScaledResolution sr = new ScaledResolution(mc);
    float scale = sr.getScaleFactor();

    float scissorX = x * scale;
    float scissorY = (sr.getScaledHeight() - (y + height)) * scale;
    float scissorW = width * scale;
    float scissorH = height * scale;

    GL11.glScissor((int) scissorX, (int) scissorY, (int) scissorW, (int) Math.max(0, scissorH));

    if (content != null) {
      content.run();
    }

    // Restore to previous scissor or disable
    if (!scissorStack.isEmpty()) {
      ScissorState prev = scissorStack.get(scissorStack.size() - 1);
      float psx = prev.x * scale;
      float psy = (sr.getScaledHeight() - (prev.y + prev.height)) * scale;
      GL11.glScissor(
          (int) psx, (int) psy, (int) (prev.width * scale), (int) Math.max(0, prev.height * scale));
    } else {
      GL11.glDisable(GL11.GL_SCISSOR_TEST);
    }
  }

  // ── Transform Helpers ────────────────────────────────────────────────────

  public static void rotate(
      final double degrees,
      final float x,
      final float y,
      final float width,
      final float height,
      final Runnable content) {
    final float translateX = x + width / 2f;
    final float translateY = y + height / 2f;

    GL11.glPushMatrix();
    GL11.glTranslatef(translateX, translateY, 0);
    GL11.glRotatef((float) degrees, 0, 0, 1);
    GL11.glTranslatef(-translateX, -translateY, 0);

    content.run();

    GL11.glPopMatrix();
  }

  public static void scale(
      final float factor,
      final float x,
      final float y,
      final float width,
      final float height,
      final Runnable content) {
    final float translateX = x + width / 2f;
    final float translateY = y + height / 2f;

    GL11.glPushMatrix();
    GL11.glTranslatef(translateX, translateY, 0);
    GL11.glScalef(factor, factor, 1);
    GL11.glTranslatef(-translateX, -translateY, 0);

    content.run();

    GL11.glPopMatrix();
  }

  // ── Helper ───────────────────────────────────────────────────────────────

  private static int applyGlobalAlpha(int color) {
    if (globalAlpha >= 1) return color;
    int alpha = (int) (((color >> 24) & 0xFF) * globalAlpha);
    return (color & 0x00FFFFFF) | (Math.min(255, Math.max(0, alpha)) << 24);
  }
}
