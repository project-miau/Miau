package miau.module.modules.misc;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Writer;
import miau.Miau;
import miau.event.EventTarget;
import miau.event.impl.Render2DEvent;
import miau.module.Module;
import miau.notification.NotificationType;
import miau.property.properties.DragProperty;
import miau.property.properties.ModeProperty;
import miau.property.properties.TextProperty;
import miau.util.animation.Animation;
import miau.util.animation.Direction;
import miau.util.animation.impl.DecelerateAnimation;
import miau.util.font.Font;
import miau.util.font.FontRepository;
import miau.util.render.ColorUtil;
import miau.util.render.RenderUtil;
import miau.util.shader.RoundedUtils;
import miau.util.spotify.LastFmAPI;
import miau.util.vector.Vector2d;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.IImageBuffer;
import net.minecraft.client.renderer.ThreadDownloadImageData;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.opengl.GL11;

public class SpotifyMod extends Module {

  private static final Minecraft mc = Minecraft.getMinecraft();

  private final TextProperty lastFmUser = new TextProperty("Username", "");
  private final TextProperty lastFmApiKey = new TextProperty("LASTFM API Key", "");
  private final ModeProperty backgroundColor =
      new ModeProperty("Background", 0, new String[] {"Miau", "Average", "Spotify Grey", "Sync"});

  private final DragProperty drag = new DragProperty("Spotify", new Vector2d(5, 150));

  public final float height = 50;
  public final float albumCoverSize = height;
  private final float playerWidth = 135;
  private final float width = albumCoverSize + playerWidth;

  private final Animation scrollTrack = new DecelerateAnimation(10000, 1, Direction.BACKWARDS);
  private final Animation scrollArtist = new DecelerateAnimation(10000, 1, Direction.BACKWARDS);

  public LastFmAPI api;
  private boolean downloadedCover;
  private ResourceLocation currentAlbumCover;
  private Color imageColor = Color.WHITE;
  private String lastDownloadedId = "";

  private final Color greyColor = new Color(30, 30, 30);

  public static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
  public static final File LASTFM_CREDS_DIR =
      new File(Minecraft.getMinecraft().mcDataDir, "Miau_LastFm.json");

  public SpotifyMod() {
    super("Spotify", false, true);
    this.drag.render = true;
  }

  @Override
  public void onEnabled() {
    if (mc.thePlayer == null) {
      this.toggle();
      return;
    }

    String user = this.lastFmUser.getValue();
    String key = this.lastFmApiKey.getValue();

    if (api == null) api = new LastFmAPI();

    if (user.equals("") || key.equals("")) {
      loadCredentials();
      user = this.lastFmUser.getValue();
      key = this.lastFmApiKey.getValue();

      if (user.equals("") || key.equals("")) {
        Miau.notificationManager.pop(
            "Error", "Please input Last.fm User and API Key in settings", NotificationType.WARN);
        try {
          if (java.awt.Desktop.isDesktopSupported()) {
            java.awt.Desktop.getDesktop()
                .browse(new java.net.URI("https://idle.e-z.tools/p/b8tsrcndhv"));
          }
        } catch (Exception e) {
          e.printStackTrace();
        }
        this.toggle();
        return;
      }
    }

    saveCredentials();
    api.startConnection(user, key);
    super.onEnabled();
  }

  public static void scissor(double x, double y, double width, double height) {
    ScaledResolution sr = new ScaledResolution(Minecraft.getMinecraft());
    final double scale = sr.getScaleFactor();
    y = sr.getScaledHeight() - y;
    x *= scale;
    y *= scale;
    width *= scale;
    height *= scale;
    GL11.glScissor((int) x, (int) (y - height), (int) width, (int) height);
  }

  @EventTarget
  public void onRender2DEvent(Render2DEvent event) {
    if (api == null || !api.isPlaying) return;

    float x = (float) drag.position.x;
    float y = (float) drag.position.y;

    if (backgroundColor.getModeString().equals("Miau")) {
      drag.scale.x = 170;
      drag.scale.y = 205;
      renderMiauMode(x, y);
    } else {
      drag.scale.x = width;
      drag.scale.y = height;
      renderClassicMode(x, y);
    }
  }

  private void renderClassicMode(float x, float y) {
    Color color2 = ColorUtil.darker(imageColor, 0.65f);

    switch (backgroundColor.getModeString()) {
      case "Average":
        float[] hsb =
            Color.RGBtoHSB(imageColor.getRed(), imageColor.getGreen(), imageColor.getBlue(), null);
        if (hsb[2] < 0.5f) {
          color2 = ColorUtil.brighter(imageColor, 0.65f);
        }
        RoundedUtils.drawRound(
            x + (albumCoverSize - 15), y, playerWidth + 15, height, 6, imageColor);
        break;
      case "Spotify Grey":
        RoundedUtils.drawRound(
            x + (albumCoverSize - 15), y, playerWidth + 15, height, 6, greyColor);
        break;
      case "Sync":
        RoundedUtils.drawRound(
            x + (albumCoverSize - 15), y, playerWidth + 15, height, 6, ColorUtil.rainbow(0));
        break;
    }

    Font font18 = FontRepository.getFont("inter-medium", 18f);
    Font font22 = FontRepository.getFont("inter-bold", 22f);

    GL11.glEnable(GL11.GL_SCISSOR_TEST);
    scissor(x + albumCoverSize, y, playerWidth, height);

    String currentTrackName = api.trackName;
    String currentArtistName = api.artistName;

    if (scrollTrack.getDirection() == Direction.BACKWARDS && scrollTrack.getOutput() == 0) {
      scrollTrack.reset();
    }
    if (scrollArtist.getDirection() == Direction.BACKWARDS && scrollArtist.getOutput() == 0) {
      scrollArtist.reset();
    }
    boolean needsToScrollTrack = font22.width(currentTrackName) > playerWidth;
    boolean needsToScrollArtist = font18.width(currentArtistName) > playerWidth;

    float trackX =
        (float)
            (((x + albumCoverSize) - font22.width(currentTrackName))
                + ((font22.width(currentTrackName) + playerWidth) * scrollTrack.getOutput()));

    font22.draw(currentTrackName, needsToScrollTrack ? trackX : x + albumCoverSize + 4, y + 8, -1);

    float artistX =
        (float)
            (((x + albumCoverSize) - font18.width(currentArtistName))
                + ((font18.width(currentArtistName) + playerWidth) * scrollArtist.getOutput()));

    font18.draw(
        currentArtistName, needsToScrollArtist ? artistX : x + albumCoverSize + 4, y + 26, -1);

    GL11.glDisable(GL11.GL_SCISSOR_TEST);

    downloadAlbumArt();

    if (currentAlbumCover != null && downloadedCover) {
      RenderUtil.resetColor();
      mc.getTextureManager().bindTexture(currentAlbumCover);
      GlStateManager.color(1, 1, 1);
      GL11.glEnable(GL11.GL_BLEND);
      RoundedUtils.drawRoundTextured(x, y, albumCoverSize, albumCoverSize, 6, 1);
    }
  }

  private void renderMiauMode(float x, float y) {
    float width = 170;
    float height = 205;

    Color colorTop = new Color(130, 28, 46, 140);
    Color colorBottom = new Color(8, 3, 5, 216);
    RoundedUtils.drawGradientRound(
        x, y, width, height, 15, colorBottom, colorTop, colorBottom, colorTop);

    float padding = 9;
    float artSize = width - (padding * 2);
    float artX = x + padding;
    float artY = y + padding;

    downloadAlbumArt();

    if (currentAlbumCover != null && downloadedCover) {
      RenderUtil.resetColor();
      mc.getTextureManager().bindTexture(currentAlbumCover);
      GlStateManager.color(1, 1, 1);
      GL11.glEnable(GL11.GL_BLEND);
      RoundedUtils.drawRoundTextured(artX, artY, artSize, artSize, 11, 1);
    } else {
      RoundedUtils.drawRound(artX, artY, artSize, artSize, 11, new Color(255, 128, 149, 255));
    }

    float infoY = artY + artSize + 10;
    Font font19 = FontRepository.getFont("inter-bold", 19f);
    Font font13 = FontRepository.getFont("inter-medium", 13.5f);

    String title = api.trackName != null ? api.trackName : "Unknown";
    String artist = api.artistName != null ? api.artistName : "Unknown";

    font19.draw(title, artX, infoY, Color.WHITE.getRGB());
    font13.draw(artist, artX, infoY + 11, new Color(255, 255, 255, 147).getRGB());
  }

  private void downloadAlbumArt() {
    if (api.currentMbid != null && !api.currentMbid.equals(lastDownloadedId)) {
      downloadedCover = false;
      lastDownloadedId = api.currentMbid;

      if (api.albumUrl != null && !api.albumUrl.isEmpty()) {
        final ThreadDownloadImageData albumCover =
            new ThreadDownloadImageData(
                null,
                api.albumUrl,
                null,
                new IImageBuffer() {
                  @Override
                  public BufferedImage parseUserSkin(BufferedImage image) {
                    imageColor = averageColor(image, image.getWidth(), image.getHeight(), 1);
                    downloadedCover = true;
                    return image;
                  }

                  @Override
                  public void skinAvailable() {}
                });
        mc.getTextureManager()
            .loadTexture(
                currentAlbumCover =
                    new ResourceLocation("lastfmAlbums/" + System.currentTimeMillis()),
                albumCover);
      }
    }
  }

  public void saveCredentials() {
    JsonObject keyObject = new JsonObject();
    keyObject.addProperty("user", this.lastFmUser.getValue());
    keyObject.addProperty("key", this.lastFmApiKey.getValue());
    try {
      Writer writer = new BufferedWriter(new FileWriter(LASTFM_CREDS_DIR));
      GSON.toJson(keyObject, writer);
      writer.flush();
      writer.close();
    } catch (IOException e) {
      e.printStackTrace();
    }
  }

  public void loadCredentials() {
    JsonObject fileContent;
    try {
      fileContent = new JsonParser().parse(new FileReader(LASTFM_CREDS_DIR)).getAsJsonObject();
      if (fileContent.has("user")) {
        this.lastFmUser.setValue(fileContent.get("user").getAsString());
      }
      if (fileContent.has("key")) {
        this.lastFmApiKey.setValue(fileContent.get("key").getAsString());
      }
    } catch (FileNotFoundException e) {
    }
  }

  public static Color averageColor(BufferedImage image, int width, int height, int pixelStep) {
    int[] color = new int[3];
    int count = 0;
    for (int i = 0; i < width; i += pixelStep) {
      for (int j = 0; j < height; j += pixelStep) {
        Color pixel = new Color(image.getRGB(i, j));
        color[0] += pixel.getRed();
        color[1] += pixel.getGreen();
        color[2] += pixel.getBlue();
        count++;
      }
    }
    return new Color(color[0] / count, color[1] / count, color[2] / count);
  }
}
