package myau.module.modules.misc;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import myau.Myau;
import myau.clientanticheat.AntiCheatAlertStyle;
import myau.clientanticheat.CheckDataManager;
import myau.clientanticheat.ClientAntiCheatContext;
import myau.clientanticheat.PlayerCheckData;
import myau.clientanticheat.PlayerEligibility;
import myau.clientanticheat.combat.autoblock.AutoBlockCheck;
import myau.clientanticheat.combat.autoclicker.ClickSpeedCheck;
import myau.clientanticheat.combat.killaura.KillAuraHeuristicsCheck;
import myau.clientanticheat.combat.killaura.KillAuraLatencyCheck;
import myau.clientanticheat.combat.killaura.KillAuraNoSwingCheck;
import myau.clientanticheat.combat.killaura.KillAuraRotationSpeed;
import myau.clientanticheat.combat.killaura.KillAuraToolSwitchCheck;
import myau.clientanticheat.combat.killaura.KillAuraUnifiedCheck;
import myau.clientanticheat.combat.reach.HitboxRaytraceCheck;
import myau.clientanticheat.movement.blink.BlinkCheck;
import myau.clientanticheat.movement.blink.FakeLagCheck;
import myau.clientanticheat.movement.blink.MicroBlinkCheck;
import myau.clientanticheat.movement.noslow.NoSlowCheck;
import myau.clientanticheat.movement.sprint.ActionSprintCheck;
import myau.clientanticheat.movement.sprint.OmniSprintCheck;
import myau.clientanticheat.movement.velocity.VelocityCheck;
import myau.clientanticheat.player.scaffold.ScaffoldPlacementCheck;
import myau.clientanticheat.player.scaffold.ScaffoldRotationCheck;
import myau.clientanticheat.player.scaffold.ScaffoldSneakCheck;
import myau.event.EventTarget;
import myau.event.impl.TickEvent;
import myau.event.types.EventType;
import myau.module.Module;
import myau.property.properties.BooleanProperty;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayer;

public class HackerDetector extends Module implements ClientAntiCheatContext {

  private static final Minecraft mc = Minecraft.getMinecraft();

  public final BooleanProperty enableKillaura = new BooleanProperty("killaura", true);
  public final BooleanProperty enableAutoBlock = new BooleanProperty("autoblock", true);
  public final BooleanProperty enableScaffold = new BooleanProperty("scaffold", true);
  public final BooleanProperty enableReach = new BooleanProperty("reach", true);
  public final BooleanProperty enableVelocity = new BooleanProperty("velocity", true);
  public final BooleanProperty enableNoSlow = new BooleanProperty("noslow", true);
  public final BooleanProperty enableBlink = new BooleanProperty("blink", true);
  public final BooleanProperty enableSprint = new BooleanProperty("sprint", true);
  public final BooleanProperty enableAutoClicker = new BooleanProperty("autoclicker", true);
  public final BooleanProperty addTarget = new BooleanProperty("add-target", true);
  public final BooleanProperty sound = new BooleanProperty("sound", true);
  public final BooleanProperty debugMessages = new BooleanProperty("debug", false);

  // ── Check instances ──────────────────────────────────────────────────

  // KillAura (6 checkers — unified + heuristics + 4 extras)
  private final KillAuraUnifiedCheck killauraUnified = new KillAuraUnifiedCheck();
  private final KillAuraHeuristicsCheck killauraHeuristics = new KillAuraHeuristicsCheck();
  private final KillAuraNoSwingCheck killauraNoSwing = new KillAuraNoSwingCheck();
  private final KillAuraLatencyCheck killauraLatency = new KillAuraLatencyCheck();
  private final KillAuraToolSwitchCheck killauraToolSwitch = new KillAuraToolSwitchCheck();
  private final KillAuraRotationSpeed killauraRotation = new KillAuraRotationSpeed();

  // Combat
  private final AutoBlockCheck autoBlockCheck = new AutoBlockCheck();
  private final HitboxRaytraceCheck reachCheck = new HitboxRaytraceCheck();

  // Movement
  private final VelocityCheck velocityCheck = new VelocityCheck();
  private final NoSlowCheck noSlowCheck = new NoSlowCheck();
  private final BlinkCheck blinkCheck = new BlinkCheck();
  private final FakeLagCheck fakeLagCheck = new FakeLagCheck();
  private final MicroBlinkCheck microBlinkCheck = new MicroBlinkCheck();
  private final OmniSprintCheck omniSprintCheck = new OmniSprintCheck();
  private final ActionSprintCheck actionSprintCheck = new ActionSprintCheck();

  // Player/Scaffold
  private final ScaffoldRotationCheck scaffoldRotation = new ScaffoldRotationCheck();
  private final ScaffoldPlacementCheck scaffoldPlacement = new ScaffoldPlacementCheck();
  private final ScaffoldSneakCheck scaffoldSneak = new ScaffoldSneakCheck();

  // AutoClicker
  private final ClickSpeedCheck clickSpeedCheck = new ClickSpeedCheck();

  // ── Shared state ─────────────────────────────────────────────────────

  private final Set<UUID> flaggedPlayers = new HashSet<>();
  private final CheckDataManager checkDataManager = new CheckDataManager();
  private long lastAlertSoundTime;
  private int flagCount;
  private static final int MAX_FLAG_COUNT = 10;

  // Cooldown map per check/player to avoid spam within the same check
  private final Map<String, Long> checkCooldowns = new HashMap<>();
  private static final long CHECK_COOLDOWN_MS = 2500L;

  public HackerDetector() {
    super("HackerDetector", false, false);
  }

  @EventTarget
  public void onTick(TickEvent event) {
    if (!this.isEnabled()
        || event.getType() != EventType.POST
        || mc.thePlayer == null
        || mc.theWorld == null) {
      return;
    }
    if (mc.isSingleplayer()) {
      return;
    }

    this.checkDataManager.update(mc.theWorld);
    long currentTick = mc.theWorld.getTotalWorldTime();

    for (EntityPlayer player : mc.theWorld.playerEntities) {
      if (player == null || player.isDead) continue;
      UUID uuid = player.getUniqueID();
      if (uuid == null) continue;

      if (!PlayerEligibility.shouldCheckPlayer(player) || this.flaggedPlayers.contains(uuid)) {
        continue;
      }

      PlayerCheckData data = this.checkDataManager.get(player);
      if (data == null) continue;

      data.updateRainData(player);

      // ── KillAura (Unified + Heuristics + 4 Extras) ──────────────────
      if (this.enableKillaura.getValue()) {
        this.killauraUnified.check(player, data, currentTick, this);
        this.killauraHeuristics.check(player, data, this);
        this.killauraNoSwing.check(player, data, this);
        this.killauraLatency.check(player, data, this);
        this.killauraToolSwitch.check(player, data, currentTick, this);
        this.killauraRotation.check(player, data, currentTick, this);
      }

      // ── AutoBlock ──────────────────────────────────────────────────
      if (this.enableAutoBlock.getValue()) {
        this.autoBlockCheck.check(player, data, currentTick, this);
      }

      // ── Scaffold ───────────────────────────────────────────────────
      if (this.enableScaffold.getValue()) {
        this.scaffoldRotation.check(player, mc.theWorld, data, this);
        this.scaffoldPlacement.check(player, mc.theWorld, data, this);
        this.scaffoldSneak.check(player, mc.theWorld, data, this);
      }

      // ── Reach ──────────────────────────────────────────────────────
      if (this.enableReach.getValue()) {
        this.reachCheck.check(player, mc.theWorld, data, this);
      }

      // ── Velocity ───────────────────────────────────────────────────
      if (this.enableVelocity.getValue()) {
        this.velocityCheck.check(player, data, this);
      }

      // ── NoSlow ─────────────────────────────────────────────────────
      if (this.enableNoSlow.getValue()) {
        this.noSlowCheck.check(player, data, currentTick, this);
      }

      // ── Blink / FakeLag ────────────────────────────────────────────
      if (this.enableBlink.getValue()) {
        this.blinkCheck.check(player, data, this);
        this.fakeLagCheck.check(player, data, this);
        this.microBlinkCheck.check(player, data, this);
      }

      if (this.enableSprint.getValue()) {
        this.omniSprintCheck.check(player, data, this);
        this.actionSprintCheck.check(player, data, this);
      }

      if (this.enableAutoClicker.getValue()) {
        this.clickSpeedCheck.check(player, data, currentTick, this);
      }
    }
  }

  @Override
  public void receiveSignal(String playerName, String cheatName) {
    this.receiveSignal(playerName, cheatName, "behavior anomaly", 0);
  }

  @Override
  public void receiveSignal(String playerName, String cheatName, String detail, int vl) {
    if (playerName == null || playerName.isEmpty() || cheatName == null) return;
    if (mc.theWorld == null) return;

    String cooldownKey = playerName + "@" + cheatName + "@" + detail;
    long now = System.currentTimeMillis();
    Long lastTime = this.checkCooldowns.get(cooldownKey);
    if (lastTime != null && now - lastTime < CHECK_COOLDOWN_MS) {
      return;
    }
    this.checkCooldowns.put(cooldownKey, now);

    ++this.flagCount;
    AntiCheatAlertStyle.displayFlag(playerName, cheatName, detail, vl, this.flagCount,
        MAX_FLAG_COUNT);
    AntiCheatAlertStyle.markCheater(playerName, cheatName, vl);

    if (this.sound.getValue() && now - this.lastAlertSoundTime >= 1500L) {
      mc.thePlayer.playSound("random.orb", 0.3F, 1.0F);
      this.lastAlertSoundTime = now;
    }

    if (this.addTarget.getValue() && Myau.targetManager != null) {
      Myau.targetManager.add(playerName);
    }
  }

  @Override
  public PlayerCheckData getPlayerData(EntityPlayer player) {
    return this.checkDataManager.get(player);
  }

  @Override
  public void onDisabled() {
    this.clearAll();
  }

  public void clearAll() {
    this.flaggedPlayers.clear();
    this.flagCount = 0;
    this.lastAlertSoundTime = 0;
    this.checkCooldowns.clear();

    this.killauraUnified.reset();
    this.killauraHeuristics.reset();
    this.killauraNoSwing.reset();
    this.killauraLatency.reset();
    this.killauraToolSwitch.reset();
    this.killauraRotation.reset();
    this.autoBlockCheck.reset();
    this.reachCheck.reset();
    this.velocityCheck.reset();
    this.noSlowCheck.reset();
    this.blinkCheck.reset();
    this.fakeLagCheck.reset();
    this.microBlinkCheck.reset();
    this.omniSprintCheck.reset();
    this.actionSprintCheck.reset();
    this.scaffoldRotation.reset();
    this.scaffoldPlacement.reset();
    this.scaffoldSneak.reset();
    this.clickSpeedCheck.reset();
    this.checkDataManager.reset();
  }
}
