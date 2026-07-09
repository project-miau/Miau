package miau.module.modules.render;

import java.awt.Color;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import miau.Miau;
import miau.event.EventTarget;
import miau.event.impl.ShaderEvent;
import miau.module.Module;
import miau.property.properties.BooleanProperty;
import miau.property.properties.DragProperty;
import miau.property.properties.IntProperty;
import miau.util.animation.Animation;
import miau.util.animation.Easing;
import miau.util.shader.RoundedUtils;
import miau.util.vector.Vector2d;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.scoreboard.Score;
import net.minecraft.scoreboard.ScoreObjective;
import net.minecraft.scoreboard.ScorePlayerTeam;

public class Scoreboard extends Module {
  private static final Minecraft mc = Minecraft.getMinecraft();

  /** Drag property — marked as structure=true so DragManager does NOT draw a border over it. */
  public final DragProperty drag = new DragProperty("Position", new Vector2d(0, 0), false, true);

  /** The actual computed card position (updated every frame). */
  public float defaultX = 0;

  public float defaultY = 0;

  public final IntProperty yOffset = new IntProperty("Y Offset", 0, -250, 250);
  public final BooleanProperty customFont = new BooleanProperty("Custom Font", false);
  public final BooleanProperty textShadow = new BooleanProperty("Text Shadow", true);
  public final BooleanProperty redNumbers = new BooleanProperty("Red Numbers", false);

  private final Animation autofitAnimation = new Animation(Easing.EASE_OUT_EXPO, 300);

  public Scoreboard() {
    super("Scoreboard", true, false);
  }

  /**
   * Recalculates card bounds each frame. Autofit shifts the scoreboard down when other right‑side
   * modules occupy the same space.
   */
  public void updateBounds(ScaledResolution scaledRes) {
    net.minecraft.scoreboard.Scoreboard sb = null;
    ScoreObjective objective = null;
    if (mc.theWorld != null) {
      sb = mc.theWorld.getScoreboard();
      if (sb != null) {
        objective = sb.getObjectiveInDisplaySlot(1);
      }
    }

    int size;
    int maxWidth;
    if (objective != null && sb != null) {
      Collection<Score> collection = sb.getSortedScores(objective);
      List<Score> list = new ArrayList<>();
      for (Score score : collection) {
        if (score.getPlayerName() != null && !score.getPlayerName().startsWith("#")) {
          list.add(score);
        }
      }
      if (list.size() > 15) {
        list = list.subList(list.size() - 15, list.size());
      }
      size = list.size();
      maxWidth = mc.fontRendererObj.getStringWidth(objective.getDisplayName());
      for (Score score : list) {
        ScorePlayerTeam team = sb.getPlayersTeam(score.getPlayerName());
        String name =
            ScorePlayerTeam.formatPlayerName(team, score.getPlayerName())
                + ": "
                + score.getScorePoints();
        maxWidth = Math.max(maxWidth, mc.fontRendererObj.getStringWidth(name));
      }
    } else {
      size = 5;
      maxWidth = 80;
    }

    // Card dimensions
    int padding = 8;
    int width = maxWidth + padding + 4;
    int height = size * mc.fontRendererObj.FONT_HEIGHT + 14;

    float baseX = scaledRes.getScaledWidth() - width - 2;
    float baseY = scaledRes.getScaledHeight() / 2 - height / 3 + yOffset.getValue();

    // ── Autofit: push down if HUD module list is on the right side ────────
    float autofitOffset = 0;
    HUD hud = (HUD) Miau.moduleManager.getModule(HUD.class);
    if (hud != null && hud.isEnabled() && hud.posX.getValue() == 1) {
      float moduleListHeight = hud.getModuleListHeight();
      if (moduleListHeight > 0) {
        float hudStartY;
        if (hud.posY.getValue() == 0) {
          hudStartY = hud.offsetY.getValue();
          if (hud.showWatermark.getValue()) {
            hudStartY += hud.getFont().getFontHeight() + 6.0F;
          }
        } else {
          hudStartY = scaledRes.getScaledHeight() - hud.offsetY.getValue() - moduleListHeight;
        }
        float hudBottom = hudStartY + moduleListHeight;
        if (hudBottom > baseY) {
          autofitOffset = hudBottom - baseY + 4;
        }
      }
    }

    this.defaultX = baseX;
    autofitAnimation.run(baseY + autofitOffset);
    this.defaultY = autofitAnimation.getValue();

    // Always keep position in sync (no initialisation guard — the Mixin reads defaultX/Y directly)
    this.drag.position.x = baseX;
    this.drag.position.y = this.defaultY;
    this.drag.targetPosition.x = baseX;
    this.drag.targetPosition.y = this.defaultY;
    this.drag.scale.x = width;
    this.drag.scale.y = height;
  }

  /**
   * Renders the scoreboard background during PostProcessing shader passes.
   * Pass 0 = bloom background (solid dark)
   * Pass 1 = blur background (semi-transparent dark)
   * Pass 2 = normal render (background + text — handled by mixin)
   */
  @EventTarget
  public void onShaderEvent(ShaderEvent event) {
    ScoreObjective objective = null;
    if (mc.theWorld != null) {
      net.minecraft.scoreboard.Scoreboard sb = mc.theWorld.getScoreboard();
      if (sb != null) {
        objective = sb.getObjectiveInDisplaySlot(1);
      }
    }
    if (objective == null) return;

    PostProcessing pp = (PostProcessing) Miau.moduleManager.getModule(PostProcessing.class);
    if (pp == null || !pp.isActive()) return;

    int pass = event.getPass();
    float cardX = this.defaultX;
    float cardY = this.defaultY;
    float cardWidth = (float) this.drag.scale.x;
    float cardHeight = (float) this.drag.scale.y;
    float radius = 2.0f;

    if (pass == ShaderEvent.BLOOM_PASS) {
      // Bloom pass: draw solid dark background for glow effect
      RoundedUtils.drawRound(
          cardX, cardY, cardWidth, cardHeight, radius, new Color(0xFF090909, true));
    } else if (pass == ShaderEvent.BLUR_PASS) {
      // Blur pass: draw semi-transparent background for blur effect
      RoundedUtils.drawRound(
          cardX, cardY, cardWidth, cardHeight, radius, new Color(0x80, 0x09, 0x09, 0x09));
    }
  }
}
