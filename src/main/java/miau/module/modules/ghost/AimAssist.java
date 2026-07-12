package miau.module.modules.ghost;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Random;
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
import net.minecraft.util.MathHelper;

public class AimAssist extends Module {

  private static final Minecraft mc = Minecraft.getMinecraft();
  private final Random random = new Random();

  public final ModeProperty mode = new ModeProperty("mode", 0, new String[] {"NORMAL", "SILENT"});
  public final IntProperty speed = new IntProperty("speed", 10, 1, 30);
  public final FloatProperty multipointHorizontal = new FloatProperty("multipoint-horizontal", 0.0F, 0.0F, 100.0F);
  public final FloatProperty multipointVertical = new FloatProperty("multipoint-vertical", 0.0F, 0.0F, 100.0F);
  public final FloatProperty randomization = new FloatProperty("randomization", 50.0F, 0.0F, 100.0F);
  public final FloatProperty fov = new FloatProperty("fov", 90.0F, 15.0F, 360.0F);
  public final FloatProperty range = new FloatProperty("range", 4.5F, 0.0F, 5.0F);
  public final ModeProperty sortMode = new ModeProperty("sort", 1, new String[] {"HEALTH", "ANGLE", "HURT_TIME", "DISTANCE"});

  public final BooleanProperty ignoreBehindWalls = new BooleanProperty("ignore-behind-walls", false);
  public final BooleanProperty ignoreBehindEntities = new BooleanProperty("ignore-behind-entities", false);
  public final BooleanProperty aimInvis = new BooleanProperty("aim-invis", false);
  public final BooleanProperty clickAim = new BooleanProperty("require-mouse", true);
  public final BooleanProperty ignoreTeammates = new BooleanProperty("ignore-teammates", true);
  public final BooleanProperty stopWhenBreaking = new BooleanProperty("stop-when-breaking", false);
  public final BooleanProperty keepMoveDirection = new BooleanProperty("keep-move-direction", true);
  public final IntProperty hoverDelay = new IntProperty("hover-delay", 100, 0, 500);
  public final BooleanProperty weaponOnly = new BooleanProperty("weapon-only", false);

  private long miningStartTime = -1L;
  private float silentYaw, silentPitch;
  private float yawVelocity, pitchVelocity;
  private float randomYawOffset, randomPitchOffset;
  private float targetRandomYaw, targetRandomPitch;
  private long lastRandomUpdate = 0L;
  private boolean firstRotation = true;
  private EntityPlayer lastTarget = null;

  public AimAssist() {
    super("AimAssist", false);
  }

  @Override
  public void onDisabled() {
    miningStartTime = -1L;
    firstRotation = true;
    yawVelocity = 0f;
    pitchVelocity = 0f;
    lastTarget = null;
    super.onDisabled();
  }

  @EventTarget
  public void onUpdate(UpdateEvent event) {
    if (this.mode.getValue()!= 1 ||!conditionsMet()) {
      if (conditionsMet()) firstRotation = true; 
      return;
    }
    EntityPlayer target = getEnemy(true);
    if (target == null) {
      firstRotation = true;
      lastTarget = null;
      return;
    }

    boolean allowThroughBlocks =!this.ignoreBehindWalls.getValue();
    boolean allowThroughEntities =!this.ignoreBehindEntities.getValue();

    float baseYaw = firstRotation? mc.thePlayer.rotationYaw : RotationUtil.serverYaw;
    float basePitch = firstRotation? mc.thePlayer.rotationPitch : RotationUtil.serverPitch;

    if (firstRotation) {
      silentYaw = baseYaw;
      silentPitch = basePitch;
      firstRotation = false;
    }

    if (lastTarget!= target) {
      yawVelocity *= 0.3f;
      pitchVelocity *= 0.3f;
      lastTarget = target;
    }

    float[] rot = RotationUtil.getRotationsWithBackup(
            target,
            this.multipointHorizontal.getValue(),
            this.multipointVertical.getValue(),
            baseYaw,
            basePitch,
            this.range.getValue(),
            allowThroughBlocks,
            allowThroughEntities);
    if (rot == null) return;

    updateRandomization();

    float targetYaw = rot[0] + randomYawOffset;
    float targetPitch = rot[1] + randomPitchOffset;
    targetPitch = MathHelper.clamp_float(targetPitch, -90f, 90f);

    float deltaYaw = MathHelper.wrapAngleTo180_float(targetYaw - silentYaw);
    float deltaPitch = targetPitch - silentPitch;

    if (Math.abs(deltaYaw) < 0.18f && Math.abs(deltaPitch) < 0.18f) {
      event.setRotation(silentYaw, silentPitch, 10);
      return;
    }

    float speedVal = this.speed.getValue();
    float baseLerp = 0.03f + (speedVal / 30f) * 0.22f;
    float angleDist = (float)Math.sqrt(deltaYaw * deltaYaw + deltaPitch * deltaPitch);
    float distanceBoost = MathHelper.clamp_float(angleDist / 20f, 0f, 1.2f);
    float lerpFactor = baseLerp * (1f + distanceBoost * 0.55f);
    float smoothTime = 1.05f - lerpFactor; 
    silentYaw = smoothDampAngle(silentYaw, targetYaw, 0, smoothTime, 120f);
    silentPitch = smoothDampAngle(silentPitch, targetPitch, 1, smoothTime, 90f);

    float maxYawStep = 8f + speedVal * 1.6f;
    float maxPitchStep = 6f + speedVal * 1.1f;
    float clampedYawDelta = MathHelper.clamp_float(MathHelper.wrapAngleTo180_float(silentYaw - baseYaw), -maxYawStep, maxYawStep);
    float clampedPitchDelta = MathHelper.clamp_float(silentPitch - basePitch, -maxPitchStep, maxPitchStep);

    float finalYaw = baseYaw + clampedYawDelta;
    float finalPitch = MathHelper.clamp_float(basePitch + clampedPitchDelta, -90f, 90f);

    silentYaw = finalYaw;
    silentPitch = finalPitch;

    event.setRotation(finalYaw, finalPitch, 10);
  }

  private void updateRandomization() {
    if (this.randomization.getValue() <= 0.01f) {
      randomYawOffset *= 0.85f;
      randomPitchOffset *= 0.85f;
      return;
    }
    long now = System.currentTimeMillis();
    if (now - lastRandomUpdate > 180 + random.nextInt(170)) {
      float str = this.randomization.getValue() / 100f;
      targetRandomYaw = (random.nextFloat() - 0.5f) * 2f * str * 1.2f;
      targetRandomPitch = (random.nextFloat() - 0.5f) * 2f * str * 0.9f;
      lastRandomUpdate = now;
    }
    randomYawOffset += (targetRandomYaw - randomYawOffset) * 0.06f;
    randomPitchOffset += (targetRandomPitch - randomPitchOffset) * 0.06f;
  }

  private float smoothDampAngle(float current, float target, int velocityIndex, float smoothTime, float maxSpeed) {
    smoothTime = Math.max(0.0001f, smoothTime);
    float delta = MathHelper.wrapAngleTo180_float(target - current);
    float targetWrapped = current + delta;

    float velocity = velocityIndex == 0? yawVelocity : pitchVelocity;
    float omega = 2f / smoothTime;
    float x = omega * 0.016f;
    float exp = 1f / (1f + x + 0.48f * x * x + 0.235f * x * x * x);

    float change = current - targetWrapped;
    float originalTo = targetWrapped;

    float maxChange = maxSpeed * smoothTime;
    change = MathHelper.clamp_float(change, -maxChange, maxChange);
    targetWrapped = current - change;

    float temp = (velocity + omega * change) * 0.016f;
    velocity = (velocity - omega * temp) * exp;
    float output = targetWrapped + (change + temp) * exp;

    if (velocityIndex == 0) yawVelocity = velocity;
    else pitchVelocity = velocity;

    if ((originalTo - current > 0) == (output > originalTo)) {
      output = originalTo;
      if (velocityIndex == 0) yawVelocity = 0; else pitchVelocity = 0;
    }
    return output;
  }

  @EventTarget
  public void onTick(TickEvent event) {
    if (event.getType()!= EventType.POST) return;
    if (this.mode.getValue()!= 0 ||!conditionsMet()) return;
    EntityPlayer target = getEnemy(false);
    if (target == null) return;

    boolean allowThroughBlocks =!this.ignoreBehindWalls.getValue();
    boolean allowThroughEntities =!this.ignoreBehindEntities.getValue();

    float[] rot = RotationUtil.getRotationsWithBackup(target, multipointHorizontal.getValue(), multipointVertical.getValue(), mc.thePlayer.rotationYaw, mc.thePlayer.rotationPitch, range.getValue(), allowThroughBlocks, allowThroughEntities);
    if (rot == null) return;

    float[] smooth = RotationUtil.smoothRotation(mc.thePlayer.rotationYaw, mc.thePlayer.rotationPitch, rot[0], rot[1], speed.getValue(), randomization.getValue());
    mc.thePlayer.rotationYaw = smooth[0];
    mc.thePlayer.rotationPitch = smooth[1];
  }

  private EntityPlayer getEnemy(boolean silentMode) {
    float viewYaw = silentMode? (firstRotation? mc.thePlayer.rotationYaw : silentYaw) : mc.thePlayer.rotationYaw;
    List<EntityPlayer> candidates = new ArrayList<>();
    for (Entity player : mc.theWorld.playerEntities) {
      if (!(player instanceof EntityPlayer)) continue;
      EntityPlayer entityPlayer = (EntityPlayer) player;
      if (entityPlayer == mc.thePlayer || entityPlayer.deathTime!= 0) continue;
      if (TeamUtil.isFriend(entityPlayer)) continue;
      if (this.ignoreTeammates.getValue() && TeamUtil.isSameTeam(entityPlayer)) continue;
      if (!this.aimInvis.getValue() && entityPlayer.isInvisible()) continue;
      if (RotationUtil.distanceSqFromEyeToClosestOnAABB(entityPlayer) > this.range.getValue() * this.range.getValue()) continue;
      if (TeamUtil.isBot(entityPlayer)) continue;
      if (this.fov.getValue()!= 360.0F) {
        double deltaX = entityPlayer.posX - mc.thePlayer.posX;
        double deltaZ = entityPlayer.posZ - mc.thePlayer.posZ;
        float targetYaw = (float) (Math.atan2(deltaZ, deltaX) * 180.0 / Math.PI) - 90.0f;
        float diff = Math.abs(MathHelper.wrapAngleTo180_float(targetYaw - viewYaw));
        if (diff > this.fov.getValue() / 2.0F) continue;
      }
      candidates.add(entityPlayer);
    }
    if (candidates.isEmpty()) return null;
    Comparator<EntityPlayer> primary;
    switch (this.sortMode.getValue()) {
      case 0: primary = Comparator.comparingDouble(p -> p.getHealth() + p.getAbsorptionAmount()); break;
      case 1: primary = Comparator.comparingDouble(p -> { float[] rots = RotationUtil.getRotations(p); double yawDelta = Math.abs(MathHelper.wrapAngleTo180_float(rots[0] - viewYaw)); double pitchDelta = Math.abs(MathHelper.wrapAngleTo180_float(rots[1] - mc.thePlayer.rotationPitch)); return yawDelta + pitchDelta; }); break;
      case 2: primary = Comparator.comparingInt(p -> p.hurtTime); break;
      case 3: default: primary = Comparator.comparingDouble(p -> mc.thePlayer.getDistanceSqToEntity(p)); break;
    }
    candidates.sort(primary.thenComparingDouble(p -> mc.thePlayer.getDistanceSqToEntity(p)));
    if (this.ignoreBehindWalls.getValue() || this.ignoreBehindEntities.getValue()) {
      boolean allowThroughBlocks =!this.ignoreBehindWalls.getValue();
      boolean allowThroughEntities =!this.ignoreBehindEntities.getValue();
      for (EntityPlayer candidate : candidates) {
        if (RotationUtil.hasValidAimPoint(candidate, this.multipointHorizontal.getValue(), this.multipointVertical.getValue(), this.range.getValue(), allowThroughBlocks, allowThroughEntities)) return candidate;
      }
      return null;
    }
    return candidates.get(0);
  }

  private boolean conditionsMet() {
    if (mc.currentScreen!= null ||!mc.inGameHasFocus) return false;
    if (this.weaponOnly.getValue() &&!ItemUtil.isHoldingSword()) return false;
    if (this.clickAim.getValue() &&!KeyBindUtil.isKeyDown(mc.gameSettings.keyBindAttack.getKeyCode())) return false;
    if (this.stopWhenBreaking.getValue() && PlayerUtil.isAttacking() && mc.objectMouseOver!= null && mc.objectMouseOver.typeOfHit == net.minecraft.util.MovingObjectPosition.MovingObjectType.BLOCK) {
      if (miningStartTime == -1L) miningStartTime = System.currentTimeMillis();
      long elapsed = System.currentTimeMillis() - miningStartTime;
      if (elapsed >= this.hoverDelay.getValue()) return false;
    } else miningStartTime = -1L;
    return true;
  }
}