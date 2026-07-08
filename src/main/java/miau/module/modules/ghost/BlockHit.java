package miau.module.modules.ghost;

import miau.Miau;
import miau.event.EventTarget;
import miau.event.impl.*;
import miau.event.types.EventType;
import miau.interfaces.IMixinItemRenderer;
import miau.module.Module;
import miau.module.modules.combat.KillAura;
import miau.property.properties.*;
import miau.util.client.KeyBindUtil;
import miau.util.player.ItemUtil;
import miau.util.player.RotationUtil;
import miau.util.player.TeamUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.network.play.client.C02PacketUseEntity;
import net.minecraft.util.MovingObjectPosition;
import org.lwjgl.input.Mouse;

public class BlockHit extends Module {
  private static final Minecraft mc = Minecraft.getMinecraft();

  public final FloatProperty range = new FloatProperty("Range", 4.0F, 2.0F, 6.0F);
  public final FloatProperty maxHurtTimeMs = new FloatProperty("Max Hurt Time", 200F, 50F, 500F);
  public final FloatProperty maxHoldMs = new FloatProperty("Max Hold Time", 150F, 50F, 500F);

  public final PercentProperty lagChance = new PercentProperty("Lag Chance", 100);
  public final FloatProperty lagMaxDuration =
      new FloatProperty("Lag Max Duration", 200F, 50F, 500F);
  public final BooleanProperty preventDelayAttacks =
      new BooleanProperty("Prevent Delay Attacks", true);
  public final BooleanProperty blockAgainImmediately =
      new BooleanProperty("Block Again Immediately", true);
  public final BooleanProperty forceBlockAnimation =
      new BooleanProperty("Force Block Animation", true);

  public final BooleanProperty requireLmb = new BooleanProperty("Require Left Mouse", true);
  public final BooleanProperty requireRmb = new BooleanProperty("Require Right Mouse", false);
  public final BooleanProperty onlyWhenDamaged = new BooleanProperty("Damaged Only", false);
  public final BooleanProperty ignoreTeammates = new BooleanProperty("Ignore Teammates", true);

  private boolean isBlocking;
  private boolean manualBlock;
  private int blockStartTick = -1;
  private EntityPlayer currentTarget;
  private int lastSelfHurtTime;

  private boolean isLagging;
  private int lagStartTick = -1;
  private int tickCounter;

  public BlockHit() {
    super("BlockHit", false, false);
  }

  @Override
  public void onEnabled() {
    tickCounter = 0;
    resetState(false);
  }

  @Override
  public void onDisabled() {
    resetState(true);
  }

  private static int msToTicks(double ms) {
    if (ms <= 0.0) return 0;
    return (int) Math.ceil(ms / 50.0);
  }

  @EventTarget
  public void onRightClick(RightClickMouseEvent event) {
    if (shouldBlockVanillaUse()) {
      event.setCancelled(true);
    }
  }

  /** Main per‑tick logic. */
  @EventTarget
  public void onLivingUpdate(LivingUpdateEvent event) {
    if (!checkPreconditions()) {
      resetState(true);
      return;
    }

    if (!ItemUtil.isHoldingSword()) {
      resetState(false);
      return;
    }

    tickCounter++;
    int currentTick = tickCounter;

    // Track hurt‑time changes (for Damaged‑Only mode)
    int selfHurtTime = mc.thePlayer.hurtTime;
    boolean hurtAgain = selfHurtTime > lastSelfHurtTime;
    lastSelfHurtTime = selfHurtTime;

    // Find target
    currentTarget = findTarget(range.getValue() * range.getValue(), ignoreTeammates.getValue());
    boolean killAuraAttacking = isKillAuraAttacking();
    boolean rmbDown = Mouse.isButtonDown(1);
    boolean lmbDown = Mouse.isButtonDown(0) || killAuraAttacking;

    // Must have RMB held
    if (!rmbDown) {
      resetState(true);
      return;
    }

    // ── RMB only (no LMB) → manual blocking ──
    if (!lmbDown) {
      if (isLagging) releaseLag();
      if (!isBlocking) {
        startBlocking(currentTick);
        manualBlock = true;
      }
      return;
    }

    // Transition from manual → auto
    if (manualBlock) {
      stopBlocking(true);
      manualBlock = false;
    }

    boolean hasTarget = currentTarget != null;
    boolean conditionsMet = hasTarget && checkConditions(lmbDown, rmbDown);

    // ── Handle active lag (Lag mode only) ──
    if (isLagging) {
      int lagMaxTicks = msToTicks(lagMaxDuration.getValue());
      boolean lagExpired =
          lagMaxTicks > 0 && lagStartTick >= 0 && currentTick - lagStartTick >= lagMaxTicks;

      if (lagExpired || !conditionsMet) {
        releaseLag();
        if (lagExpired && blockAgainImmediately.getValue() && conditionsMet) {
          startBlocking(currentTick);
        }
      }
    }

    if (!conditionsMet) {
      stopBlocking(true);
      return;
    }

    // ── Start blocking if needed ──
    if (!isBlocking && !isLagging) {
      boolean shouldStart;
      if (onlyWhenDamaged.getValue()) {
        shouldStart = shouldPredictiveBlock();
      } else {
        shouldStart = true;
      }
      if (shouldStart) {
        startBlocking(currentTick);
      }
    }

    // ── Check stop conditions ──
    if (isBlocking) {
      int maxHoldTicks = msToTicks(maxHoldMs.getValue());
      boolean timeExpired =
          maxHoldTicks > 0 && blockStartTick >= 0 && currentTick - blockStartTick >= maxHoldTicks;
      boolean shouldStop = timeExpired;
      if (onlyWhenDamaged.getValue() && hurtAgain) {
        shouldStop = true;
      }
      if (shouldStop) {
        if (shouldStartLag()) {
          startLag(currentTick);
        }
        stopBlocking(true);
      }
    }
  }

  /** Force the block animation every render frame. */
  @EventTarget
  public void onRender3D(Render3DEvent event) {
    if (mc.currentScreen != null && (isBlocking || isLagging)) {
      resetState(true);
      return;
    }
    if (!forceBlockAnimation.getValue() || !ItemUtil.isHoldingSword()) return;
    ((IMixinItemRenderer) mc.getItemRenderer()).setRenderItemInUse(isBlocking || isLagging);
  }

  /** Intercept attack packets during lag. */
  @EventTarget
  public void onPacket(PacketEvent event) {
    if (event.getType() != EventType.SEND) return;
    if (!isLagging || !preventDelayAttacks.getValue()) return;
    if (!(event.getPacket() instanceof C02PacketUseEntity)) return;
    if (((C02PacketUseEntity) event.getPacket()).getAction() != C02PacketUseEntity.Action.ATTACK)
      return;

    releaseLag();
    if (blockAgainImmediately.getValue() && ItemUtil.isHoldingSword()) {
      startBlocking(tickCounter);
    }
  }

  // ── Internal helpers ──────────────────────────────────────────────

  private boolean checkPreconditions() {
    return isEnabled()
        && mc.thePlayer != null
        && !mc.thePlayer.isDead
        && mc.theWorld != null
        && mc.currentScreen == null;
  }

  private boolean checkConditions(boolean lmbDown, boolean rmbDown) {
    if (requireLmb.getValue() && !lmbDown) return false;
    if (requireRmb.getValue() && !rmbDown) return false;
    return true;
  }

  private boolean shouldPredictiveBlock() {
    int ourHurtTime = mc.thePlayer.hurtTime;
    int triggerTick = (int) Math.round(maxHurtTimeMs.getValue() / 50.0);
    triggerTick = Math.max(1, Math.min(10, triggerTick));
    return ourHurtTime >= triggerTick;
  }

  private boolean shouldBlockVanillaUse() {
    return isEnabled()
        && isLagging
        && mc.thePlayer != null
        && mc.theWorld != null
        && ItemUtil.isHoldingSword()
        && mc.currentScreen == null;
  }

  private boolean isKillAuraAttacking() {
    KillAura killAura = (KillAura) Miau.moduleManager.modules.get(KillAura.class);
    return killAura != null
        && killAura.isEnabled()
        && !killAura.requirePress.getValue()
        && killAura.getTarget() != null;
  }

  private void startBlocking(int currentTick) {
    if (!ItemUtil.isHoldingSword()) return;
    int keyCode = mc.gameSettings.keyBindUseItem.getKeyCode();
    KeyBindUtil.setKeyBindState(keyCode, true);
    KeyBindUtil.pressKeyOnce(keyCode);
    isBlocking = true;
    blockStartTick = currentTick;
  }

  private void stopBlocking(boolean forceRelease) {
    if (!isBlocking && !forceRelease) return;
    int keyCode = mc.gameSettings.keyBindUseItem.getKeyCode();
    KeyBindUtil.setKeyBindState(keyCode, false);
    isBlocking = false;
    blockStartTick = -1;
  }

  private boolean shouldStartLag() {
    double chance = lagChance.getValue();
    if (chance <= 0) return false;
    if (chance >= 100) return true;
    return Math.random() * 100 < chance;
  }

  private void startLag(int currentTick) {
    if (isLagging) return;
    int lagReferenceTick = blockStartTick >= 0 ? blockStartTick : currentTick;
    int lagMaxTicks = msToTicks(lagMaxDuration.getValue());
    if (lagMaxTicks > 0 && currentTick - lagReferenceTick >= lagMaxTicks) {
      return;
    }
    Miau.lagManager.setDelay(msToTicks(lagMaxDuration.getValue()));
    isLagging = true;
    lagStartTick = lagReferenceTick;
  }

  private void releaseLag() {
    if (!isLagging) return;
    Miau.lagManager.resetDelay();
    isLagging = false;
    lagStartTick = -1;
  }

  private void resetState(boolean releaseUseKey) {
    boolean wasActive = isBlocking || isLagging;
    releaseLag();
    stopBlocking(releaseUseKey);
    manualBlock = false;
    if (forceBlockAnimation.getValue() && wasActive) {
      ((IMixinItemRenderer) mc.getItemRenderer()).setRenderItemInUse(false);
    }
    // Re‑press the key if the player is still physically holding RMB
    if (Mouse.isButtonDown(1) && mc.currentScreen == null) {
      KeyBindUtil.setKeyBindState(mc.gameSettings.keyBindUseItem.getKeyCode(), true);
    }
    currentTarget = null;
    lastSelfHurtTime = 0;
  }

  /** Simple target finding: mouse‑over first, then closest player. */
  private EntityPlayer findTarget(double maxDistanceSq, boolean ignoreTeammates) {
    // Prefer the entity under the crosshair
    if (mc.objectMouseOver != null
        && mc.objectMouseOver.typeOfHit == MovingObjectPosition.MovingObjectType.ENTITY
        && mc.objectMouseOver.entityHit instanceof EntityPlayer) {
      EntityPlayer player = (EntityPlayer) mc.objectMouseOver.entityHit;
      if (isValidPlayer(player, maxDistanceSq, ignoreTeammates)) {
        return player;
      }
    }

    // Fallback to the closest valid player
    EntityPlayer closest = null;
    double closestDistanceSq = Double.MAX_VALUE;
    for (EntityPlayer player : mc.theWorld.playerEntities) {
      if (!isValidPlayer(player, maxDistanceSq, ignoreTeammates)) {
        continue;
      }
      double distSq = RotationUtil.distanceSqFromEyeToClosestOnAABB(player);
      if (distSq < closestDistanceSq) {
        closestDistanceSq = distSq;
        closest = player;
      }
    }
    return closest;
  }

  private boolean isValidPlayer(
      EntityPlayer player, double maxDistanceSq, boolean ignoreTeammates) {
    if (player == null || player.isDead || player == mc.thePlayer || player.deathTime != 0) {
      return false;
    }
    if (ignoreTeammates && TeamUtil.isSameTeam(player)) {
      return false;
    }
    if (TeamUtil.isFriend(player)) return false;
    double distSq = RotationUtil.distanceSqFromEyeToClosestOnAABB(player);
    return distSq <= maxDistanceSq;
  }

  // ── Module info ───────────────────────────────────────────────────

  @Override
  public String[] getSuffix() {
    if (isLagging) return new String[] {"Lag"};
    if (isBlocking) return new String[] {"Block"};
    return new String[] {"Idle"};
  }
}
