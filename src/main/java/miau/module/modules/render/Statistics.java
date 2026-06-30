package miau.module.modules.render;

import static org.lwjgl.opengl.GL11.*;

import java.awt.Color;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import miau.Miau;
import miau.event.EventTarget;
import miau.event.impl.PacketEvent;
import miau.event.impl.Render2DEvent;
import miau.event.impl.TickEvent;
import miau.event.impl.UpdateEvent;
import miau.event.types.EventType;
import miau.module.Module;
import miau.property.properties.BooleanProperty;
import miau.property.properties.ColorProperty;
import miau.property.properties.DragProperty;
import miau.property.properties.ModeProperty;
import miau.util.font.FontRepository;
import miau.util.render.RenderUtil;
import miau.util.shader.BlurUtils;
import miau.util.shader.RoundedUtils;
import miau.util.vector.Vector2d;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiDisconnected;
import net.minecraft.client.gui.GuiMainMenu;
import net.minecraft.client.gui.GuiMultiplayer;
import net.minecraft.network.play.server.S02PacketChat;
import net.minecraft.util.StringUtils;

public class Statistics extends Module {

  private static final Minecraft mc = Minecraft.getMinecraft();

  public static int gamesPlayed, killCount, deathCount;
  public static long startTime = System.currentTimeMillis(), endTime = -1;
  public static final String[] KILL_TRIGGERS = {"by *", "para *", "fue destrozado a manos de *"};

  private final Map<String, Double> statistics = new LinkedHashMap<>();

  public final BooleanProperty motionGraph = new BooleanProperty("Show Speed Graph", true);
  public final BooleanProperty separateMotionGraph =
      new BooleanProperty("Separate Graph", true, this.motionGraph::getValue);

  public final DragProperty dragging = new DragProperty("SessionStats", new Vector2d(5, 150));
  public final DragProperty motionDragging = new DragProperty("MotionGraph", new Vector2d(5, 200));

  public final ModeProperty colorMode =
      new ModeProperty("Color Mode", 0, new String[] {"HUD", "Custom"});
  public final ColorProperty customColor =
      new ColorProperty(
          "Custom Color", new Color(255, 105, 180).getRGB(), () -> this.colorMode.getValue() == 1);

  private float width, height;
  private final List<Float> speeds = new ArrayList<>();

  public Statistics() {
    super("Statistics", false, true);
  }

  private Color applyOpacity(Color color, float alpha) {
    return new Color(color.getRed(), color.getGreen(), color.getBlue(), (int) (alpha * 255));
  }

  private Color getColor1() {
    if (colorMode.getValue() == 0) {
      HUD hud = (HUD) Miau.moduleManager.modules.get(HUD.class);
      return hud.getColor(System.currentTimeMillis(), 0);
    }
    return new Color(customColor.getValue());
  }

  private Color getColor2() {
    if (colorMode.getValue() == 0) {
      HUD hud = (HUD) Miau.moduleManager.modules.get(HUD.class);
      return hud.getColor(System.currentTimeMillis(), 35);
    }
    return new Color(customColor.getValue());
  }

  private Color getColor3() {
    if (colorMode.getValue() == 0) {
      HUD hud = (HUD) Miau.moduleManager.modules.get(HUD.class);
      return hud.getColor(System.currentTimeMillis(), 180);
    }
    return new Color(customColor.getValue());
  }

  private Color getColor4() {
    if (colorMode.getValue() == 0) {
      HUD hud = (HUD) Miau.moduleManager.modules.get(HUD.class);
      return hud.getColor(System.currentTimeMillis(), 270);
    }
    return new Color(customColor.getValue());
  }

  @EventTarget
  public void onRender2D(Render2DEvent e) {
    if (!this.isEnabled()) return;

    float x = (float) this.dragging.position.x;
    float y = (float) this.dragging.position.y;
    boolean moreHeight = motionGraph.getValue() && !separateMotionGraph.getValue();
    boolean seperated = motionGraph.getValue() && separateMotionGraph.getValue();

    this.motionDragging.scale.x = seperated ? width : 0;
    this.motionDragging.scale.y = seperated ? 75 : 0;

    width = 145;
    miau.util.font.Font font18 = FontRepository.getHudFont(18);
    miau.util.font.Font font22 = FontRepository.getHudFont(22);
    miau.util.font.Font font20 = FontRepository.getHudFont(20);
    miau.util.font.Font font16 = FontRepository.getHudFont(16);

    float orginalHeight = statistics.size() * (font18.getFontHeight() + 6) + 26;
    height = orginalHeight + (moreHeight ? 75 : 0);

    this.dragging.scale.y = height;
    this.dragging.scale.x = width;

    Color c1 = applyOpacity(getColor1(), .85f);
    Color c2 = applyOpacity(getColor2(), .85f);
    Color c3 = applyOpacity(getColor3(), .85f);
    Color c4 = applyOpacity(getColor4(), .85f);

    HUD hud = (HUD) Miau.moduleManager.modules.get(HUD.class);
    boolean shaders = hud != null && hud.shaders.getValue();

    if (shaders) {
      BlurUtils.prepareBloom();
      RoundedUtils.drawGradientRound(x, y, width, height, 6, c2, c1, c4, c3);
      if (seperated) {
        RoundedUtils.drawGradientRound(
            (float) motionDragging.position.x,
            (float) motionDragging.position.y,
            (float) motionDragging.scale.x,
            (float) motionDragging.scale.y,
            6,
            c2,
            c1,
            c4,
            c3);
      }
      BlurUtils.bloomEnd(3, 4);

      BlurUtils.prepareBlur();
      RoundedUtils.drawGradientRound(
          x, y, width, height, 6, Color.BLACK, Color.BLACK, Color.BLACK, Color.BLACK);
      if (seperated) {
        RoundedUtils.drawGradientRound(
            (float) motionDragging.position.x,
            (float) motionDragging.position.y,
            (float) motionDragging.scale.x,
            (float) motionDragging.scale.y,
            6,
            Color.BLACK,
            Color.BLACK,
            Color.BLACK,
            Color.BLACK);
      }
      BlurUtils.blurEnd(2, 3);
    }

    RoundedUtils.drawGradientRound(x, y, width, height, 6, c2, c1, c4, c3);

    font22.drawWithShadow("Statistics", x + 5, y + 2, -1);

    float underlineWidth = font22.getStringWidth("Statistics");
    RoundedUtils.drawRound(
        x + 5, y + 2 + font22.getFontHeight() + 1, underlineWidth - .5f, 1f, .5f, Color.white);

    statistics.put("Games Played", (double) gamesPlayed);
    statistics.put(
        "K/D",
        deathCount == 0
            ? (double) killCount
            : miau.util.math.MathUtil.round((double) killCount / deathCount, 2));
    statistics.put("Kills", (double) killCount);

    int count = 0;
    for (Map.Entry<String, Double> entry : statistics.entrySet()) {
      String key = entry.getKey();
      Double value = entry.getValue();
      int offset = count * (font18.getFontHeight() + 7);
      font18.drawWithShadow(key + ": ", x + 5, y + offset + 21, -1);
      font18.draw(
          key.equals("K/D")
              ? String.valueOf(value.doubleValue())
              : String.valueOf(value.intValue()),
          x + 5 + font18.getStringWidth(key + ": "),
          y + offset + 21,
          -1,
          false);

      count++;
    }

    float radius = 20; // 40 diameter

    float playtimeX = x + width - (font20.getStringWidth("Play Time") + 6);
    font20.drawWithShadow(
        "Play Time", x + width - (font20.getStringWidth("Play Time") + 5), y + 4, -1);
    float playUnderlineWidth = font20.getStringWidth("Play Time");

    RoundedUtils.drawRound(
        x + width - (font20.getStringWidth("Play Time") + 5),
        y + 4 + font22.getFontHeight(),
        playUnderlineWidth - .5f,
        1,
        .5f,
        Color.white);

    int[] playTime = getPlayTime();

    float circleY = y + 4 + font22.getFontHeight() + 2;

    float wh = radius * 2 + 10;
    float centerX = playtimeX - 1.5f + (wh / 2f) - ((radius * 2 + 1) / 2f) + radius;
    float centerY = circleY + (wh / 2f) - ((radius * 2 + 1) / 2f) + radius;

    RenderUtil.draw2DCircleArc(
        centerX, centerY, radius, 0, 360, 4, applyOpacity(Color.BLACK, .5f).getRGB());

    int[] playTimeActual = getPlayTime();
    boolean change = playTime[0] % 2 == 0;

    float percentage = (playTime[1] + (playTime[2] / 60f)) / 60f;

    float startAngle = change ? 0 : (1 - percentage) * 360;
    float endAngle = change ? percentage * 360 : 360;

    RenderUtil.draw2DCircleArc(
        centerX, centerY, radius, startAngle, endAngle, 3, Color.WHITE.getRGB());

    drawAnimatedPlaytime(
        playtimeX,
        circleY + ((radius * 2 + 10) / 2f - font16.getFontHeight() / 2f),
        (radius * 2 + 10),
        playTimeActual,
        font16);

    if (motionGraph.getValue()) {
      if (seperated) {
        RoundedUtils.drawGradientRound(
            (float) motionDragging.position.x,
            (float) motionDragging.position.y,
            (float) motionDragging.scale.x,
            (float) motionDragging.scale.y,
            6,
            c2,
            c1,
            c4,
            c3);
        drawMotionGraph(
            (float) motionDragging.position.x,
            (float) motionDragging.position.y,
            (float) motionDragging.scale.x,
            (float) motionDragging.scale.y,
            font20,
            font18);
      } else {
        drawMotionGraph(x, y + height - 75, width, 75, font20, font18);
      }
    }
  }

  @EventTarget
  public void onUpdate(UpdateEvent event) {
    if (event.getType() == EventType.PRE && this.isEnabled()) {
      if (speeds.size() >= 100) {
        speeds.remove(0);
      }
      if (mc.thePlayer != null) {
        speeds.add(getPlayerSpeed());
      }
    }
  }

  @EventTarget
  public void onPacket(PacketEvent event) {
    if (event.getType() == EventType.RECEIVE && event.getPacket() instanceof S02PacketChat) {
      S02PacketChat packet = (S02PacketChat) event.getPacket();
      if (mc.thePlayer == null) return;
      String message = packet.getChatComponent().getUnformattedText();
      String strippedMessage = StringUtils.stripControlCodes(message);
      String messageStr = packet.getChatComponent().toString();

      if (!strippedMessage.contains(":")
          && Arrays.stream(KILL_TRIGGERS)
              .anyMatch(strippedMessage.replace(mc.thePlayer.getName(), "*")::contains)) {
        killCount++;
      } else if (messageStr.contains("ClickEvent{action=RUN_COMMAND, value='/play ")
          || messageStr.contains("Want to play again?")) {
        gamesPlayed++;
        if (messageStr.contains("You died!")) {
          deathCount++;
        }
      }
    }
  }

  @EventTarget
  public void onTick(TickEvent event) {
    if (endTime == -1
        && ((!mc.isSingleplayer() && mc.getCurrentServerData() == null)
            || mc.currentScreen instanceof GuiMainMenu
            || mc.currentScreen instanceof GuiMultiplayer
            || mc.currentScreen instanceof GuiDisconnected)) {
      endTime = System.currentTimeMillis();
    } else if (endTime != -1 && (mc.isSingleplayer() || mc.getCurrentServerData() != null)) {
      resetStats();
    }
  }

  private void drawMotionGraph(
      float x,
      float y,
      float width,
      float height,
      miau.util.font.Font font20,
      miau.util.font.Font font18) {
    float textX = x + 5;
    font20.drawWithShadow("Speed", textX, y + 3, -1);
    float underlineWidth = font20.getStringWidth("Speed");

    double average =
        speeds.stream().collect(Collectors.averagingDouble(value -> value.doubleValue() * 50));
    average = Math.round(average * 100) / 100.0;

    String text = "Average: " + average + " BPS";

    font18.drawWithShadow(text, x + width - (font18.getStringWidth(text) + 5), y + 3.5f, -1);

    float lineHeight = height - (font20.getFontHeight() + 16);
    float lineWidth = width - 10;
    float lineX = x + 5;
    float lineY = y + height - 5;
    float distance = 8 + font20.getFontHeight();

    RoundedUtils.drawRound(
        lineX - 3,
        y + distance,
        lineWidth + 6,
        height - (distance + 2),
        5,
        applyOpacity(Color.BLACK, .25f).getRGB());

    glPushMatrix();
    glEnable(GL_BLEND);
    glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
    glDisable(GL_TEXTURE_2D);
    glEnable(GL_LINE_SMOOTH);
    glHint(GL_LINE_SMOOTH_HINT, GL_NICEST);
    glLineWidth(1.5f);
    glBegin(GL_LINES);

    int count = 0;
    if (speeds.size() > 3) {
      for (float speed : speeds) {
        if (count >= speeds.size() - 1) continue;
        glColor4f(1f, 1f, 1f, 1f);
        float speedY = speed * lineHeight;
        float nextSpeedY = speeds.get(count + 1) * lineHeight;
        float length = lineWidth / (speeds.size() - 1);

        glVertex2f(lineX + (count * length), lineY - Math.min(speedY, lineHeight));
        glVertex2f(lineX + ((count + 1) * length), lineY - Math.min(nextSpeedY, lineHeight));
        count++;
      }
    }

    glEnd();

    glDisable(GL_LINE_SMOOTH);
    glEnable(GL_TEXTURE_2D);
    glDisable(GL_BLEND);
    glPopMatrix();
  }

  private void drawAnimatedPlaytime(
      float circleX, float y, float circleWidth, int[] playTime, miau.util.font.Font font16) {
    String seconds = ((playTime[2] < 10) ? "0" : "") + playTime[2];
    String minutes = ((playTime[1] < 10) ? "0" : "") + playTime[1];

    StringBuilder sb = new StringBuilder(seconds);
    if ((playTime[1] > 0) || playTime[0] > 0) {
      sb.insert(0, minutes + ":");
    }
    if (playTime[0] > 0) {
      sb.insert(0, playTime[0] + ":");
    }

    String timeStr = sb.toString();
    float textWidth = font16.getStringWidth(timeStr);
    font16.drawWithShadow(timeStr, (circleX - 1.5f) + (circleWidth / 2f) - (textWidth / 2f), y, -1);
  }

  public static int[] getPlayTime() {
    long diff = getTimeDiff();
    long diffSeconds = 0, diffMinutes = 0, diffHours = 0;
    if (diff > 0) {
      diffSeconds = diff / 1000 % 60;
      diffMinutes = diff / (60 * 1000) % 60;
      diffHours = diff / (60 * 60 * 1000) % 24;
    }
    return new int[] {(int) diffHours, (int) diffMinutes, (int) diffSeconds};
  }

  public static long getTimeDiff() {
    return (endTime == -1 ? System.currentTimeMillis() : endTime) - startTime;
  }

  public static void resetStats() {
    startTime = System.currentTimeMillis();
    endTime = -1;
    gamesPlayed = 0;
    killCount = 0;
    deathCount = 0;
  }

  private float getPlayerSpeed() {
    double bps =
        (Math.hypot(
                    mc.thePlayer.posX - mc.thePlayer.prevPosX,
                    mc.thePlayer.posZ - mc.thePlayer.prevPosZ)
                * ((miau.mixin.IAccessorMinecraft) mc).getTimer().timerSpeed)
            * 20;
    return (float) bps / 50;
  }

  @Override
  public void onEnabled() {
    speeds.clear();
    super.onEnabled();
  }
}
