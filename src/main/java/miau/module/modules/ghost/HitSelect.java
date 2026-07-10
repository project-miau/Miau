package miau.module.modules.ghost;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import miau.event.EventTarget;
import miau.event.impl.PacketEvent;
import miau.event.impl.TickEvent;
import miau.event.types.EventType;
import miau.event.types.Priority;
import miau.module.Module;
import miau.property.properties.BooleanProperty;
import miau.property.properties.IntProperty;
import miau.property.properties.ModeProperty;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.network.play.client.C02PacketUseEntity;
import net.minecraft.network.play.client.C0APacketAnimation;
import net.minecraft.potion.Potion;
import net.minecraft.util.MovingObjectPosition;

public class HitSelect extends Module {
  private static final Minecraft mc = Minecraft.getMinecraft();

  private static final double HIT_RANGE = 3.0;
  private static final double HIT_RANGE_SQ = HIT_RANGE * HIT_RANGE;
  private static final int HURT_WINDOW_TICKS = 10;
  private static final int SERVER_CONFIRM_COOLDOWN_TICKS = HURT_WINDOW_TICKS;
  private static final int SERVER_CONFIRM_TIMEOUT_TICKS = 30;

  private static final int BLOCK_WAIT_FIRST = 1;
  private static final int BLOCK_SERVER_COOLDOWN = 1 << 3;
  private static final int BLOCK_PREDICTED_BURST = 1 << 4;
  private static final int BLOCK_CRITICALS = 1 << 5;

  public final IntProperty pauseDuration = new IntProperty("pause-ms", 500, 0, 500);
  public final ModeProperty mode = new ModeProperty("Mode", 0, new String[] {"Active", "Passive"});
  public final IntProperty waitFirstHit = new IntProperty("wait-first-ms", 0, 0, 500);
  public final BooleanProperty disableKB = new BooleanProperty("disable-during-kb", false);
  public final BooleanProperty onlyDamaged = new BooleanProperty("only-while-damaged", false);
  public final BooleanProperty serverAttack = new BooleanProperty("server-attack-time", false);
  public final BooleanProperty fakeSwing = new BooleanProperty("fake-swing", false);
  public final IntProperty combatCancel = new IntProperty("combat-cancel-%", 100, 0, 100);
  public final IntProperty missedCancel = new IntProperty("missed-cancel-%", 0, 0, 100);

  private EntityPlayer currentTarget;
  private final Map<Integer, TargetState> targetStates = new HashMap<>();
  private int lastSelfHurtTime;
  private boolean takingKnockback;
  private boolean waitFirstTracking;
  private int waitFirstStartTick = -1;
  private boolean waitFirstUnlocked;
  private int tickCounter;

  public HitSelect() {
    super("HitSelect", false);
  }

  @Override
  public void onEnabled() {
    tickCounter = 0;
    resetAllState();
  }

  @Override
  public void onDisabled() {
    resetAllState();
  }

  private int msToTicks(double ms) {
    if (ms <= 0.0) return 0;
    return (int) Math.ceil(ms / 50.0);
  }

  @EventTarget
  public void onTick(TickEvent e) {
    if (!isEnabled()) return;
    if (e.getType() != EventType.PRE) return;
    if (mc.thePlayer == null || mc.thePlayer.isDead) {
      resetAllState();
      return;
    }

    tickCounter++;
    pruneTargetStates();

    EntityPlayer nextTarget = findTarget(HIT_RANGE_SQ);
    updateCurrentTarget(nextTarget, tickCounter);
    updateSelfDamage(tickCounter);
    updateTargetDamage(tickCounter);
  }

  @EventTarget(Priority.HIGHEST)
  public void onPacket(PacketEvent e) {
    if (!isEnabled()) return;
    if (e.getType() != EventType.SEND) return;
    if (mc.thePlayer == null || mc.thePlayer.isDead) return;

    if (e.getPacket() instanceof C0APacketAnimation) {
      MovingObjectPosition mop = mc.objectMouseOver;
      ClickType clickType = classifyClick(mop);
      if (clickType == ClickType.MISSED_SWING) {
        if (shouldCancel(missedCancel.getValue())) {
          e.setCancelled(true);
        }
      }
    }

    if (e.getPacket() instanceof C02PacketUseEntity) {
      C02PacketUseEntity packet = (C02PacketUseEntity) e.getPacket();
      if (packet.getAction() != C02PacketUseEntity.Action.ATTACK) return;

      Entity target = packet.getEntityFromWorld(mc.theWorld);

      MovingObjectPosition mop = mc.objectMouseOver;
      ClickType clickType = classifyClick(mop);

      if (clickType == ClickType.BLOCK_INTERACTION) return;

      EntityPlayer clickedTarget = asValidPlayer(target, HIT_RANGE_SQ);
      if (clickedTarget == null) return;

      updateCurrentTarget(clickedTarget, tickCounter);
      TargetState state = getTargetState(clickedTarget, tickCounter);

      int blockMask = getValidHitBlockMask(state, tickCounter);

      boolean shouldBlock =
          (blockMask & BLOCK_WAIT_FIRST) != 0
              || (blockMask & BLOCK_PREDICTED_BURST) != 0
              || applyPauseDuration(state, blockMask & ~BLOCK_PREDICTED_BURST, tickCounter);

      if (shouldBlock && shouldCancel(combatCancel.getValue())) {
        if (fakeSwing.getValue()) {
          mc.thePlayer.swingItem();
        }
        e.setCancelled(true);
        return;
      }

      recordPassedValidHit(clickedTarget, tickCounter);
    }
  }

  private ClickType classifyClick(MovingObjectPosition mop) {
    if (mop == null) return ClickType.MISSED_SWING;
    if (mop.typeOfHit == MovingObjectPosition.MovingObjectType.BLOCK)
      return ClickType.BLOCK_INTERACTION;
    if (mop.typeOfHit == MovingObjectPosition.MovingObjectType.ENTITY) {
      return asValidPlayer(mop.entityHit, HIT_RANGE_SQ) != null
          ? ClickType.VALID_HIT
          : ClickType.MISSED_SWING;
    }
    return ClickType.MISSED_SWING;
  }

  private void updateCurrentTarget(EntityPlayer nextTarget, int currentTick) {
    if (sameTarget(nextTarget)) {
      if (nextTarget != null) {
        currentTarget = nextTarget;
        getTargetState(nextTarget, currentTick);
      }
      return;
    }
    currentTarget = nextTarget;
    if (nextTarget == null) {
      resetWaitFirstState();
    } else if (!waitFirstTracking) {
      waitFirstTracking = true;
      waitFirstStartTick = currentTick;
      waitFirstUnlocked = false;
    }
    if (nextTarget != null) getTargetState(nextTarget, currentTick);
  }

  private void updateSelfDamage(int currentTick) {
    int hurtTime = mc.thePlayer.hurtTime;
    boolean hurtAgain = hurtTime > lastSelfHurtTime;
    if (hurtAgain) {
      if (waitFirstTracking && !waitFirstUnlocked) waitFirstUnlocked = true;
      if (!takingKnockback) takingKnockback = true;
      if (currentTarget != null) {
        TargetState state = getTargetState(currentTarget, currentTick);
        state.firstSelfHitSeen = true;
      }
    }
    if (takingKnockback && mc.thePlayer.onGround && !hurtAgain) takingKnockback = false;
    lastSelfHurtTime = hurtTime;
  }

  private void updateTargetDamage(int currentTick) {
    if (currentTarget == null || !serverAttack.getValue()) return;
    TargetState state = getTargetState(currentTarget, currentTick);
    int targetHurtTime = currentTarget.hurtTime;
    if (state.pendingServerConfirmationTick >= 0
        && currentTick - state.pendingServerConfirmationTick > SERVER_CONFIRM_TIMEOUT_TICKS)
      state.pendingServerConfirmationTick = -1;
    if (state.pendingServerConfirmationTick >= 0
        && targetHurtTime > state.lastObservedTargetHurtTime) {
      state.pendingServerConfirmationTick = -1;
      state.lastConfirmedTargetDamageTick = currentTick;
      state.rawBlockMask = BLOCK_SERVER_COOLDOWN;
      state.rawBlockStartTick = currentTick;
    }
    state.lastObservedTargetHurtTime = targetHurtTime;
  }

  private int getValidHitBlockMask(TargetState state, int currentTick) {
    if (currentTarget == null) return 0;
    if (disableKB.getValue() && isTakingKnockback()) return 0;
    int blockMask = 0;
    if (isWaitingForFirstHit(currentTick)) blockMask |= BLOCK_WAIT_FIRST;
    blockMask |= getBurstBlockMask(state, currentTick);
    if (isCriticalsBlocked(state, currentTick)) blockMask |= BLOCK_CRITICALS;
    return blockMask;
  }

  private int getBurstBlockMask(TargetState state, int currentTick) {
    if (serverAttack.getValue()) {
      if (state.lastConfirmedTargetDamageTick >= 0
          && currentTick - state.lastConfirmedTargetDamageTick < SERVER_CONFIRM_COOLDOWN_TICKS)
        return BLOCK_SERVER_COOLDOWN;
      return 0;
    }
    if (!isPredictedBurstWindowActive(state, currentTick)) return 0;
    int pauseTicks = msToTicks(pauseDuration.getValue());
    return pauseTicks > 0 && currentTick - state.predictedBurstWindowStartTick < pauseTicks
        ? BLOCK_PREDICTED_BURST
        : 0;
  }

  private boolean isCriticalsBlocked(TargetState state, int currentTick) {
    if (mode.getValue() != 1) return false;
    if (mc.thePlayer.onGround) return false;
    if (onlyDamaged.getValue() && !state.firstSelfHitSeen) return false;
    if (disableKB.getValue() && isTakingKnockback()) return false;
    return !canCriticalHit();
  }

  private boolean isWaitingForFirstHit(int currentTick) {
    int waitMs = waitFirstHit.getValue();
    if (waitMs <= 0
        || currentTarget == null
        || !waitFirstTracking
        || waitFirstUnlocked
        || waitFirstStartTick < 0) return false;
    int requiredTicks = msToTicks(waitMs);
    return requiredTicks > 0 && currentTick - waitFirstStartTick < requiredTicks;
  }

  private boolean canCriticalHit() {
    return mc.thePlayer.fallDistance > 0.0f
        && !mc.thePlayer.onGround
        && !mc.thePlayer.isOnLadder()
        && !mc.thePlayer.isInWater()
        && !mc.thePlayer.isPotionActive(Potion.blindness)
        && mc.thePlayer.ridingEntity == null;
  }

  private boolean isTakingKnockback() {
    return takingKnockback || mc.thePlayer.hurtTime > 0;
  }

  private boolean applyPauseDuration(TargetState state, int blockMask, int currentTick) {
    if (blockMask == 0) {
      state.rawBlockMask = 0;
      state.rawBlockStartTick = -1;
      return false;
    }
    int pauseMs = pauseDuration.getValue();
    if (pauseMs <= 0) {
      state.rawBlockMask = blockMask;
      state.rawBlockStartTick = currentTick;
      return false;
    }
    if (blockMask != state.rawBlockMask) {
      state.rawBlockMask = blockMask;
      state.rawBlockStartTick = currentTick;
    } else if (state.rawBlockStartTick < 0) {
      state.rawBlockStartTick = currentTick;
    }
    int requiredTicks = msToTicks(pauseMs);
    return requiredTicks > 0 && currentTick - state.rawBlockStartTick < requiredTicks;
  }

  private void recordPassedValidHit(EntityPlayer target, int currentTick) {
    if (target == null) return;
    updateCurrentTarget(target, currentTick);
    TargetState state = getTargetState(target, currentTick);
    if (serverAttack.getValue()) {
      state.pendingServerConfirmationTick = currentTick;
      state.lastConfirmedTargetDamageTick = -1;
      return;
    }
    if (!isPredictedBurstWindowActive(state, currentTick))
      startPredictedBurstWindow(state, currentTick, HURT_WINDOW_TICKS);
  }

  private boolean shouldCancel(double chance) {
    if (chance <= 0.0) return false;
    if (chance >= 100.0) return true;
    return Math.random() * 100.0 < chance;
  }

  private boolean sameTarget(EntityPlayer nextTarget) {
    if (currentTarget == null || nextTarget == null) return currentTarget == nextTarget;
    return currentTarget.getEntityId() == nextTarget.getEntityId();
  }

  private void resetWaitFirstState() {
    waitFirstTracking = false;
    waitFirstStartTick = -1;
    waitFirstUnlocked = false;
  }

  private boolean isPredictedBurstWindowActive(TargetState state, int currentTick) {
    return state.predictedBurstWindowEndTick >= 0
        && currentTick < state.predictedBurstWindowEndTick;
  }

  private void startPredictedBurstWindow(TargetState state, int startTick, int windowTicks) {
    state.predictedBurstWindowStartTick = startTick;
    state.predictedBurstWindowEndTick = startTick + Math.max(1, windowTicks);
  }

  private TargetState getTargetState(EntityPlayer target, int currentTick) {
    TargetState state = targetStates.get(target.getEntityId());
    if (state == null) {
      state = new TargetState();
      if (serverAttack.getValue()) state.lastObservedTargetHurtTime = target.hurtTime;
      targetStates.put(target.getEntityId(), state);
    }
    return state;
  }

  private void pruneTargetStates() {
    if (mc.theWorld == null) {
      targetStates.clear();
      return;
    }
    Iterator<Map.Entry<Integer, TargetState>> it = targetStates.entrySet().iterator();
    while (it.hasNext()) {
      Map.Entry<Integer, TargetState> entry = it.next();
      Entity entity = mc.theWorld.getEntityByID(entry.getKey());
      if (!(entity instanceof EntityPlayer)
          || entity.isDead
          || ((EntityPlayer) entity).deathTime != 0) it.remove();
    }
  }

  private void resetAllState() {
    currentTarget = null;
    targetStates.clear();
    lastSelfHurtTime = 0;
    takingKnockback = false;
    resetWaitFirstState();
  }

  private EntityPlayer findTarget(double rangeSq) {
    EntityPlayer closest = null;
    double closestDist = rangeSq;
    for (EntityPlayer player : mc.theWorld.playerEntities) {
      if (player == mc.thePlayer || player.isDead || player.deathTime != 0) continue;
      double dist = mc.thePlayer.getDistanceSqToEntity(player);
      if (dist < closestDist) {
        closestDist = dist;
        closest = player;
      }
    }
    return closest;
  }

  private EntityPlayer asValidPlayer(Entity entity, double rangeSq) {
    if (!(entity instanceof EntityPlayer)) return null;
    EntityPlayer player = (EntityPlayer) entity;
    if (player == mc.thePlayer || player.isDead || player.deathTime != 0) return null;
    if (mc.thePlayer.getDistanceSqToEntity(player) > rangeSq) return null;
    return player;
  }

  private enum ClickType {
    VALID_HIT,
    BLOCK_INTERACTION,
    MISSED_SWING
  }

  private static class TargetState {
    boolean firstSelfHitSeen;
    int lastConfirmedTargetDamageTick = -1;
    int pendingServerConfirmationTick = -1;
    int predictedBurstWindowStartTick = -1;
    int predictedBurstWindowEndTick = -1;
    int lastObservedTargetHurtTime;
    int rawBlockStartTick = -1;
    int rawBlockMask;
  }
}
