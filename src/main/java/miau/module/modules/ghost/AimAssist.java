package miau.module.modules.ghost;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import miau.event.EventTarget;
import miau.event.impl.TickEvent;
import miau.event.impl.UpdateEvent;
import miau.event.types.EventType;
import miau.module.Module;
import miau.property.properties.BooleanProperty;
import miau.property.properties.FloatProperty;
import miau.property.properties.IntProperty;
import miau.property.properties.ModeProperty;
import miau.util.client.KeyBindUtil;
import miau.util.player.ItemUtil;
import miau.util.player.PlayerUtil;
import miau.util.player.RotationUtil;
import miau.util.player.TeamUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;

public class AimAssist extends Module {

  private static final Minecraft mc = Minecraft.getMinecraft();

  public final ModeProperty mode = new ModeProperty("mode", 0, new String[] {"NORMAL", "SILENT"});
  public final IntProperty speed = new IntProperty("speed", 10, 1, 30);
  public final FloatProperty multipointHorizontal =
      new FloatProperty("multipoint-horizontal", 0.0F, 0.0F, 100.0F);
  public final FloatProperty multipointVertical =
      new FloatProperty("multipoint-vertical", 0.0F, 0.0F, 100.0F);
  public final FloatProperty randomization =
      new FloatProperty("randomization", 50.0F, 0.0F, 100.0F);
  public final FloatProperty fov = new FloatProperty("fov", 90.0F, 15.0F, 360.0F);
  public final FloatProperty range = new FloatProperty("range", 4.5F, 0.0F, 5.0F);
  public final ModeProperty sortMode =
      new ModeProperty("sort", 1, new String[] {"HEALTH", "ANGLE", "HURT_TIME", "DISTANCE"});

  public final BooleanProperty ignoreBehindWalls =
      new BooleanProperty("ignore-behind-walls", false);
  public final BooleanProperty ignoreBehindEntities =
      new BooleanProperty("ignore-behind-entities", false);
  public final BooleanProperty aimInvis = new BooleanProperty("aim-invis", false);
  public final BooleanProperty clickAim = new BooleanProperty("require-mouse", true);
  public final BooleanProperty ignoreTeammates = new BooleanProperty("ignore-teammates", true);
  public final BooleanProperty stopWhenBreaking = new BooleanProperty("stop-when-breaking", false);
  public final BooleanProperty keepMoveDirection = new BooleanProperty("keep-move-direction", true);
  public final IntProperty hoverDelay = new IntProperty("hover-delay", 100, 0, 500);
  public final BooleanProperty weaponOnly = new BooleanProperty("weapon-only", false);

  private long miningStartTime = -1L;

  public AimAssist() {
    super("AimAssist", false);
  }

  @Override
  public void onDisabled() {
    miningStartTime = -1L;
    super.onDisabled();
  }

  @EventTarget
  public void onUpdate(UpdateEvent event) {
    if (this.mode.getValue() != 1 || !conditionsMet()) {
      return;
    }
    EntityPlayer target = getEnemy(true);
    if (target == null) {
      return;
    }

    boolean allowThroughBlocks = !this.ignoreBehindWalls.getValue();
    boolean allowThroughEntities = !this.ignoreBehindEntities.getValue();

    float[] rot =
        RotationUtil.getRotationsWithBackup(
            target,
            this.multipointHorizontal.getValue(),
            this.multipointVertical.getValue(),
            mc.thePlayer.rotationYaw,
            mc.thePlayer.rotationPitch,
            this.range.getValue(),
            allowThroughBlocks,
            allowThroughEntities);
    if (rot == null) return;

    float[] smooth =
        RotationUtil.smoothRotation(
            mc.thePlayer.rotationYaw,
            mc.thePlayer.rotationPitch,
            rot[0],
            rot[1],
            this.speed.getValue(),
            this.randomization.getValue());

    event.setRotation(smooth[0], smooth[1], 10);

    if (!this.keepMoveDirection.getValue()) {
      // Logic để keep move direction sẽ handle ở MovementEvent (hoặc ClientRotationEvent tuỳ Miau)
      // Miau thường có RotationUtil xử lý movement input override nếu cần
    }
  }

  @EventTarget
  public void onTick(TickEvent event) {
    if (event.getType() != EventType.POST) return;
    if (this.mode.getValue() != 0 || !conditionsMet()) {
      return;
    }
    EntityPlayer target = getEnemy(false);
    if (target == null) {
      return;
    }

    boolean allowThroughBlocks = !this.ignoreBehindWalls.getValue();
    boolean allowThroughEntities = !this.ignoreBehindEntities.getValue();

    float[] rot =
        RotationUtil.getRotationsWithBackup(
            target,
            this.multipointHorizontal.getValue(),
            this.multipointVertical.getValue(),
            mc.thePlayer.rotationYaw,
            mc.thePlayer.rotationPitch,
            this.range.getValue(),
            allowThroughBlocks,
            allowThroughEntities);
    if (rot == null) return;

    float[] smooth =
        RotationUtil.smoothRotation(
            mc.thePlayer.rotationYaw,
            mc.thePlayer.rotationPitch,
            rot[0],
            rot[1],
            this.speed.getValue(),
            this.randomization.getValue());

    mc.thePlayer.rotationYaw = smooth[0];
    mc.thePlayer.rotationPitch = smooth[1];
  }

  private EntityPlayer getEnemy(boolean silentMode) {
    float viewYaw = mc.thePlayer.rotationYaw;
    if (silentMode) {
      viewYaw = RotationUtil.serverYaw;
    }

    List<EntityPlayer> candidates = new ArrayList<>();
    for (Entity player : mc.theWorld.playerEntities) {
      if (!(player instanceof EntityPlayer)) continue;
      EntityPlayer entityPlayer = (EntityPlayer) player;

      if (entityPlayer == mc.thePlayer || entityPlayer.deathTime != 0) {
        continue;
      }
      if (TeamUtil.isFriend(entityPlayer)) {
        continue;
      }
      if (this.ignoreTeammates.getValue() && TeamUtil.isSameTeam(entityPlayer)) {
        continue;
      }
      if (!this.aimInvis.getValue() && entityPlayer.isInvisible()) {
        continue;
      }
      if (RotationUtil.distanceSqFromEyeToClosestOnAABB(entityPlayer)
          > this.range.getValue() * this.range.getValue()) {
        continue;
      }
      if (TeamUtil.isBot(entityPlayer)) {
        continue;
      }
      if (this.fov.getValue() != 360.0F) {
        double deltaX = entityPlayer.posX - mc.thePlayer.posX;
        double deltaZ = entityPlayer.posZ - mc.thePlayer.posZ;
        float targetYaw = (float) (Math.atan2(deltaZ, deltaX) * 180.0 / Math.PI) - 90.0f;
        float diff =
            Math.abs(net.minecraft.util.MathHelper.wrapAngleTo180_float(targetYaw - viewYaw));

        if (diff > this.fov.getValue() / 2.0F) {
          continue;
        }
      }
      candidates.add(entityPlayer);
    }

    if (candidates.isEmpty()) {
      return null;
    }

    Comparator<EntityPlayer> primary;
    switch (this.sortMode.getValue()) {
      case 0: // HEALTH
        primary = Comparator.comparingDouble(p -> p.getHealth() + p.getAbsorptionAmount());
        break;
      case 1: // ANGLE
        primary =
            Comparator.comparingDouble(
                p -> {
                  float[] rots = RotationUtil.getRotations(p);
                  double yawDelta =
                      Math.abs(
                          net.minecraft.util.MathHelper.wrapAngleTo180_float(
                              rots[0] - mc.thePlayer.rotationYaw));
                  double pitchDelta =
                      Math.abs(
                          net.minecraft.util.MathHelper.wrapAngleTo180_float(
                              rots[1] - mc.thePlayer.rotationPitch));
                  return yawDelta + pitchDelta;
                });
        break;
      case 2: // HURT_TIME
        primary = Comparator.comparingInt(p -> p.hurtTime);
        break;
      case 3: // DISTANCE
        primary = Comparator.comparingDouble(p -> mc.thePlayer.getDistanceSqToEntity(p));
        break;
      default:
        primary = Comparator.comparingDouble(p -> mc.thePlayer.getDistanceSqToEntity(p));
    }

    candidates.sort(primary.thenComparingDouble(p -> mc.thePlayer.getDistanceSqToEntity(p)));

    if (this.ignoreBehindWalls.getValue() || this.ignoreBehindEntities.getValue()) {
      boolean allowThroughBlocks = !this.ignoreBehindWalls.getValue();
      boolean allowThroughEntities = !this.ignoreBehindEntities.getValue();
      for (EntityPlayer candidate : candidates) {
        if (RotationUtil.hasValidAimPoint(
            candidate,
            this.multipointHorizontal.getValue(),
            this.multipointVertical.getValue(),
            this.range.getValue(),
            allowThroughBlocks,
            allowThroughEntities)) {
          return candidate;
        }
      }
      return null;
    }

    return candidates.get(0);
  }

  private boolean conditionsMet() {
    if (mc.currentScreen != null || !mc.inGameHasFocus) {
      return false;
    }
    if (this.weaponOnly.getValue() && !ItemUtil.isHoldingSword()) {
      return false;
    }
    if (this.clickAim.getValue()
        && !KeyBindUtil.isKeyDown(mc.gameSettings.keyBindAttack.getKeyCode())) {
      return false;
    }
    if (this.stopWhenBreaking.getValue()
        && PlayerUtil.isAttacking()
        && mc.objectMouseOver != null
        && mc.objectMouseOver.typeOfHit
            == net.minecraft.util.MovingObjectPosition.MovingObjectType.BLOCK) {
      if (miningStartTime == -1L) {
        miningStartTime = System.currentTimeMillis();
      }
      long elapsed = System.currentTimeMillis() - miningStartTime;
      if (elapsed >= this.hoverDelay.getValue()) {
        return false;
      }
    } else {
      miningStartTime = -1L;
    }
    return true;
  }
}
