package myau.module.modules.misc;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import myau.Myau;
import myau.clientanticheat.CheckDataManager;
import myau.clientanticheat.ClickSpeedCheck;
import myau.clientanticheat.ClientAntiCheatContext;
import myau.clientanticheat.PlayerCheckData;
import myau.clientanticheat.combat.autoblock.AutoBlockCheck;
import myau.clientanticheat.combat.killaura.KillAuraAngleSnap;
import myau.clientanticheat.combat.killaura.KillAuraHeuristicsCheck;
import myau.clientanticheat.combat.killaura.KillAuraLatencyCheck;
import myau.clientanticheat.combat.killaura.KillAuraRotationSpeed;
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
import myau.util.client.ChatUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.world.World;

public class HackerDetector extends Module implements ClientAntiCheatContext {
  private static final Minecraft mc = Minecraft.getMinecraft();
  private static final int FLAG_WINDOW_SECONDS = 5;
  private static final int ALERT_COOLDOWN_SECONDS = 5;

  public final BooleanProperty scaffold = new BooleanProperty("scaffold", true);
  public final BooleanProperty killAura = new BooleanProperty("killaura", true);
  public final BooleanProperty autoBlock = new BooleanProperty("autoblock", true);
  public final BooleanProperty noSlow = new BooleanProperty("noslow", true);
  public final BooleanProperty blink = new BooleanProperty("blink", true);
  public final BooleanProperty reach = new BooleanProperty("reach", true);
  public final BooleanProperty velocity = new BooleanProperty("velocity", true);
  public final BooleanProperty sprint = new BooleanProperty("sprint", true);
  public final BooleanProperty autoClicker = new BooleanProperty("autoclicker", true);
  public final BooleanProperty addTarget = new BooleanProperty("add-target", true);
  public final BooleanProperty sound = new BooleanProperty("sound", true);

  private final ScaffoldPlacementCheck scaffoldPlacementCheck = new ScaffoldPlacementCheck();
  private final ScaffoldSneakCheck scaffoldSneakCheck = new ScaffoldSneakCheck();
  private final ScaffoldRotationCheck scaffoldRotationCheck = new ScaffoldRotationCheck();

  private final KillAuraAngleSnap killAuraAngleSnap = new KillAuraAngleSnap();
  private final KillAuraRotationSpeed killAuraRotationSpeed = new KillAuraRotationSpeed();
  private final KillAuraHeuristicsCheck killAuraHeuristicsCheck = new KillAuraHeuristicsCheck();
  private final KillAuraLatencyCheck killAuraLatencyCheck = new KillAuraLatencyCheck();

  private final HitboxRaytraceCheck hitboxRaytraceCheck = new HitboxRaytraceCheck();
  private final AutoBlockCheck autoBlockCheck = new AutoBlockCheck();

  private final NoSlowCheck noSlowCheck = new NoSlowCheck();
  private final VelocityCheck velocityCheck = new VelocityCheck();
  private final BlinkCheck blinkCheck = new BlinkCheck();
  private final MicroBlinkCheck microBlinkCheck = new MicroBlinkCheck();
  private final FakeLagCheck fakeLagCheck = new FakeLagCheck();
  private final OmniSprintCheck omniSprintCheck = new OmniSprintCheck();
  private final ActionSprintCheck actionSprintCheck = new ActionSprintCheck();

  private final ClickSpeedCheck clickSpeedCheck = new ClickSpeedCheck();

  private final CheckDataManager checkDataManager = new CheckDataManager();
  private final Map<String, int[]> flagMap = new HashMap<>();
  private final Map<String, Integer> alertCooldowns = new HashMap<>();
  private final Set<String> whitelist = new HashSet<>();

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

    World world = mc.theWorld;
    this.checkDataManager.update(world);
    long currentTick = world.getTotalWorldTime();
    for (EntityPlayer player : new ArrayList<>(world.playerEntities)) {
      if (player == mc.thePlayer || player.isDead || player.getName() == null) {
        continue;
      }
      PlayerCheckData data = this.checkDataManager.get(player);
      if (this.scaffold.getValue()) {
        this.scaffoldPlacementCheck.check(player, world, data, this);
        this.scaffoldSneakCheck.check(player, world, data, this);
        this.scaffoldRotationCheck.check(player, world, data, this);
      }
      if (this.killAura.getValue()) {
        this.killAuraAngleSnap.check(player, data, this);
        this.killAuraRotationSpeed.check(player, world, data, currentTick, this);
        this.killAuraHeuristicsCheck.check(player, data, this);
        this.killAuraLatencyCheck.check(player, data, this);
      }
      if (this.autoBlock.getValue()) {
        this.autoBlockCheck.check(player, data, currentTick, this);
      }
      if (this.noSlow.getValue()) {
        this.noSlowCheck.check(player, data, currentTick, this);
      }
      if (this.blink.getValue()) {
        this.blinkCheck.check(player, data, this);
        this.microBlinkCheck.check(player, data, this);
        this.fakeLagCheck.check(player, data, this);
      }
      if (this.reach.getValue()) {
        this.hitboxRaytraceCheck.check(player, world, data, this);
      }
      if (this.velocity.getValue()) {
        this.velocityCheck.check(player, data, this);
      }
      if (this.sprint.getValue()) {
        this.omniSprintCheck.check(player, data, this);
        this.actionSprintCheck.check(player, data, this);
      }
      if (this.autoClicker.getValue()) {
        this.clickSpeedCheck.check(player, data, currentTick, this);
      }
    }
    this.pruneFlags();
  }

  @Override
  public void receiveSignal(String playerName, String cheatName) {
    if (playerName == null || playerName.isEmpty() || cheatName == null) return;
    if (mc.thePlayer == null || mc.theWorld == null) return;
    if (playerName.equalsIgnoreCase(mc.thePlayer.getName())) return;
    if (this.isWhitelisted(playerName)) return;

    int currentTime = (int) (mc.theWorld.getTotalWorldTime() / 20);
    String flagKey = this.getFlagKey(playerName, cheatName);
    int[] flagData = this.flagMap.getOrDefault(flagKey, new int[] {0, currentTime});
    if (currentTime - flagData[1] > FLAG_WINDOW_SECONDS) {
      flagData[0] = 0;
    }
    flagData[0] += 1;
    flagData[1] = currentTime;
    this.flagMap.put(flagKey, flagData);

    int maxFlagCount = this.maxFlagsFor(cheatName);
    int lastAlert = this.alertCooldowns.getOrDefault(flagKey, -ALERT_COOLDOWN_SECONDS);
    if (flagData[0] >= maxFlagCount && currentTime - lastAlert >= ALERT_COOLDOWN_SECONDS) {
      ChatUtil.display(
          "%s%s%s failed %s%s",
          EnumChatFormatting.RED,
          playerName,
          EnumChatFormatting.GRAY,
          EnumChatFormatting.RED,
          cheatName);
      if (this.sound.getValue()) {
        mc.thePlayer.playSound("random.orb", 0.3F, 1.0F);
      }
      if (this.addTarget.getValue() && Myau.targetManager != null) {
        Myau.targetManager.add(playerName);
      }
      this.alertCooldowns.put(flagKey, currentTime);
      this.flagMap.remove(flagKey);
    }
  }

  @Override
  public PlayerCheckData getPlayerData(EntityPlayer player) {
    return this.checkDataManager.get(player);
  }

  private void pruneFlags() {
    int currentTime = (int) (mc.theWorld.getTotalWorldTime() / 20);
    Map<String, int[]> nextFlagMap = new HashMap<>();
    for (Map.Entry<String, int[]> entry : this.flagMap.entrySet()) {
      int[] flagData = entry.getValue();
      if (currentTime - flagData[1] <= FLAG_WINDOW_SECONDS) {
        nextFlagMap.put(entry.getKey(), flagData);
      }
    }
    this.flagMap.clear();
    this.flagMap.putAll(nextFlagMap);
    this.alertCooldowns
        .entrySet()
        .removeIf(entry -> currentTime - entry.getValue() > ALERT_COOLDOWN_SECONDS);
  }

  private boolean isWhitelisted(String playerName) {
    for (String name : this.whitelist) {
      if (name.equalsIgnoreCase(playerName)) {
        return true;
      }
    }
    return false;
  }

  private int maxFlagsFor(String cheatName) {
    if (cheatName.equals("AutoBlock")) return 4;
    if (cheatName.equals("Noslow")) return 3;
    if (cheatName.equals("KillAura")) return 3;
    if (cheatName.equals("Scaffold")) return 3;
    if (cheatName.equals("Blink")) return 2;
    if (cheatName.equals("Reach")) return 2;
    if (cheatName.equals("Velocity")) return 2;
    if (cheatName.equals("AutoClicker")) return 2;
    return 2;
  }

  private String getFlagKey(String playerName, String cheatName) {
    return playerName.toLowerCase(Locale.ROOT) + ":" + cheatName;
  }

  @Override
  public void onDisabled() {
    this.scaffoldPlacementCheck.reset();
    this.scaffoldSneakCheck.reset();
    this.scaffoldRotationCheck.reset();
    this.killAuraHeuristicsCheck.reset();
    this.killAuraLatencyCheck.reset();
    this.blinkCheck.reset();
    this.microBlinkCheck.reset();
    this.fakeLagCheck.reset();
    this.clickSpeedCheck.reset();
    this.killAuraAngleSnap.reset();
    this.killAuraRotationSpeed.reset();
    this.killAuraHeuristicsCheck.reset();
    this.hitboxRaytraceCheck.reset();
    this.autoBlockCheck.reset();
    this.noSlowCheck.reset();
    this.velocityCheck.reset();
    this.blinkCheck.reset();
    this.omniSprintCheck.reset();
    this.actionSprintCheck.reset();
    this.clickSpeedCheck.reset();
    this.checkDataManager.reset();
    this.flagMap.clear();
    this.alertCooldowns.clear();
  }
}
