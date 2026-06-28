package myau.util.render;

import java.awt.Color;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ScaledResolution;
import org.lwjgl.opengl.GL11;

public class OpalRender {

  public static void rect(float x, float y, float width, float height, int color) {
    RenderUtil.drawRect(x, y, x + width, y + height, color);
  }

  /** Draw a rounded rectangle with uniform radius. */
  public static void roundedRect(
      float x, float y, float width, float height, float radius, int color) {
    roundedRectVarying(x, y, width, height, radius, radius, radius, radius, color);
  }

  /**
   * Draw a gradient rounded rect using the shader-based gradient rounded utility. Opal's NVG
   * roundedRectGradient with angle=90 goes color1(top) → color2(bottom).
   */
  public static void roundedRectGradient(
      float x,
      float y,
      float width,
      float height,
      float radius,
      int color1,
      int color2,
      float angleDegrees) {
    // Shader-based gradient round
    if (angleDegrees >= 45 && angleDegrees < 135) {
      // roughly vertical gradient: color1 on top, color2 on bottom
      myau.util.shader.RoundedUtils.drawGradientVertical(
          x, y, width, height, radius, new Color(color1, true), new Color(color2, true));
    } else {
      // horizontal gradient: color1 on left, color2 on right
      myau.util.shader.RoundedUtils.drawGradientHorizontal(
          x, y, width, height, radius, new Color(color1, true), new Color(color2, true));
    }
  }

  /**
   * Draw non-uniform rounded rect (varying corner radii). tl=topLeft, tr=topRight, br=bottomRight,
   * bl=bottomLeft
   */
  public static void roundedRectVarying(
      float x,
      float y,
      float width,
      float height,
      float tl,
      float tr,
      float br,
      float bl,
      int color) {
    myau.util.shader.RoundedUtils.drawRoundedRectRise(
        x,
        y,
        width,
        height,
        Math.max(tl, Math.max(tr, Math.max(br, bl))),
        color,
        tl > 0,
        tr > 0,
        br > 0,
        bl > 0);
  }

  public static void roundedRectVaryingGradient(
      float x,
      float y,
      float width,
      float height,
      float tl,
      float tr,
      float br,
      float bl,
      int color1,
      int color2,
      int type) {
    myau.util.shader.RoundedUtils.drawGradientRound(
        x,
        y,
        width,
        height,
        Math.max(tl, Math.max(tr, Math.max(br, bl))),
        color1,
        color2,
        color2,
        color1);
  }

  /**
   * Draw a filled rectangle with gradient between two colors. type: 0 = horizontal, 1 = vertical
   */
  public static void rectGradient(
      float x, float y, float width, float height, int color1, int color2, int type) {
    float r1 = (color1 >> 16 & 0xFF) / 255.0F;
    float g1 = (color1 >> 8 & 0xFF) / 255.0F;
    float b1 = (color1 & 0xFF) / 255.0F;
    float a1 = (color1 >> 24 & 0xFF) / 255.0F;

    float r2 = (color2 >> 16 & 0xFF) / 255.0F;
    float g2 = (color2 >> 8 & 0xFF) / 255.0F;
    float b2 = (color2 & 0xFF) / 255.0F;
    float a2 = (color2 >> 24 & 0xFF) / 255.0F;

    GL11.glDisable(GL11.GL_TEXTURE_2D);
    GL11.glEnable(GL11.GL_BLEND);
    GL11.glDisable(GL11.GL_ALPHA_TEST);
    GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
    GL11.glShadeModel(GL11.GL_SMOOTH);

    GL11.glBegin(GL11.GL_QUADS);
    if (type == 0) { // Horizontal
      GL11.glColor4f(r1, g1, b1, a1);
      GL11.glVertex2f(x, y);
      GL11.glVertex2f(x, y + height);
      GL11.glColor4f(r2, g2, b2, a2);
      GL11.glVertex2f(x + width, y + height);
      GL11.glVertex2f(x + width, y);
    } else { // Vertical
      GL11.glColor4f(r1, g1, b1, a1);
      GL11.glVertex2f(x, y);
      GL11.glVertex2f(x + width, y);
      GL11.glColor4f(r2, g2, b2, a2);
      GL11.glVertex2f(x + width, y + height);
      GL11.glVertex2f(x, y + height);
    }
    GL11.glEnd();

    GL11.glShadeModel(GL11.GL_FLAT);
    GL11.glDisable(GL11.GL_BLEND);
    GL11.glEnable(GL11.GL_ALPHA_TEST);
    GL11.glEnable(GL11.GL_TEXTURE_2D);
  }

  /** Perform a GL rotation around a center point and execute the runnable. */
  public static void rotate(float degrees, float centerX, float centerY, Runnable content) {
    GL11.glPushMatrix();
    GL11.glTranslatef(centerX, centerY, 0);
    GL11.glRotatef(degrees, 0, 0, 1);
    GL11.glTranslatef(-centerX, -centerY, 0);
    try {
      content.run();
    } finally {
      GL11.glPopMatrix();
    }
  }

  // ── Scissor (nested-safe) ──────────────────────────────────────────────────

  private static final java.nio.IntBuffer SCISSOR_BOX = org.lwjgl.BufferUtils.createIntBuffer(16);

  public static void scissor(float x, float y, float width, float height, Runnable runnable) {
    boolean wasEnabled = GL11.glIsEnabled(GL11.GL_SCISSOR_TEST);
    int[] currentScissor = new int[4];

    if (wasEnabled) {
      SCISSOR_BOX.clear();
      GL11.glGetInteger(GL11.GL_SCISSOR_BOX, SCISSOR_BOX);
      currentScissor[0] = SCISSOR_BOX.get(0);
      currentScissor[1] = SCISSOR_BOX.get(1);
      currentScissor[2] = SCISSOR_BOX.get(2);
      currentScissor[3] = SCISSOR_BOX.get(3);
    }

    ScaledResolution sr = new ScaledResolution(Minecraft.getMinecraft());
    int scale = sr.getScaleFactor();

    int finalX = (int) (x * scale);
    int finalY = (int) (Minecraft.getMinecraft().displayHeight - ((y + height) * scale));
    int finalWidth = (int) (width * scale);
    int finalHeight = (int) (height * scale);

    if (wasEnabled) {
      // Intersect with current scissor
      int cx = Math.max(finalX, currentScissor[0]);
      int cy = Math.max(finalY, currentScissor[1]);
      int cWidth = Math.min(finalX + finalWidth, currentScissor[0] + currentScissor[2]) - cx;
      int cHeight = Math.min(finalY + finalHeight, currentScissor[1] + currentScissor[3]) - cy;
      GL11.glScissor(cx, cy, Math.max(0, cWidth), Math.max(0, cHeight));
    } else {
      GL11.glEnable(GL11.GL_SCISSOR_TEST);
      GL11.glScissor(finalX, finalY, Math.max(0, finalWidth), Math.max(0, finalHeight));
    }

    try {
      runnable.run();
    } finally {
      if (wasEnabled) {
        GL11.glScissor(currentScissor[0], currentScissor[1], currentScissor[2], currentScissor[3]);
      } else {
        GL11.glDisable(GL11.GL_SCISSOR_TEST);
      }
    }
  }

  // ── Color utilities (Opal-style) ───────────────────────────────────────────

  public static int applyOpacity(int color, float opacity) {
    Color c = new Color(color, true);
    return new Color(c.getRed(), c.getGreen(), c.getBlue(), (int) (c.getAlpha() * opacity))
        .getRGB();
  }

  public static int darker(int color, float factor) {
    float f = 1 - factor;
    int r = (int) ((color >> 16 & 0xFF) * f);
    int g = (int) ((color >> 8 & 0xFF) * f);
    int b = (int) ((color & 0xFF) * f);
    int a = (color >> 24 & 0xFF);
    return ((r & 0xFF) << 16) | ((g & 0xFF) << 8) | (b & 0xFF) | ((a & 0xFF) << 24);
  }

  public static int interpolateColors(int color1, int color2, float amount) {
    amount = Math.min(1, Math.max(0, amount));

    int r1 = (color1 >> 16) & 0xFF;
    int g1 = (color1 >> 8) & 0xFF;
    int b1 = color1 & 0xFF;
    int a1 = (color1 >> 24) & 0xFF;

    int r2 = (color2 >> 16) & 0xFF;
    int g2 = (color2 >> 8) & 0xFF;
    int b2 = color2 & 0xFF;
    int a2 = (color2 >> 24) & 0xFF;

    int r = (int) (r1 + (r2 - r1) * amount);
    int g = (int) (g1 + (g2 - g1) * amount);
    int b = (int) (b1 + (b2 - b1) * amount);
    int a = (int) (a1 + (a2 - a1) * amount);

    return (a << 24) | (r << 16) | (g << 8) | b;
  }

  /** Rainbow rect: draws vertical stripes across the given area */
  public static void rainbowRect(float x, float y, float width, float height) {
    for (float i = y; i < y + height; i += 0.5f) {
      float hue = (i - y) / height;
      int rgbColor = Color.HSBtoRGB(hue, 1, 1) | 0xFF000000;
      float segmentH = Math.min(0.5f, y + height - i);
      rect(x, i, width, segmentH, rgbColor);
    }
  }

  /** Draw outline of a rounded rect. */
  public static void roundedRectOutline(
      float x, float y, float width, float height, float radius, float thickness, int color) {
    myau.util.shader.RoundedUtils.drawRoundOutline(
        x, y, width, height, radius, thickness, new Color(0, 0, 0, 0), new Color(color, true));
  }
}
