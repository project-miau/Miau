package myau.clientanticheat;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import myau.util.client.ChatUtil;

/**
 * Enhanced AntiCheat alert styling with Fake VL system. Features: - Realistic fake VL values (looks
 * like server-side anticheat) - Severity/risk percentage with emoji indicators - Movement fix
 * desync detection alerts - Nametag-overlay integration (tracks marked cheaters for tab list
 * rendering) - Consistent style across all check types
 */
public class AntiCheatAlertStyle {

  // ── Marked cheaters for NametagOverlayRenderer ─────────────────────────
  private static final Map<String, MarkedCheater> markedCheaters = new HashMap<>();

  private static final class MarkedCheater {
    final String playerName;
    final String checkName;
    final int vl;
    final long markedAt;
    final long duration;

    MarkedCheater(String playerName, String checkName, int vl, long duration) {
      this.playerName = playerName;
      this.checkName = checkName;
      this.vl = vl;
      this.markedAt = System.currentTimeMillis();
      this.duration = duration;
    }

    boolean isExpired() {
      return System.currentTimeMillis() - markedAt > duration;
    }
  }

  private static final long MARK_DURATION_MS = 120_000L; // 2 minutes

  /**
   * Display a styled flag alert with fake VL, risk %, severity, and trace ID. Uses ChatUtil.display
   * for formatting.
   */
  public static void displayFlag(
      String playerName, String cheatName, String detail, int vl, int flagCount, int maxFlagCount) {
    if (playerName == null || playerName.isEmpty() || cheatName == null || cheatName.isEmpty()) {
      return;
    }

    String normalizedCheat = normalizeCheatName(cheatName);
    String normalizedDetail = normalizeDetail(cheatName, detail);
    int visualVl = visualVl(playerName, normalizedCheat, vl, flagCount, maxFlagCount);
    int risk = riskPercent(visualVl, flagCount, maxFlagCount);
    String severity = severity(risk);
    String severityColor = severityColor(risk);
    String traceId = traceId(playerName, normalizedCheat);
    String emoji = severityEmoji(risk);

    // Mark as a cheater for nametag overlay
    markCheater(playerName, normalizedCheat, visualVl);

    // Build the alert message in a realistic anticheat style
    StringBuilder sb = new StringBuilder();
    sb.append(severityColor).append(severity).append(" &8| &f").append(playerName);
    sb.append(" &7failed &b").append(normalizedCheat);
    sb.append(" &8(&7").append(normalizedDetail).append("&8)");
    sb.append(" &8| &dVL &f").append(visualVl / 10).append(".").append(visualVl % 10);
    sb.append(" &8| &cRisk &f").append(risk).append("%");
    sb.append(" &8| &7flags &f").append(flagCount).append("&8/&f").append(maxFlagCount);
    sb.append(" &8| &7Trace#&f").append(traceId);
    sb.append(" &8").append(emoji);

    ChatUtil.display(sb.toString());
  }

  /** Display a movement fix desync alert (new style for body/head desync). */
  public static void displayDesyncAlert(String playerName, String component, String detail) {
    if (playerName == null || component == null) return;

    markCheater(playerName, component, 50);

    StringBuilder sb = new StringBuilder();
    sb.append("&4DESYNC &8| &f").append(playerName);
    sb.append(" &7").append(component);
    sb.append(" &8(&7").append(detail).append("&8)");
    sb.append(" &8| &cVL &f5.0 &8| &cRisk &f75%");
    sb.append(" &8| &7Trace#&f").append(traceId(playerName, component));
    sb.append(" &8\u26A0");

    ChatUtil.display(sb.toString());
  }

  /** Display a generic catch-all alert with the same style. */
  public static void displayGeneric(String playerName, String checkName, String detail, int vl) {
    if (playerName == null || playerName.isEmpty()) return;

    markCheater(playerName, checkName, vl);

    StringBuilder sb = new StringBuilder();
    sb.append("&eALERT &8| &f").append(playerName);
    sb.append(" &7flagged &b").append(checkName);
    if (detail != null && !detail.isEmpty()) {
      sb.append(" &8(&7").append(detail).append("&8)");
    }
    sb.append(" &8| &dVL &f").append(vl);
    sb.append(" &8| &7Trace#&f").append(traceId(playerName, checkName));

    ChatUtil.display(sb.toString());
  }

  // ── Normalization helpers ─────────────────────────────────────────────

  public static String normalizeCheatName(String cheatName) {
    int detailStart = cheatName.indexOf(" (");
    return detailStart > 0 ? cheatName.substring(0, detailStart) : cheatName;
  }

  public static String normalizeDetail(String cheatName, String detail) {
    if (detail != null
        && !detail.trim().isEmpty()
        && !"behavior anomaly".equalsIgnoreCase(detail)) {
      return detail.trim();
    }
    int detailStart = cheatName.indexOf(" (");
    if (detailStart > 0 && cheatName.endsWith(")")) {
      return cheatName.substring(detailStart + 2, cheatName.length() - 1);
    }
    return "heuristic anomaly";
  }

  // ── Fake VL system ────────────────────────────────────────────────────

  /** Generates a realistic fake VL value (looks like server-side anticheat). */
  private static int visualVl(
      String playerName, String cheatName, int vl, int flagCount, int maxFlagCount) {
    int seed = Math.abs((playerName + ":" + cheatName).hashCode());
    int base = Math.max(vl * 10, flagCount * 18 + maxFlagCount * 7);
    return Math.min(120, Math.max(10, base + seed % 17));
  }

  private static int riskPercent(int visualVl, int flagCount, int maxFlagCount) {
    int flagRisk = maxFlagCount <= 0 ? 35 : (flagCount * 100 / maxFlagCount);
    return Math.min(99, Math.max(25, (visualVl * 5 + flagRisk * 6) / 11));
  }

  private static String severity(int risk) {
    if (risk >= 90) return "RAGE";
    if (risk >= 75) return "HIGH";
    if (risk >= 55) return "MED";
    return "LOW";
  }

  private static String severityColor(int risk) {
    if (risk >= 90) return "&4";
    if (risk >= 75) return "&c";
    if (risk >= 55) return "&e";
    return "&a";
  }

  private static String severityEmoji(int risk) {
    if (risk >= 90) return "\u2622"; // ☢ radioactive
    if (risk >= 75) return "\u26A0"; // ⚠ warning
    if (risk >= 55) return "\u26A1"; // ⚡ high voltage
    return "\u2139"; // ℹ info
  }

  private static String traceId(String playerName, String cheatName) {
    String hex = Integer.toHexString(Math.abs((playerName + cheatName).hashCode())).toUpperCase();
    return hex.length() <= 4 ? hex : hex.substring(0, 4);
  }

  // ── Nametag integration ───────────────────────────────────────────────

  public static void markCheater(String playerName, String checkName, int vl) {
    String key = playerName.toLowerCase(Locale.ROOT);
    markedCheaters.put(key, new MarkedCheater(playerName, checkName, vl, MARK_DURATION_MS));
    pruneExpired();
  }

  public static boolean hasMarkedCheaters() {
    pruneExpired();
    return !markedCheaters.isEmpty();
  }

  public static Set<String> getMarkedCheaterNames() {
    pruneExpired();
    Set<String> names = new HashSet<>();
    for (MarkedCheater m : markedCheaters.values()) {
      names.add(m.playerName);
    }
    return names;
  }

  public static int getNametagColor() {
    // Red icon with the configured opacity
    return 0xCCFF3B30;
  }

  private static void pruneExpired() {
    markedCheaters.values().removeIf(MarkedCheater::isExpired);
  }

  /** Clear all marked cheaters (on world unload). */
  public static void clearMarkedCheaters() {
    markedCheaters.clear();
  }
}
