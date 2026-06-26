package myau.clientanticheat.combat.autoclicker;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;
import myau.clientanticheat.CheckBuffer;
import myau.clientanticheat.ClientAntiCheatContext;
import myau.clientanticheat.PlayerCheckData;
import myau.clientanticheat.StatisticalUtils;
import net.minecraft.entity.player.EntityPlayer;

public class ClickSpeedCheck {
  private final Map<String, LinkedList<Long>> clickTimestamps = new HashMap<>();
  private final Map<String, LinkedList<Long>> clickIntervals = new HashMap<>();

  private final Map<String, CheckBuffer> deviationBuffers = new HashMap<>();
  private final Map<String, CheckBuffer> entropyBuffers = new HashMap<>();
  private final Map<String, CheckBuffer> kurtosisBuffers = new HashMap<>();
  private final Map<String, CheckBuffer> fluctuationBuffers = new HashMap<>();
  private final Map<String, CheckBuffer> repetitiveBuffers = new HashMap<>();
  private final Map<String, CheckBuffer> equalDelayBuffers = new HashMap<>();
  private final Map<String, CheckBuffer> cpsBuffers = new HashMap<>();

  private final Map<String, LinkedList<Integer>> cpsWindowHistory = new HashMap<>();
  private final Map<String, LinkedList<Integer>> fluctuationCpsValues = new HashMap<>();
  private final Map<String, LinkedList<Long>> spikeTimestamps = new HashMap<>();

  private final Map<String, LinkedList<Double>> intervalDifferenceHistory = new HashMap<>();

  private final Map<String, Integer> consecutiveEqualDelays = new HashMap<>();
  private final Map<String, Long> lastDelayTick = new HashMap<>();

  private static final int CLICK_SAMPLE_SIZE = 100;
  private static final int STDDEV_COLLECTION_SIZE = 20;
  private static final int FLUCTUATION_WINDOW_SIZE = 5;
  private static final int CPS_WINDOW_TICKS = 20;
  private static final int MIN_CLICKS_FOR_ANALYSIS = 8;
  private static final long EQUAL_DELAY_TOLERANCE = 1L;

  public void check(
      EntityPlayer player, PlayerCheckData data, long currentTick, ClientAntiCheatContext context) {
    if (data == null || !data.startedSwinging()) {
      return;
    }

    String name = player.getName();
    if (name == null) return;

    LinkedList<Long> timestamps =
        this.clickTimestamps.computeIfAbsent(name, k -> new LinkedList<>());
    LinkedList<Long> intervals = this.clickIntervals.computeIfAbsent(name, k -> new LinkedList<>());
    LinkedList<Integer> cpsHistory =
        this.cpsWindowHistory.computeIfAbsent(name, k -> new LinkedList<>());

    CheckBuffer deviationBuffer =
        this.deviationBuffers.computeIfAbsent(name, k -> new CheckBuffer());
    CheckBuffer entropyBuffer = this.entropyBuffers.computeIfAbsent(name, k -> new CheckBuffer());
    CheckBuffer kurtosisBuffer = this.kurtosisBuffers.computeIfAbsent(name, k -> new CheckBuffer());
    CheckBuffer fluctuationBuffer =
        this.fluctuationBuffers.computeIfAbsent(name, k -> new CheckBuffer());
    CheckBuffer repetitiveBuffer =
        this.repetitiveBuffers.computeIfAbsent(name, k -> new CheckBuffer());
    CheckBuffer equalDelayBuffer =
        this.equalDelayBuffers.computeIfAbsent(name, k -> new CheckBuffer());
    CheckBuffer cpsBuffer = this.cpsBuffers.computeIfAbsent(name, k -> new CheckBuffer());

    timestamps.addFirst(currentTick);
    while (timestamps.size() > CLICK_SAMPLE_SIZE) {
      timestamps.removeLast();
    }

    if (timestamps.size() >= 2) {
      long interval = timestamps.get(0) - timestamps.get(1);
      if (interval > 0) {
        intervals.addFirst(interval);
        while (intervals.size() > CLICK_SAMPLE_SIZE) {
          intervals.removeLast();
        }
      }
    }

    int cps = 0;
    long cutoff = currentTick - CPS_WINDOW_TICKS;
    for (long ts : timestamps) {
      if (ts >= cutoff) cps++;
    }
    cpsHistory.addFirst(cps);
    while (cpsHistory.size() > 50) {
      cpsHistory.removeLast();
    }

    if (intervals.size() < MIN_CLICKS_FOR_ANALYSIS) {
      return;
    }

    double stddev = StatisticalUtils.standardDeviation(intervals);
    if (stddev < 1.5D && intervals.size() > 10) {
      if (deviationBuffer.flag(2.0D, 5.0D)) {
        context.receiveSignal(name, "AutoClicker (Deviation)");
        deviationBuffer.reset();
      }
    } else {
      deviationBuffer.decay(0.15D);
    }

    LinkedList<Long> entropySamples = intervals;
    if (entropySamples.size() >= 10) {
      double ent = StatisticalUtils.entropy(intervals);
      if (ent < 1.5D && intervals.size() >= 15) {
        if (entropyBuffer.flag(1.5D, 4.0D)) {
          context.receiveSignal(name, "AutoClicker (Entropy)");
          entropyBuffer.reset();
        }
      } else {
        entropyBuffer.decay(0.2D);
      }
    }

    if (intervals.size() >= 20) {
      double kurt = StatisticalUtils.kurtosis(intervals);
      if (kurt < -0.8D && kurt >= -2.0D) {
        if (kurtosisBuffer.flag(1.5D, 5.0D)) {
          context.receiveSignal(name, "AutoClicker (Kurtosis)");
          kurtosisBuffer.reset();
        }
      } else if (kurt > 3.0D) {
        if (kurtosisBuffer.flag(1.0D, 5.0D)) {
          context.receiveSignal(name, "AutoClicker (Kurtosis Spiky)");
          kurtosisBuffer.reset();
        }
      } else {
        kurtosisBuffer.decay(0.2D);
      }
    }

    LinkedList<Integer> fluctuationValues =
        this.fluctuationCpsValues.computeIfAbsent(name, k -> new LinkedList<>());
    LinkedList<Long> spikeTsList =
        this.spikeTimestamps.computeIfAbsent(name, k -> new LinkedList<>());

    if (cpsHistory.size() >= FLUCTUATION_WINDOW_SIZE) {
      boolean isSpike = true;
      int currentCps = cpsHistory.getFirst();
      for (int i = 1; i < FLUCTUATION_WINDOW_SIZE && i < cpsHistory.size(); i++) {
        if (Math.abs(cpsHistory.get(i) - currentCps) > 3) {
          isSpike = false;
          break;
        }
      }

      if (isSpike && currentCps > 5) {
        fluctuationValues.addFirst(currentCps);
        spikeTsList.addFirst(currentTick);
        while (fluctuationValues.size() > 20) {
          fluctuationValues.removeLast();
        }
        while (spikeTsList.size() > 20) {
          spikeTsList.removeLast();
        }
      }

      if (fluctuationValues.size() >= 8) {
        double fluctuationStdDev = StatisticalUtils.standardDeviation(fluctuationValues);
        if (fluctuationStdDev < 1.5D) {
          if (fluctuationBuffer.flag(1.5D, 4.0D)) {
            context.receiveSignal(name, "AutoClicker (Fluctuation)");
            fluctuationBuffer.reset();
          }
        } else {
          fluctuationBuffer.decay(0.15D);
        }
      }
    }

    LinkedList<Double> diffHistory =
        this.intervalDifferenceHistory.computeIfAbsent(name, k -> new LinkedList<>());

    if (intervals.size() >= 4) {
      for (int i = 0; i < intervals.size() - 1 && diffHistory.size() < 50; i++) {
        if (i >= diffHistory.size()) {
          double diff = Math.abs((double) (intervals.get(i) - intervals.get(i + 1)));
          diffHistory.addFirst(diff);
        }
      }
      while (diffHistory.size() > 50) {
        diffHistory.removeLast();
      }

      if (diffHistory.size() >= 10) {
        if (StatisticalUtils.hasRepetitivePattern(diffHistory, 0.5D)) {
          if (repetitiveBuffer.flag(1.5D, 4.0D)) {
            context.receiveSignal(name, "AutoClicker (Repetitive)");
            repetitiveBuffer.reset();
          }
        } else {
          repetitiveBuffer.decay(0.15D);
        }
      }
    }

    if (intervals.size() >= 2) {
      long currentDelay = intervals.getFirst();
      Long lastDelay = this.lastDelayTick.get(name);

      if (lastDelay != null && lastDelay >= 0) {
        long diff = Math.abs(currentDelay - lastDelay);
        if (diff <= EQUAL_DELAY_TOLERANCE && currentDelay > 0 && currentDelay < 10) {
          int consecutive = this.consecutiveEqualDelays.getOrDefault(name, 0) + 1;
          this.consecutiveEqualDelays.put(name, consecutive);

          if (consecutive > 8) {
            if (equalDelayBuffer.flag(2.0D, 4.0D)) {
              context.receiveSignal(name, "AutoClicker (Equal Delay)");
              equalDelayBuffer.reset();
              this.consecutiveEqualDelays.put(name, 0);
            }
          }
        } else {
          int consecutive = this.consecutiveEqualDelays.getOrDefault(name, 0);
          if (consecutive > 0) {
            this.consecutiveEqualDelays.put(name, Math.max(0, consecutive - 1));
          }
          equalDelayBuffer.decay(0.1D);
        }
      }

      this.lastDelayTick.put(name, currentDelay);
    }

    if (cps > 20) {
      if (cpsBuffer.flag(1.0D, 3.0D)) {
        context.receiveSignal(name, "AutoClicker (CPS)");
        cpsBuffer.reset();
      }
    } else {
      cpsBuffer.decay(0.2D);
    }
  }

  public void reset() {
    this.clickTimestamps.clear();
    this.clickIntervals.clear();
    this.deviationBuffers.clear();
    this.entropyBuffers.clear();
    this.kurtosisBuffers.clear();
    this.fluctuationBuffers.clear();
    this.repetitiveBuffers.clear();
    this.equalDelayBuffers.clear();
    this.cpsBuffers.clear();
    this.cpsWindowHistory.clear();
    this.fluctuationCpsValues.clear();
    this.spikeTimestamps.clear();
    this.intervalDifferenceHistory.clear();
    this.consecutiveEqualDelays.clear();
    this.lastDelayTick.clear();
  }
}
