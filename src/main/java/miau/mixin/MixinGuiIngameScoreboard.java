package miau.mixin;

import java.awt.*;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import miau.Miau;
import miau.module.modules.render.HUD;
import miau.module.modules.render.Scoreboard;
import miau.util.shader.RoundedUtils;
import net.minecraft.client.gui.GuiIngame;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.scoreboard.Score;
import net.minecraft.scoreboard.ScoreObjective;
import net.minecraft.scoreboard.ScorePlayerTeam;
import net.minecraft.util.EnumChatFormatting;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GuiIngame.class)
public abstract class MixinGuiIngameScoreboard {

  @Shadow
  protected abstract void renderScoreboard(ScoreObjective objective, ScaledResolution scaledRes);

  private static boolean isRenderingBloom = false;

  @Inject(method = "renderScoreboard", at = @At("HEAD"), cancellable = true)
  private void onRenderScoreboardPre(
      ScoreObjective objective, ScaledResolution scaledRes, CallbackInfo ci) {
    if (Miau.moduleManager == null) return;

    Scoreboard scoreboardMod = (Scoreboard) Miau.moduleManager.getModule(Scoreboard.class);
    if (scoreboardMod == null || !scoreboardMod.isEnabled()) {
      return;
    }

    // Recalculate bounds every frame (this updates defaultX/defaultY)
    scoreboardMod.updateBounds(scaledRes);

    HUD hud = (HUD) Miau.moduleManager.getModule(HUD.class);
    boolean useShaders = hud != null && hud.isEnabled() && hud.shadow.getValue();

    if (!isRenderingBloom && useShaders) {
      // Bloom pass
      isRenderingBloom = true;
      miau.util.shader.BlurUtils.prepareBloom();
      renderOpalScoreboard(objective, scaledRes, scoreboardMod, true);
      miau.util.shader.BlurUtils.bloomEnd(6, 24.0f);

      // Blur pass
      miau.util.shader.BlurUtils.prepareBlur();
      renderOpalScoreboard(objective, scaledRes, scoreboardMod, false);
      miau.util.shader.BlurUtils.blurEnd(5, 25.0f);

      // Final pass
      renderOpalScoreboard(objective, scaledRes, scoreboardMod, false);
      isRenderingBloom = false;
      ci.cancel();
      return;
    }

    renderOpalScoreboard(objective, scaledRes, scoreboardMod, false);
    ci.cancel();
  }

  /**
   * Opal-style scoreboard rendering with rounded-rect frosted-glass card.
   *
   * <p>Position is read from {@code scoreboardMod.defaultX/defaultY} — computed fresh each frame.
   */
  private void renderOpalScoreboard(
      ScoreObjective objective,
      ScaledResolution scaledRes,
      Scoreboard scoreboardMod,
      boolean bloomPass) {
    net.minecraft.scoreboard.Scoreboard scoreboard = objective.getScoreboard();
    Collection<Score> collection = scoreboard.getSortedScores(objective);
    List<Score> list = new ArrayList<>();
    for (Score score : collection) {
      if (score.getPlayerName() != null && !score.getPlayerName().startsWith("#")) {
        list.add(score);
      }
    }
    if (list.size() > 15) {
      list = list.subList(list.size() - 15, list.size());
    }

    float cardX = scoreboardMod.defaultX;
    float cardY = scoreboardMod.defaultY;
    float cardWidth = (float) scoreboardMod.drag.scale.x;
    float cardHeight = (float) scoreboardMod.drag.scale.y;
    float radius = 2.0f;

    boolean shadow = scoreboardMod.textShadow.getValue();

    // --- Draw card background ---
    if (bloomPass) {
      RoundedUtils.drawRound(
          cardX, cardY, cardWidth, cardHeight, radius, new Color(0xFF090909, true));
    } else {
      RoundedUtils.drawRound(
          cardX, cardY, cardWidth, cardHeight, radius, new Color(0x80, 0x09, 0x09, 0x09));
    }

    // --- Title ---
    String title = objective.getDisplayName();
    float titleX = cardX + cardWidth / 2.0F - getStringWidth(title) / 2.0F;
    float titleY = cardY + 2.0F;
    drawString(title, titleX, titleY, -1, shadow);

    // --- Entries ---
    int lineCount = 0;
    for (Score score : list) {
      lineCount++;
      ScorePlayerTeam team = scoreboard.getPlayersTeam(score.getPlayerName());
      String playerName = ScorePlayerTeam.formatPlayerName(team, score.getPlayerName());

      float entryY =
          cardY + mc.fontRendererObj.FONT_HEIGHT + 2 + lineCount * mc.fontRendererObj.FONT_HEIGHT;
      float leftPad = cardX + 4.0F;

      drawString(playerName, leftPad, entryY, -1, shadow);

      String scoreText;
      if (scoreboardMod.redNumbers.getValue()) {
        scoreText = EnumChatFormatting.RED + "" + score.getScorePoints();
      } else {
        scoreText = " " + score.getScorePoints();
      }
      float rightEdge = cardX + cardWidth - 4.0F;
      drawString(scoreText, rightEdge - getStringWidth(scoreText), entryY, -1, shadow);
    }
  }

  private static final net.minecraft.client.Minecraft mc =
      net.minecraft.client.Minecraft.getMinecraft();

  private int getStringWidth(String text) {
    return mc.fontRendererObj.getStringWidth(text);
  }

  private void drawString(String text, double x, double y, int color, boolean shadow) {
    if (shadow) {
      mc.fontRendererObj.drawStringWithShadow(text, (float) x, (float) y, color);
    } else {
      mc.fontRendererObj.drawString(text, (int) x, (int) y, color);
    }
  }
}
