package myau.clientanticheat.combat.autoblock;

import java.util.HashMap;
import java.util.Map;
import myau.clientanticheat.CheckBuffer;
import myau.clientanticheat.ClientAntiCheatContext;
import myau.clientanticheat.PlayerCheckData;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemSword;

public class AutoBlockCheck {
  private final Map<String, Long> guardingTicks = new HashMap<>();
  private final Map<String, Long> lastBlockStart = new HashMap<>();
  private final Map<String, CheckBuffer> attackWhileBlockingBuffers = new HashMap<>();
  private final Map<String, CheckBuffer> sprintBlockBuffers = new HashMap<>();
  private final Map<String, CheckBuffer> rapidToggleBuffers = new HashMap<>();
  private final Map<String, CheckBuffer> impossibleBlockBuffers = new HashMap<>();
  private final Map<String, CheckBuffer> instantUnblockBuffers = new HashMap<>();
  private final Map<String, CheckBuffer> multiInteractionBuffers = new HashMap<>();
  private final Map<String, CheckBuffer> toggleTimingBuffers = new HashMap<>();
  private final Map<String, CheckBuffer> sprintToggleBuffers = new HashMap<>();
  private final Map<String, CheckBuffer> itemOperationBuffers = new HashMap<>();
  private final Map<String, Integer> ticksSinceBlockStart = new HashMap<>();
  private final Map<String, Integer> consecutiveToggleCount = new HashMap<>();
  private final Map<String, Boolean> wasBlockingLastTick = new HashMap<>();
  private final Map<String, Long> lastUnblockTick = new HashMap<>();

  public void check(
      EntityPlayer player, PlayerCheckData data, long currentTick, ClientAntiCheatContext context) {
    String name = player.getName();
    if (name == null) return;

    ItemStack heldItem = player.getHeldItem();
    boolean holdingSword = heldItem != null && heldItem.getItem() instanceof ItemSword;
    boolean blocking = player.isBlocking();
    boolean guarding = holdingSword && blocking;
    boolean attacking =
        data != null
            ? data.startedSwinging()
            : player.swingProgress > 0.0F && player.prevSwingProgress == 0.0F;
    double horizontalSpeed =
        data != null
            ? data.horizontalDelta
            : Math.sqrt(player.motionX * player.motionX + player.motionZ * player.motionZ);

    CheckBuffer attackBuffer =
        this.attackWhileBlockingBuffers.computeIfAbsent(name, key -> new CheckBuffer());
    CheckBuffer sprintBuffer =
        this.sprintBlockBuffers.computeIfAbsent(name, key -> new CheckBuffer());
    CheckBuffer rapidBuffer =
        this.rapidToggleBuffers.computeIfAbsent(name, key -> new CheckBuffer());
    CheckBuffer impossibleBuffer =
        this.impossibleBlockBuffers.computeIfAbsent(name, key -> new CheckBuffer());
    CheckBuffer instantUnblockBuffer =
        this.instantUnblockBuffers.computeIfAbsent(name, key -> new CheckBuffer());
    CheckBuffer multiInteractionBuffer =
        this.multiInteractionBuffers.computeIfAbsent(name, key -> new CheckBuffer());
    CheckBuffer toggleTimingBuffer =
        this.toggleTimingBuffers.computeIfAbsent(name, key -> new CheckBuffer());
    CheckBuffer sprintToggleBuffer =
        this.sprintToggleBuffers.computeIfAbsent(name, key -> new CheckBuffer());
    CheckBuffer itemOpBuffer =
        this.itemOperationBuffers.computeIfAbsent(name, key -> new CheckBuffer());

    boolean wasBlocking = this.wasBlockingLastTick.getOrDefault(name, false);
    this.wasBlockingLastTick.put(name, blocking);

    if (!holdingSword && blocking) {
      if (impossibleBuffer.flag(1.5D, 3.0D)) {
        context.receiveSignal(name, "AutoBlock (Impossible)");
        impossibleBuffer.reset();
      }
      return;
    }

    if (data != null && data.sprintToggleCount > 1) {
      if (sprintToggleBuffer.flag(1.5D, 4.0D)) {
        context.receiveSignal(name, "AutoBlock (Sprint Toggle)");
        sprintToggleBuffer.reset();
      }
    } else {
      sprintToggleBuffer.decay(0.15D);
    }

    if (data != null && data.heldItemChangeTicks > 2 && guarding) {
      if (itemOpBuffer.flag(1.0D, 3.5D)) {
        context.receiveSignal(name, "AutoBlock (Item Operations)");
        itemOpBuffer.reset();
      }
    } else {
      itemOpBuffer.decay(0.2D);
    }

    if (guarding) {
      Long previousStart = this.guardingTicks.putIfAbsent(name, currentTick);
      if (previousStart == null) {
        long lastStart = this.lastBlockStart.getOrDefault(name, -100L);

        if (currentTick - lastStart <= 3L) {
          if (rapidBuffer.flag(1.0D, 3.0D)) {
            context.receiveSignal(name, "AutoBlock (Rapid Toggle)");
            rapidBuffer.reset();
          }
        }

        int consecutiveToggles = this.consecutiveToggleCount.getOrDefault(name, 0);
        long lastUnblock = this.lastUnblockTick.getOrDefault(name, -100L);
        long ticksBetweenToggle = currentTick - lastUnblock;

        if (ticksBetweenToggle <= 1L) {
          consecutiveToggles++;
          this.consecutiveToggleCount.put(name, consecutiveToggles);

          if (consecutiveToggles > 2) {
            if (toggleTimingBuffer.flag(1.5D, 3.0D)) {
              context.receiveSignal(name, "AutoBlock (Toggle Timing)");
              toggleTimingBuffer.reset();
            }
          }
        } else {
          this.consecutiveToggleCount.put(name, Math.max(0, consecutiveToggles - 1));
          toggleTimingBuffer.decay(0.2D);
        }

        this.lastBlockStart.put(name, currentTick);
        this.ticksSinceBlockStart.put(name, 0);
      }

      int ticksGuarded = this.ticksSinceBlockStart.getOrDefault(name, 0);
      this.ticksSinceBlockStart.put(name, ticksGuarded + 1);

      if (attacking && ticksGuarded > 1) {
        if (attackBuffer.flag(1.25D, 3.5D)) {
          context.receiveSignal(name, "AutoBlock (Attack)");
          attackBuffer.reset();
        }
      } else {
        attackBuffer.decay(0.35D);
      }

      if (player.isSprinting() && horizontalSpeed > 0.17D && ticksGuarded > 3) {
        if (sprintBuffer.flag(1.0D, 5.0D)) {
          context.receiveSignal(name, "AutoBlock (Sprint)");
          sprintBuffer.reset();
        }
      } else {
        sprintBuffer.decay(0.25D);
      }
    } else {
      if (wasBlocking) {
        int ticksWereBlocking = this.ticksSinceBlockStart.getOrDefault(name, 99);
        if (ticksWereBlocking <= 1) {
          if (instantUnblockBuffer.flag(1.5D, 3.0D)) {
            context.receiveSignal(name, "AutoBlock (Instant Unblock)");
            instantUnblockBuffer.reset();
          }
        } else {
          instantUnblockBuffer.decay(0.3D);
        }

        this.lastUnblockTick.put(name, currentTick);
      }

      if (wasBlocking && blocking) {
        if (multiInteractionBuffer.flag(2.0D, 3.0D)) {
          context.receiveSignal(name, "AutoBlock (Multi-Interaction)");
          multiInteractionBuffer.reset();
        }
      } else {
        multiInteractionBuffer.decay(0.15D);
      }

      this.guardingTicks.remove(name);
      this.ticksSinceBlockStart.remove(name);
      attackBuffer.decay(0.45D);
      sprintBuffer.decay(0.35D);
      rapidBuffer.decay(0.15D);
    }
  }

  public void reset() {
    this.guardingTicks.clear();
    this.lastBlockStart.clear();
    this.attackWhileBlockingBuffers.clear();
    this.sprintBlockBuffers.clear();
    this.rapidToggleBuffers.clear();
    this.impossibleBlockBuffers.clear();
    this.instantUnblockBuffers.clear();
    this.multiInteractionBuffers.clear();
    this.toggleTimingBuffers.clear();
    this.sprintToggleBuffers.clear();
    this.itemOperationBuffers.clear();
    this.ticksSinceBlockStart.clear();
    this.consecutiveToggleCount.clear();
    this.wasBlockingLastTick.clear();
    this.lastUnblockTick.clear();
  }
}
