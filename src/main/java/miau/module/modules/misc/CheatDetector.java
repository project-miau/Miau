package miau.module.modules.misc;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import miau.Miau;
import miau.event.EventTarget;
import miau.event.impl.UpdateEvent;
import miau.module.Module;
import miau.module.modules.misc.cheatdetector.CheatDetectorData;
import miau.property.properties.BooleanProperty;
import miau.property.properties.FloatProperty;
import miau.property.properties.ModeProperty;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayer;

public class CheatDetector extends Module {
  private static final Minecraft mc = Minecraft.getMinecraft();

  public final ModeProperty alertMode =
      new ModeProperty("alert-mode", 0, new String[] {"Notification", "Chat"});
  public final FloatProperty alertCoolDown = new FloatProperty("alert-cooldown", 1000f, 0f, 2000f);

  public final BooleanProperty checkKillaura = new BooleanProperty("killaura", true);
  public final BooleanProperty checkMovement = new BooleanProperty("movement", true);
  public final BooleanProperty checkScaffold = new BooleanProperty("scaffold", true);

  public final BooleanProperty selfCheck = new BooleanProperty("check-self", false);

  private final Set<EntityPlayer> cheaters = new HashSet<>();
  private final Map<UUID, CheatDetectorData> dataMap = new HashMap<>();

  public CheatDetector() {
    super("CheatDetector", false);
  }

  public boolean isCheckEnabled(String name) {
    if (name.equals("AutoBlock")
        || name.equals("Killaura")
        || name.equals("AttackRaytraceCheck")
        || name.equals("ClickSpeedLimiterCheck")
        || name.equals("ClickPatternsCheck")
        || name.equals("HeuristicsCheck")) {
      return checkKillaura.getValue();
    }
    if (name.equals("No slow") || name.equals("PhysicsCheck") || name.equals("TimerCheck")) {
      return checkMovement.getValue();
    }
    if (name.equals("BreakSpeedLimiterCheck")
        || name.equals("InteractionRaytraceCheck")
        || name.equals("ScaffoldSneakCheck")
        || name.equals("ScaffoldAngleSnapCheck")
        || name.equals("ScaffoldRotationSpeedCheck")
        || name.equals("ScaffoldJumpAndPlaceCheck")) {
      return checkScaffold.getValue();
    }
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
