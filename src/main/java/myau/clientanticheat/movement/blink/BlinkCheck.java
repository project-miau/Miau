package myau.clientanticheat.movement.blink;

import java.util.HashMap;
import java.util.Map;
import myau.clientanticheat.CheckBuffer;
import myau.clientanticheat.ClientAntiCheatContext;
import myau.clientanticheat.PlayerCheckData;
import net.minecraft.entity.player.EntityPlayer;

public class BlinkCheck {
  private final Map<String, Integer> frozenTicks = new HashMap<>();
  private final Map<String, CheckBuffer> burstBuffers = new HashMap<>();
  private final Map<String, CheckBuffer> pulseBuffers = new HashMap<>();
  private final Map<String, CheckBuffer> timerBuffers = new HashMap<>();
  private final Map<String, long[]> timerBalanceData = new HashMap<>();
  private final Map<String, CheckBuffer> blinkLimitBuffers = new HashMap<>();

  private static final long FIFTY_MS_NANOS = 50_000_000L;
  private static final long CLOCK_ERROR_NANOS = 1_000_000L;
  private static final long TIMER_OVERFLOW_LIMIT = 80_000_000L;
  private static final int BLINK_TICK_LIMIT = 60;

  public void check(EntityPlayer player, PlayerCheckData data, ClientAntiCheatContext context) {
    String name = player.getName();
    if (name == null || data == null) return;
    CheckBuffer burstBuffer = this.burstBuffers.computeIfAbsent(name, key -> new CheckBuffer());
    CheckBuffer pulseBuffer = this.pulseBuffers.computeIfAbsent(name, key -> new CheckBuffer());
    CheckBuffer timerBuffer = this.timerBuffers.computeIfAbsent(name, key -> new CheckBuffer());
    CheckBuffer blinkLimitBuffer =
        this.blinkLimitBuffers.computeIfAbsent(name, key -> new CheckBuffer());

    if (isExempt(player, data)) {
      this.frozenTicks.remove(name);
      burstBuffer.decay(0.5D);
      pulseBuffer.decay(0.5D);
      timerBuffer.decay(0.5D);
      blinkLimitBuffer.decay(0.5D);
      this.timerBalanceData.remove(name);
      return;
    }

    long[] balanceState =
        this.timerBalanceData.computeIfAbsent(name, key -> new long[] {System.nanoTime(), 0L, 0L});

    long now = System.nanoTime();
    long delta = now - balanceState[0];
    balanceState[0] = now;

    balanceState[1] += FIFTY_MS_NANOS + CLOCK_ERROR_NANOS - delta;

    long maxLagNanos = (long) BLINK_TICK_LIMIT * FIFTY_MS_NANOS;
    balanceState[1] = Math.max(-maxLagNanos, Math.min(balanceState[1], FIFTY_MS_NANOS * 20));

    if (balanceState[1] > TIMER_OVERFLOW_LIMIT) {
      double ticksAhead = (double) balanceState[1] / FIFTY_MS_NANOS;
      double vl = Math.min(Math.max(ticksAhead * 2.0, 1.0), 5.0);
      if (timerBuffer.flag(vl, 4.0D)) {
        context.receiveSignal(name, "Blink (Timer)");
        timerBuffer.reset();
      }
      balanceState[1] -= FIFTY_MS_NANOS / 5;
    } else {
      timerBuffer.decay(0.05D);
    }

    if (balanceState[1] < -(long) BLINK_TICK_LIMIT * FIFTY_MS_NANOS / 2) {
      double ticksBehind = (double) -balanceState[1] / FIFTY_MS_NANOS;
      if (blinkLimitBuffer.flag(1.5D, 3.0D)) {
        context.receiveSignal(name, "Blink (Limit)");
        blinkLimitBuffer.reset();
      }
    } else {
      blinkLimitBuffer.decay(0.1D);
    }

    boolean frozen = data.totalDelta < 0.002D && data.yawDelta < 0.03F && data.pitchDelta < 0.03F;
    if (frozen) {
      this.frozenTicks.put(name, this.frozenTicks.getOrDefault(name, 0) + 1);
      burstBuffer.decay(0.1D);
      return;
    }

    int frozenBefore = this.frozenTicks.getOrDefault(name, 0);
    this.frozenTicks.remove(name);

    boolean catchUpBurst = frozenBefore >= 8 && data.totalDelta > 0.65D && data.totalDelta < 8.0D;
    boolean pulseMove =
        data.totalDelta > 1.15D && data.totalDelta < 8.0D && data.horizontalDelta > 0.8D;
    boolean heavyPulse = frozenBefore >= 3 && data.totalDelta > 0.95D && data.totalDelta < 8.0D;

    if (catchUpBurst) {
      if (burstBuffer.flag(1.5D + Math.min(1.0D, frozenBefore / 20.0D), 3.0D)) {
        context.receiveSignal(name, "Blink");
        burstBuffer.reset();
        pulseBuffer.reset();
      }
    } else {
      burstBuffer.decay(0.25D);
    }

    if (pulseMove && !player.isCollidedHorizontally && data.airTicks < 8) {
      if (pulseBuffer.flag(1.0D, 3.5D)) {
        context.receiveSignal(name, "Blink");
        pulseBuffer.reset();
      }
    } else if (heavyPulse) {
      if (pulseBuffer.flag(1.25D, 3.0D)) {
        context.receiveSignal(name, "Pulse Blink");
        pulseBuffer.reset();
      }
    } else {
      pulseBuffer.decay(0.2D);
    }
  }

  private boolean isExempt(EntityPlayer player, PlayerCheckData data) {
    return player.isDead
        || player.ticksExisted < 40
        || data.recentlyTeleported()
        || player.isInWater()
        || player.isInLava()
        || player.isOnLadder()
        || player.isRiding()
        || player.capabilities.isFlying;
  }

  public void reset() {
    this.frozenTicks.clear();
    this.burstBuffers.clear();
    this.pulseBuffers.clear();
    this.timerBuffers.clear();
    this.timerBalanceData.clear();
    this.blinkLimitBuffers.clear();
  }
}
