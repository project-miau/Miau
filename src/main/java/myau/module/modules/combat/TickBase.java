package myau.module.modules.combat;

import static org.lwjgl.opengl.GL11.*;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;
import myau.Myau;
import myau.event.EventTarget;
import myau.event.impl.PacketEvent;
import myau.event.impl.PlayerUpdateEvent;
import myau.event.impl.Render3DEvent;
import myau.event.impl.TickEvent;
import myau.event.types.EventType;
import myau.event.types.Priority;
import myau.module.Module;
import myau.property.properties.*;
import myau.util.math.RandomUtil;
import myau.util.player.RotationUtil;
import myau.util.player.SimulatedPlayer;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.network.play.server.S08PacketPlayerPosLook;
import net.minecraft.util.Vec3;

/**
 * TickBase module - ported from RinBounce TickBase.kt
 *
 * <p>Allows skipping game ticks and fast-forwarding player movement to approach targets faster by
 * manipulating the client tick cycle.
 *
 * <p>Modes: - Past: Skip ticks first (server receives fewer updates), then fast-forward - Future:
 * Fast-forward first (more movement compressed), then skip ticks
 */
public class TickBase extends Module {

  private static final Minecraft mc = Minecraft.getMinecraft();

  // ===== Properties =====
  public final ModeProperty mode = new ModeProperty("Mode", 0, new String[] {"Past", "Future"});
  public final BooleanProperty onlyOnKillAura = new BooleanProperty("OnlyOnKillAura", true);

  public final IntProperty change = new IntProperty("Changes", 100, 0, 100);

  public final IntProperty balanceMaxValue = new IntProperty("BalanceMaxValue", 100, 1, 1000);
  public final FloatProperty balanceRecoveryIncrement =
      new FloatProperty("BalanceRecoveryIncrement", 0.1f, 0.01f, 10f);
  public final IntProperty maxTicksAtATime = new IntProperty("MaxTicksAtATime", 20, 1, 100);

  // Mimics floatRange(min..max, 0f..10f) from RinBounce
  public final FloatProperty rangeToAttackMin =
      new FloatProperty("RangeToAttackMin", 3.0f, 0f, 10f);
  public final FloatProperty rangeToAttackMax =
      new FloatProperty("RangeToAttackMax", 5.0f, 0f, 10f);

  public final BooleanProperty forceGround = new BooleanProperty("ForceGround", false);
  public final IntProperty pauseAfterTick = new IntProperty("PauseAfterTick", 0, 0, 100);
  public final BooleanProperty pauseOnFlag = new BooleanProperty("PauseOnFlag", true);

  public final BooleanProperty line = new BooleanProperty("Line", true);
  public final ColorProperty lineColor =
      new ColorProperty("LineColor", Color.GREEN.getRGB(), () -> line.getValue());

  // ===== Internal State =====
  private int ticksToSkip = 0;
  private float tickBalance = 0f;
  private boolean reachedTheLimit = false;
  private final List<TickData> tickBuffer = new ArrayList<>();
  boolean duringTickModification = false;
  private boolean modificationFlag = false;

  // Past mode state machine
  private enum Phase {
    IDLE,
    PAST_SKIP,
    PAST_ACCEL
  }

  private Phase phase = Phase.IDLE;
  private int pastAccelRemaining = 0;

  public TickBase() {
    super("TickBase", false);
  }

  @Override
  public void onEnabled() {
    super.onEnabled();
    resetState();
  }

  @Override
  public void onDisabled() {
    super.onDisabled();
    resetState();
  }

  private void resetState() {
    duringTickModification = false;
    modificationFlag = false;
    ticksToSkip = 0;
    tickBalance = 0f;
    reachedTheLimit = false;
    tickBuffer.clear();
    phase = Phase.IDLE;
    pastAccelRemaining = 0;
  }

  @Override
  public String[] getSuffix() {
    return new String[] {mode.getModeString()};
  }

  // ========== Helper: range check (mimics floatRange) ==========
  private boolean isInRangeToAttack(double distance) {
    return distance >= rangeToAttackMin.getValue() && distance <= rangeToAttackMax.getValue();
  }

  // ========== EVENT: Simulate positions into tick buffer ==========
  // Corresponds to onMove in RinBounce — fires at onUpdateWalkingPlayer (movement sync)
  @EventTarget(Priority.HIGHEST)
  public void onPlayerUpdate(PlayerUpdateEvent event) {
    if (!isEnabled()) return;
    if (mc.thePlayer == null) return;
    if (mc.thePlayer.ridingEntity != null || isBlinkEnabled()) return;

    // Don't simulate while modifying
    if (duringTickModification) return;

    tickBuffer.clear();

    SimulatedPlayer simulatedPlayer = SimulatedPlayer.fromClientPlayer(mc.thePlayer.movementInput);

    simulatedPlayer.rotationYaw =
        RotationUtil.serverYaw != 0 ? RotationUtil.serverYaw : mc.thePlayer.rotationYaw;

    // Update balance
    if (tickBalance <= 0) {
      reachedTheLimit = true;
    }
    if (tickBalance > balanceMaxValue.getValue() / 2f) {
      reachedTheLimit = false;
    }
    if (tickBalance <= balanceMaxValue.getValue()) {
      tickBalance += balanceRecoveryIncrement.getValue();
    }

    if (reachedTheLimit) return;

    int simTicks =
        Math.min((int) tickBalance, maxTicksAtATime.getValue() * (mode.getValue() == 0 ? 2 : 1));

    for (int i = 0; i < simTicks; i++) {
      simulatedPlayer.tick();
      tickBuffer.add(
          new TickData(
              simulatedPlayer.getPos(),
              simulatedPlayer.fallDistance,
              simulatedPlayer.motionX,
              simulatedPlayer.motionY,
              simulatedPlayer.motionZ,
              simulatedPlayer.onGround,
              simulatedPlayer.isCollidedHorizontally));
    }
  }

  // ========== EVENT: Main tick logic + state machine ==========
  // Runs at HIGHEST priority - controls all phase transitions
  @EventTarget(Priority.HIGHEST)
  public void onPreTick(TickEvent event) {
    if (!isEnabled()) return;
    if (event.getType() != EventType.PRE) return;
    if (mc.thePlayer == null) return;
    if (mc.thePlayer.ridingEntity != null || isBlinkEnabled()) return;

    // --- Cleanup (corresponds to onGameLoop in RinBounce) ---
    if (modificationFlag) {
      modificationFlag = false;
      duringTickModification = false;
      phase = Phase.IDLE;
      pastAccelRemaining = 0;
    }

    // --- Phase handlers ---
    switch (phase) {
      case PAST_SKIP:
        // Current tick will be cancelled by onCancelTick (HIGH priority)
        // We just check if skipping is done
        if (ticksToSkip <= 0) {
          // Skip phase complete → transition to acceleration
          phase = Phase.PAST_ACCEL;
          pastAccelRemaining = pastAccelRemaining; // already set
          duringTickModification = true; // keep modifying
        }
        return; // Don't enter main logic

      case PAST_ACCEL:
        if (pastAccelRemaining > 0) {
          mc.thePlayer.onUpdate();
          tickBalance -= 1;
          pastAccelRemaining--;
          if (pastAccelRemaining <= 0) {
            modificationFlag = true; // cleanup next tick
          }
        }
        return; // Don't enter main logic
    }

    // --- IDLE phase: Main logic (corresponds to onGameTick in RinBounce) ---
    if (tickBuffer.isEmpty()) return;

    EntityLivingBase nearbyEnemy = getNearestEntityInRange();
    if (nearbyEnemy == null) return;

    Vec3 currentPos = mc.thePlayer.getPositionVector();
    double currentDistance = currentPos.distanceTo(nearbyEnemy.getPositionVector());

    // Find best tick index
    int bestTick = -1;
    int bestIndex = Integer.MAX_VALUE;
    boolean foundCritical = false;

    for (int i = 0; i < tickBuffer.size(); i++) {
      TickData tick = tickBuffer.get(i);
      double tickDistance = tick.position.distanceTo(nearbyEnemy.getPositionVector());

      if (tickDistance < currentDistance
          && isInRangeToAttack(tickDistance)
          && !tick.isCollidedHorizontally
          && (!forceGround.getValue() || tick.onGround)) {

        if (!foundCritical && tick.fallDistance > 0.0f) {
          bestTick = i;
          bestIndex = i;
          foundCritical = true;
        } else if (!foundCritical && i < bestIndex) {
          bestTick = i;
          bestIndex = i;
        } else if (foundCritical && tick.fallDistance > 0.0f && i < bestIndex) {
          bestTick = i;
          bestIndex = i;
        }
      }
    }

    if (bestTick < 0 || bestTick == 0) return;

    // Chance check
    if (RandomUtil.nextInt(0, 100) > change.getValue()) {
      ticksToSkip = 0;
      return;
    }

    // KillAura check
    if (onlyOnKillAura.getValue()) {
      KillAura killAura = (KillAura) Myau.moduleManager.modules.get(KillAura.class);
      if (killAura == null || !killAura.isEnabled() || getKillAuraTarget(killAura) == null) {
        ticksToSkip = 0;
        return;
      }
    }

    // Start modification
    duringTickModification = true;
    int skipTicks =
        Math.min(
            bestTick + pauseAfterTick.getValue(),
            maxTicksAtATime.getValue() + pauseAfterTick.getValue());

    if (mode.getValue() == 0) {
      // ===== PAST MODE =====
      // Phase 1: Skip ticks (handled by onCancelTick)
      // Phase 2: Then fast-forward
      ticksToSkip = skipTicks;
      pastAccelRemaining = skipTicks;
      phase = Phase.PAST_SKIP;
    } else {
      // ===== FUTURE MODE =====
      // Fast-forward first (same tick), then skip
      for (int i = 0; i < skipTicks; i++) {
        mc.thePlayer.onUpdate();
        tickBalance -= 1;
      }
      ticksToSkip = skipTicks;
      modificationFlag = true; // cleanup next tick
    }
  }

  // ========== EVENT: Cancel ticks when skipping ==========
  @EventTarget(Priority.HIGH)
  public void onCancelTick(TickEvent event) {
    if (!isEnabled()) return;
    if (event.getType() != EventType.PRE) return;
    if (mc.thePlayer == null) return;
    if (mc.thePlayer.ridingEntity != null || isBlinkEnabled()) return;

    if (ticksToSkip-- > 0) {
      event.setCancelled(true);
    }
  }

  // ========== EVENT: Reset balance on server position flag ==========
  @EventTarget
  public void onPacket(PacketEvent event) {
    if (!isEnabled()) return;
    if (!pauseOnFlag.getValue()) return;

    if (event.getType() == EventType.RECEIVE
        && event.getPacket() instanceof S08PacketPlayerPosLook) {
      tickBalance = 0f;
    }
  }

  // ========== EVENT: Render prediction line ==========
  @EventTarget
  public void onRender3D(Render3DEvent event) {
    if (!isEnabled()) return;
    if (!line.getValue()) return;

    synchronized (tickBuffer) {
      glPushMatrix();
      glDisable(GL_TEXTURE_2D);
      glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
      glEnable(GL_LINE_SMOOTH);
      glEnable(GL_BLEND);
      glDisable(GL_DEPTH_TEST);
      mc.entityRenderer.disableLightmap();

      glBegin(GL_LINE_STRIP);
      Color color = new Color(lineColor.getValue(), true);
      glColor4f(
          color.getRed() / 255f,
          color.getGreen() / 255f,
          color.getBlue() / 255f,
          color.getAlpha() / 255f);

      double renderPosX = mc.getRenderManager().viewerPosX;
      double renderPosY = mc.getRenderManager().viewerPosY;
      double renderPosZ = mc.getRenderManager().viewerPosZ;

      for (TickData tick : tickBuffer) {
        glVertex3d(
            tick.position.xCoord - renderPosX,
            tick.position.yCoord - renderPosY,
            tick.position.zCoord - renderPosZ);
      }

      glColor4f(1.0f, 1.0f, 1.0f, 1.0f);
      glEnd();
      glEnable(GL_DEPTH_TEST);
      glDisable(GL_LINE_SMOOTH);
      glDisable(GL_BLEND);
      glEnable(GL_TEXTURE_2D);
      glPopMatrix();
    }
  }

  // ========== Helpers ==========

  private myau.module.modules.movement.Blink blink = null;

  private boolean isBlinkEnabled() {
    if (blink == null) {
      blink =
          (myau.module.modules.movement.Blink)
              Myau.moduleManager.modules.get(myau.module.modules.movement.Blink.class);
    }
    return blink != null && blink.isEnabled();
  }

  private EntityLivingBase getKillAuraTarget(KillAura ka) {
    try {
      java.lang.reflect.Field targetField = KillAura.class.getDeclaredField("target");
      targetField.setAccessible(true);
      Object attackData = targetField.get(ka);
      if (attackData != null) {
        return (EntityLivingBase) attackData.getClass().getMethod("getEntity").invoke(attackData);
      }
    } catch (Exception ignored) {
    }
    return null;
  }

  private EntityLivingBase getNearestEntityInRange() {
    if (mc.thePlayer == null || mc.theWorld == null) return null;

    EntityLivingBase nearest = null;
    double nearestDist = Double.MAX_VALUE;

    for (Object o : mc.theWorld.loadedEntityList) {
      if (o instanceof EntityLivingBase) {
        EntityLivingBase entity = (EntityLivingBase) o;
        if (isSelected(entity)) {
          double dist = mc.thePlayer.getDistanceToEntity(entity);
          if (dist < nearestDist) {
            nearestDist = dist;
            nearest = entity;
          }
        }
      }
    }
    return nearest;
  }

  private boolean isSelected(EntityLivingBase entity) {
    if (entity == mc.thePlayer || entity == mc.getRenderViewEntity()) return false;
    if (entity.isDead || entity.deathTime > 0) return false;
    if (entity instanceof EntityPlayer) {
      EntityPlayer player = (EntityPlayer) entity;
      if (player.isInvisible() && !canTargetInvisibles()) return false;
      if (Myau.friendManager != null && Myau.friendManager.isFriend(player.getName())) return false;
    }
    return true;
  }

  private boolean canTargetInvisibles() {
    KillAura ka = (KillAura) Myau.moduleManager.modules.get(KillAura.class);
    if (ka != null) {
      try {
        java.lang.reflect.Field f = KillAura.class.getDeclaredField("targetInvisibles");
        f.setAccessible(true);
        BooleanProperty prop = (BooleanProperty) f.get(ka);
        return prop.getValue();
      } catch (Exception ignored) {
      }
    }
    return false;
  }

  // ===== TickData =====
  public static class TickData {
    public final Vec3 position;
    public final float fallDistance;
    public final double motionX;
    public final double motionY;
    public final double motionZ;
    public final boolean onGround;
    public final boolean isCollidedHorizontally;

    public TickData(
        Vec3 position,
        float fallDistance,
        double motionX,
        double motionY,
        double motionZ,
        boolean onGround,
        boolean isCollidedHorizontally) {
      this.position = position;
      this.fallDistance = fallDistance;
      this.motionX = motionX;
      this.motionY = motionY;
      this.motionZ = motionZ;
      this.onGround = onGround;
      this.isCollidedHorizontally = isCollidedHorizontally;
    }
  }
}
