package myau.util.font.impl.rise;

import java.awt.Font;
import java.awt.FontFormatException;
import java.io.File;
import java.io.IOException;
import myau.util.animation.*;
import myau.util.client.*;
import myau.util.math.*;
import myau.util.misc.*;
import myau.util.network.*;
import myau.util.player.*;
import myau.util.render.*;
import myau.util.time.*;
import myau.util.world.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.IResourceManager;
import net.minecraft.util.ResourceLocation;

public class FontUtil {

  private static final IResourceManager RESOURCE_MANAGER =
      Minecraft.getMinecraft().getResourceManager();

  public static Font getResource(final String resource, final int size) {
    try {
      return Font.createFont(
              Font.TRUETYPE_FONT,
              RESOURCE_MANAGER.getResource(new ResourceLocation(resource)).getInputStream())
          .deriveFont((float) size);
    } catch (final FontFormatException | IOException ignored) {
      return null;
    }
  }

  public static Font getDiskResource(final String resource, final int size) {
    try {
      return Font.createFont(Font.TRUETYPE_FONT, new File(resource)).deriveFont((float) size);
    } catch (final FontFormatException | IOException ignored) {
      return null;
    }
  }
}
