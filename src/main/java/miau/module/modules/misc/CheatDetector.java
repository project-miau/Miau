package miau.module.modules.misc;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import miau.Miau;
import miau.event.EventTarget;
import miau.event.impl.PacketEvent;
import miau.event.impl.UpdateEvent;
import miau.module.Module;
import miau.module.modules.misc.cheatdetector.CheatDetectorData;
import miau.property.properties.BooleanProperty;
import miau.property.properties.FloatProperty;
import miau.property.properties.ModeProperty;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayer;

/**
 * @author meowtils
 */
public class CheatDetector extends Module {
  private static final Minecraft mc = Minecraft.getMinecraft();

  public final ModeProperty alertMode =
      new ModeProperty("alert-mode", 0, new String[] {"Notification", "Chat"});
  public final FloatProperty alertCoolDown = new FloatProperty("alert-cooldown", 1000f, 0f, 2000f);

  public final BooleanProperty checkAutoBlock = new BooleanProperty("auto-block", true);
  public final BooleanProperty checkKillaura = new BooleanProperty("killaura", true);
  public final BooleanProperty checkAttackRaytrace = new BooleanProperty("attack-raytrace", true);
  public final BooleanProperty checkClickSpeedLimiter =
      new BooleanProperty("click-speed-limiter", true);
  public final BooleanProperty checkClickPatterns = new BooleanProperty("click-patterns", true);
  public final BooleanProperty checkHeuristics = new BooleanProperty("heuristics", true);

  public final BooleanProperty checkNoSlow = new BooleanProperty("no-slow", true);
  public final BooleanProperty checkPhysics = new BooleanProperty("physics", true);
  public final BooleanProperty checkTimer = new BooleanProperty("timer", true);

  public final BooleanProperty checkInventoryClickAnalysis =
      new BooleanProperty("inv-click-analysis", true);
  public final BooleanProperty checkProtocolScanner = new BooleanProperty("protocol-scanner", true);

  public final BooleanProperty checkBreakSpeedLimiter =
      new BooleanProperty("break-speed-limiter", true);
  public final BooleanProperty checkInteractionRaytrace =
      new BooleanProperty("interaction-raytrace", true);

  public final BooleanProperty checkScaffoldSneak = new BooleanProperty("scaffold-sneak", true);
  public final BooleanProperty checkScaffoldAngleSnap =
      new BooleanProperty("scaffold-angle-snap", true);
  public final BooleanProperty checkScaffoldRotationSpeed =
      new BooleanProperty("scaffold-rotation-speed", true);
  public final BooleanProperty checkScaffoldJumpAndPlace =
      new BooleanProperty("scaffold-jump-place", true);

  public final BooleanProperty selfCheck = new BooleanProperty("check-self", false);

  private final Set<EntityPlayer> cheaters = new HashSet<>();
  private final Map<UUID, CheatDetectorData> dataMap = new HashMap<>();

  public CheatDetector() {
    super("CheatDetector", false);
  }

  public boolean isCheckEnabled(String name) {
    if ("AutoBlockCheck".equals(name)) return checkAutoBlock.getValue();
    if ("KillauraCheck".equals(name)) return checkKillaura.getValue();
    if ("AttackRaytraceCheck".equals(name)) return checkAttackRaytrace.getValue();
    if ("ClickSpeedLimiterCheck".equals(name)) return checkClickSpeedLimiter.getValue();
    if ("ClickPatternsCheck".equals(name)) return checkClickPatterns.getValue();
    if ("HeuristicsCheck".equals(name)) return checkHeuristics.getValue();

    if ("NoSlowCheck".equals(name)) return checkNoSlow.getValue();
    if ("PhysicsCheck".equals(name)) return checkPhysics.getValue();
    if ("TimerCheck".equals(name)) return checkTimer.getValue();

    if ("InventoryClickAnalysisCheck".equals(name)) return checkInventoryClickAnalysis.getValue();
    if ("ProtocolScannerCheck".equals(name)) return checkProtocolScanner.getValue();

    if ("BreakSpeedLimiterCheck".equals(name)) return checkBreakSpeedLimiter.getValue();
    if ("InteractionRaytraceCheck".equals(name)) return checkInteractionRaytrace.getValue();

    if ("ScaffoldSneakCheck".equals(name)) return checkScaffoldSneak.getValue();
    if ("ScaffoldAngleSnapCheck".equals(name)) return checkScaffoldAngleSnap.getValue();
    if ("ScaffoldRotationSpeedCheck".equals(name)) return checkScaffoldRotationSpeed.getValue();
    if ("ScaffoldJumpAndPlaceCheck".equals(name)) return checkScaffoldJumpAndPlace.getValue();

    if ("AutoBlock".equals(name)) return checkAutoBlock.getValue();
    if ("No slow".equals(name)) return checkNoSlow.getValue();
    if ("Legit scaffold".equals(name)) return checkScaffoldSneak.getValue();
    if ("Killaura".equals(name)) return checkKillaura.getValue();
    return false;
  }

  @EventTarget
  public void onUpdate(UpdateEvent e) {
    if (!this.isEnabled()) return;
    if (mc.theWorld == null) return;

    for (EntityPlayer player : mc.theWorld.playerEntities) {
      if (player.getDistanceToEntity(mc.thePlayer) > 16 * mc.gameSettings.renderDistanceChunks)
        continue;

      if ((selfCheck.getValue() || player != mc.thePlayer)
          && !player.isDead
          && (Miau.friendManager == null || !Miau.friendManager.isFriend(player.getName()))) {
        if (AntiBot.isBot(player)) continue;

        CheatDetectorData data =
            dataMap.computeIfAbsent(player.getUniqueID(), k -> new CheatDetectorData());
        data.onUpdate(player);
      }
    }
  }

  @EventTarget
  public void onPacket(PacketEvent e) {
    if (!this.isEnabled()) return;
    if (mc.theWorld == null) return;

    for (EntityPlayer player : mc.theWorld.playerEntities) {
      if (player.getDistanceToEntity(mc.thePlayer) > 16 * mc.gameSettings.renderDistanceChunks)
        continue;

      if ((selfCheck.getValue() || player != mc.thePlayer)
          && !player.isDead
          && (Miau.friendManager == null || !Miau.friendManager.isFriend(player.getName()))) {
        if (AntiBot.isBot(player)) continue;

        CheatDetectorData data =
            dataMap.computeIfAbsent(player.getUniqueID(), k -> new CheatDetectorData());
        data.onPacket(e, player);
      }
    }
  }

  @Override
  public void onDisabled() {
    cheaters.clear();
    dataMap.clear();
  }

  public void mark(EntityPlayer ent) {
    cheaters.add(ent);
  }

  public boolean isCheater(EntityPlayer ent) {
    for (EntityPlayer player : cheaters) {
      if (!ent.getName().equals(player.getName())) continue;
      return true;
    }
    return false;
  }

  public void cleanup() {
    Set<UUID> onlineUUIDs =
        mc.theWorld.playerEntities.stream()
            .map(EntityPlayer::getUniqueID)
            .collect(Collectors.toSet());

    dataMap.keySet().removeIf(uuid -> !onlineUUIDs.contains(uuid));
  }
}
