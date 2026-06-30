package miau.module.modules.misc;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import miau.Miau;
import miau.event.EventTarget;
import miau.event.impl.PacketEvent;
import miau.event.impl.UpdateEvent;
import miau.module.Module;
import miau.module.modules.misc.cheatdetector.Check;
import miau.module.modules.misc.cheatdetector.impl.combat.InvalidInteract;
import miau.module.modules.misc.cheatdetector.impl.combat.VelocityCheck;
import miau.module.modules.misc.cheatdetector.impl.combat.aim.AimCheck;
import miau.module.modules.misc.cheatdetector.impl.movement.OmniSprintCheck;
import miau.module.modules.misc.cheatdetector.impl.movement.motion.MotionCheck;
import miau.module.modules.misc.cheatdetector.impl.movement.noslow.NoSlowCheck;
import miau.module.modules.misc.cheatdetector.impl.player.LegitScaffoldCheck;
import miau.module.modules.misc.cheatdetector.impl.player.NoFallCheck;
import miau.module.modules.misc.cheatdetector.impl.player.scaffold.ScaffoldCheck;
import miau.property.properties.BooleanProperty;
import miau.property.properties.FloatProperty;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayer;

public class CheatDetector extends Module {
  private static final Minecraft mc = Minecraft.getMinecraft();

  public final BooleanProperty checkAim = new BooleanProperty("aim", true);
  public final BooleanProperty checkInvalidInteract = new BooleanProperty("invalid-interact", true);
  public final BooleanProperty checkMotion = new BooleanProperty("motion", true);
  public final BooleanProperty checkNoFall = new BooleanProperty("no-fall", true);
  public final BooleanProperty checkNoSlow = new BooleanProperty("no-slow", true);
  public final BooleanProperty checkOmniSprint = new BooleanProperty("omni-sprint", true);
  public final BooleanProperty checkScaffold = new BooleanProperty("scaffold", true);
  public final BooleanProperty checkVelocity = new BooleanProperty("velocity", true);
  public final BooleanProperty checkLegitScaffold = new BooleanProperty("legit-scaffold", true);

  public final BooleanProperty selfCheck = new BooleanProperty("check-self", false);
  public final FloatProperty alertCoolDown = new FloatProperty("alert-cooldown", 1000f, 0f, 2000f);

  private final Set<EntityPlayer> cheaters = new HashSet<>();
  private final ArrayList<Check> checks = new ArrayList<>();

  public CheatDetector() {
    super("CheatDetector", false);
    addChecks(
        new AimCheck(),
        new InvalidInteract(),
        new MotionCheck(),
        new NoFallCheck(),
        new NoSlowCheck(),
        new ScaffoldCheck(),
        new VelocityCheck(),
        new OmniSprintCheck(),
        new LegitScaffoldCheck());
  }

  public boolean isCheckEnabled(String name) {
    if ("Aim".equals(name)) return checkAim.getValue();
    if ("Invalid interact".equals(name)) return checkInvalidInteract.getValue();
    if ("Motion".equals(name)) return checkMotion.getValue();
    if ("No fall".equals(name)) return checkNoFall.getValue();
    if ("No slow".equals(name)) return checkNoSlow.getValue();
    if ("Omni sprint".equals(name)) return checkOmniSprint.getValue();
    if ("Scaffold".equals(name)) return checkScaffold.getValue();
    if ("Velocity".equals(name)) return checkVelocity.getValue();
    if ("Legit scaffold".equals(name)) return checkLegitScaffold.getValue();
    return false;
  }

  @EventTarget
  public void onUpdate(UpdateEvent e) {
    if (!this.isEnabled()) return;
    if (mc.theWorld == null) return;

    for (EntityPlayer player : mc.theWorld.playerEntities) {
      if (player.getDistanceToEntity(mc.thePlayer) > 16 * mc.gameSettings.renderDistanceChunks)
        continue;

      for (Check check : checks) {
        if ((selfCheck.getValue() || player != mc.thePlayer)
            && !player.isDead
            && (Miau.friendManager == null || !Miau.friendManager.isFriend(player.getName()))) {
          if (AntiBot.isBot(player)) continue;

          if (isCheckEnabled(check.getName())) {
            check.onUpdate(player);
          }
        }
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

      for (Check check : checks) {
        if ((selfCheck.getValue() || player != mc.thePlayer)
            && !player.isDead
            && (Miau.friendManager == null || !Miau.friendManager.isFriend(player.getName()))) {
          if (AntiBot.isBot(player)) continue;

          if (isCheckEnabled(check.getName())) {
            check.onPacket(e, player);
          }
        }
      }
    }
  }

  @Override
  public void onDisabled() {
    cheaters.clear();
  }

  public void addChecks(Check... checks) {
    this.checks.addAll(Arrays.asList(checks));
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

    checks.forEach(check -> check.cleanup(onlineUUIDs));
  }
}
