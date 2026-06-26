package myau.module.modules.misc;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import myau.Myau;
import myau.clientanticheat.AntiCheatAlertStyle;
import myau.clientanticheat.CheckDataManager;
import myau.clientanticheat.ClientAntiCheatContext;
import myau.clientanticheat.PlayerCheckData;
import myau.clientanticheat.PlayerEligibility;
import myau.clientanticheat.combat.killaura.KillAuraLatencyCheck;
import myau.clientanticheat.combat.killaura.KillAuraNoSwingCheck;
import myau.clientanticheat.combat.killaura.KillAuraRotationSpeed;
import myau.clientanticheat.combat.killaura.KillAuraToolSwitchCheck;
import myau.clientanticheat.combat.killaura.KillAuraUnifiedCheck;
import myau.event.EventTarget;
import myau.event.impl.TickEvent;
import myau.event.types.EventType;
import myau.module.Module;
import myau.property.properties.BooleanProperty;
import myau.util.client.ChatUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemBlock;
import net.minecraft.util.EnumChatFormatting;

public class HackerDetector extends Module implements ClientAntiCheatContext {

  private static final Minecraft mc = Minecraft.getMinecraft();

  public final BooleanProperty enableAutoBlock = new BooleanProperty("autoblock", true);
  public final BooleanProperty enableLegitScaffold = new BooleanProperty("legit-scaffold", true);
  public final BooleanProperty enableKillaura = new BooleanProperty("killaura", true);
  public final BooleanProperty addTarget = new BooleanProperty("add-target", true);
  public final BooleanProperty sound = new BooleanProperty("sound", true);
  public final BooleanProperty debugMessages = new BooleanProperty("debug", false);

  private final Map<UUID, Integer> autoBlockTicks = new HashMap<>();

  private final Map<UUID, Long> scaffoldLastCrouchStart = new HashMap<>();
  private final Map<UUID, Long> scaffoldLastCrouchEnd = new HashMap<>();
  private final Map<UUID, Boolean> scaffoldWasSneaking = new HashMap<>();
  private final Map<UUID, Long> scaffoldLastSwingTick = new HashMap<>();
  private final Map<UUID, List<Integer>> scaffoldCrouchDurations = new HashMap<>();
  private final Map<UUID, Long> scaffoldLastFlagTick = new HashMap<>();
  private final Map<UUID, Integer> scaffoldViolationLevels = new HashMap<>();
  private static final long SCAFFOLD_COOLDOWN_TICKS = 60L;

  private final KillAuraUnifiedCheck killauraUnified = new KillAuraUnifiedCheck();
  private final KillAuraNoSwingCheck killauraNoSwing = new KillAuraNoSwingCheck();
  private final KillAuraLatencyCheck killauraLatency = new KillAuraLatencyCheck();
  private final KillAuraToolSwitchCheck killauraToolSwitch = new KillAuraToolSwitchCheck();
  private final KillAuraRotationSpeed killauraRotation = new KillAuraRotationSpeed();

  private final Set<UUID> flaggedPlayers = new HashSet<>();

  private final CheckDataManager checkDataManager = new CheckDataManager();
  private long lastAlertSoundTime;
  private int flagCount; // for AntiCheatAlertStyle
  private static final int MAX_FLAG_COUNT = 10;

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

      long tick = mc.theWorld.getTotalWorldTime();

      // ── Rain's 3 checks ──────────────────────────────────────────
      if (this.enableAutoBlock.getValue()) {
        this.autoBlockCheck(player);
      }
      if (this.enableLegitScaffold.getValue()) {
        this.legitScaffoldCheck(player);
      }
      if (this.enableKillaura.getValue()) {
        // Rain's unified killaura (burst/track/movement/consume)
        this.killauraUnified.check(player, data, tick, this);
        // Extra killaura signals (no overlap with unified)
        this.killauraNoSwing.check(player, data, this);
        this.killauraLatency.check(player, data, this);
        this.killauraToolSwitch.check(player, data, tick, this);
        this.killauraRotation.check(player, data, tick, this);
      }
    }
  }

  // ── AutoBlock ────────────────────────────────────────────────────

  private static final int AUTO_BLOCK_FAIL_TICKS = 10;

  private void autoBlockCheck(EntityPlayer player) {
    UUID uuid = player.getUniqueID();
    if (player.isSwingInProgress && player.isBlocking()) {
      int ticks = this.autoBlockTicks.getOrDefault(uuid, 0) + 1;
      this.autoBlockTicks.put(uuid, ticks);
      if (this.debugMessages.getValue() && ticks > 5) {
        ChatUtil.display(
            EnumChatFormatting.YELLOW
                + "[AntiCheat]: "
                + EnumChatFormatting.WHITE
                + player.getName()
                + " AutoBlock ticks: "
                + ticks);
      }
      if (ticks > AUTO_BLOCK_FAIL_TICKS) {
        AntiCheatAlertStyle.displayFlag(player.getName(), "AutoBlock", "auto-block", ticks, 1, 5);
        this.markFlagged(uuid, player, "AutoBlock");
      }
    } else {
      this.autoBlockTicks.remove(uuid);
    }
  }

  // ── LegitScaffold ────────────────────────────────────────────────

  private void legitScaffoldCheck(EntityPlayer player) {
    UUID uuid = player.getUniqueID();
    long tick = player.ticksExisted;

    boolean currSneak = player.isSneaking();
    boolean prevSneak = this.scaffoldWasSneaking.getOrDefault(uuid, false);
    if (currSneak && !prevSneak) {
      this.scaffoldLastCrouchStart.put(uuid, tick);
    } else if (!currSneak && prevSneak) {
      this.scaffoldLastCrouchEnd.put(uuid, tick);
      long start = this.scaffoldLastCrouchStart.getOrDefault(uuid, tick - 1L);
      int duration = (int) (tick - start);
      List<Integer> durations =
          this.scaffoldCrouchDurations.computeIfAbsent(uuid, k -> new ArrayList<>());
      durations.add(0, duration);
      if (durations.size() > 5) {
        durations.remove(5);
      }
    }
    this.scaffoldWasSneaking.put(uuid, currSneak);

    if (player.isSwingInProgress && player.swingProgressInt != player.prevSwingProgress) {
      this.scaffoldLastSwingTick.put(uuid, tick);
    }

    if (player.rotationPitch >= 60.0F
        && player.getHeldItem() != null
        && player.getHeldItem().getItem() instanceof ItemBlock
        && player.onGround) {
      long end = this.scaffoldLastCrouchEnd.getOrDefault(uuid, 0L);
      long swing = this.scaffoldLastSwingTick.getOrDefault(uuid, Long.MIN_VALUE);
      int crouchDuration = (int) (end - this.scaffoldLastCrouchStart.getOrDefault(uuid, end - 1L));
      boolean quickCrouch = crouchDuration >= 1 && crouchDuration <= 2;
      boolean swingTiming = swing >= end && swing <= end + 1L;
      List<Integer> durations =
          this.scaffoldCrouchDurations.getOrDefault(uuid, Collections.emptyList());
      boolean consistent =
          durations.size() >= 3
              && durations.get(0) <= 2
              && durations.get(1) <= 2
              && durations.get(2) <= 2;
      if (quickCrouch && swingTiming && consistent) {
        long lastFlag = this.scaffoldLastFlagTick.getOrDefault(uuid, 0L);
        if (tick - lastFlag >= SCAFFOLD_COOLDOWN_TICKS) {
          this.scaffoldLastFlagTick.put(uuid, tick);
          int vl = this.scaffoldViolationLevels.getOrDefault(uuid, 0) + 1;
          this.scaffoldViolationLevels.put(uuid, vl);
          AntiCheatAlertStyle.displayFlag(player.getName(), "LegitScaffold", "scaffold", vl, 1, 5);
          this.markFlagged(uuid, player, "LegitScaffold");
        }
      }
    }
  }

  // ── AlertManager ─────────────────────────────────────────────────

  private void markFlagged(UUID uuid, EntityPlayer player, String cheatName) {
    if (uuid == null || this.flaggedPlayers.contains(uuid)) {
      return;
    }
    this.flaggedPlayers.add(uuid);

    // Sound
    long now = System.currentTimeMillis();
    if (this.sound.getValue() && now - this.lastAlertSoundTime >= 1500L) {
      mc.thePlayer.playSound("random.orb", 0.3F, 1.0F);
      this.lastAlertSoundTime = now;
    }

    // Add target
    if (this.addTarget.getValue() && Myau.targetManager != null) {
      Myau.targetManager.add(player.getName());
    }
  }

  // ── ClientAntiCheatContext ───────────────────────────────────────

  @Override
  public void receiveSignal(String playerName, String cheatName) {
    this.receiveSignal(playerName, cheatName, "behavior anomaly", 0);
  }

  @Override
  public void receiveSignal(String playerName, String cheatName, String detail, int vl) {
    if (playerName == null || playerName.isEmpty() || cheatName == null) return;
    if (mc.theWorld == null) return;
    for (EntityPlayer player : mc.theWorld.playerEntities) {
      if (player.getName() != null && player.getName().equalsIgnoreCase(playerName)) {
        // Fancy alert style — still uses ChatUtil.display() internally
        ++this.flagCount;
        AntiCheatAlertStyle.displayFlag(
            playerName, cheatName, detail, vl, this.flagCount, MAX_FLAG_COUNT);
        // Mark for nametag overlay + sound + target
        AntiCheatAlertStyle.markCheater(playerName, cheatName, vl);
        long now = System.currentTimeMillis();
        if (this.sound.getValue() && now - this.lastAlertSoundTime >= 1500L) {
          mc.thePlayer.playSound("random.orb", 0.3F, 1.0F);
          this.lastAlertSoundTime = now;
        }
        if (this.addTarget.getValue() && Myau.targetManager != null) {
          Myau.targetManager.add(playerName);
        }
        return;
      }
    }
  }

  @Override
  public PlayerCheckData getPlayerData(EntityPlayer player) {
    return this.checkDataManager.get(player);
  }

  // ── Lifecycle ────────────────────────────────────────────────────

  @Override
  public void onDisabled() {
    this.clearAll();
  }

  public void clearAll() {
    this.autoBlockTicks.clear();
    this.scaffoldLastCrouchStart.clear();
    this.scaffoldLastCrouchEnd.clear();
    this.scaffoldWasSneaking.clear();
    this.scaffoldLastSwingTick.clear();
    this.scaffoldCrouchDurations.clear();
    this.scaffoldLastFlagTick.clear();
    this.scaffoldViolationLevels.clear();
    this.flaggedPlayers.clear();
    this.killauraUnified.reset();
    this.killauraNoSwing.reset();
    this.killauraLatency.reset();
    this.killauraToolSwitch.reset();
    this.killauraRotation.reset();
    this.checkDataManager.reset();
    this.lastAlertSoundTime = 0L;
    this.flagCount = 0;
  }
}
