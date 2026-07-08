package miau.module.modules.combat.velocity;

import miau.component.PingSpoofComponent;
import miau.event.impl.KnockbackEvent;
import miau.event.impl.PacketEvent;
import miau.event.impl.UpdateEvent;
import miau.event.types.EventType;
import miau.module.modules.combat.Velocity;
import miau.property.properties.BooleanProperty;
import miau.property.properties.IntProperty;
import miau.property.properties.ModeProperty;
import miau.property.properties.PercentProperty;
import miau.util.time.TimerUtil;
import net.minecraft.network.play.server.S32PacketConfirmTransaction;

public class GrimReduceVelocity extends VelocityMode {

  private final TimerUtil timer = new TimerUtil();
  private boolean sessionActive;
  private int s32CancelTicks;
  private int lastHurtTime;
  private long lastHurtTimestamp;

  // ── Mode ──────────────────────────────────────────────

  public final ModeProperty mode =
      new ModeProperty("Mode", 0, new String[] {"PingSpoof", "Cancel", "Hybrid"});

  // ── PingSpoof / Delay settings ────────────────────────

  public final IntProperty delayMs = new IntProperty("delay-ms", 3000, 100, 15000);

  public final BooleanProperty breakTransaction = new BooleanProperty("break-transaction", true);

  // ── S32 cancel settings ──────────────────────────────

  public final IntProperty s32CancelTicksProp = new IntProperty("s32-cancel-ticks", 6, 0, 20);

  // ── Reduction ─────────────────────────────────────────

  public final PercentProperty horizontal = new PercentProperty("horizontal", 0);

  public final PercentProperty vertical = new PercentProperty("vertical", 0);

  public final PercentProperty chance = new PercentProperty("chance", 100);

  public final BooleanProperty onSwingRequired = new BooleanProperty("require-swing", false);

  // ── Extra ─────────────────────────────────────────────

  public final IntProperty safetyTimeoutMs = new IntProperty("safety-timeout", 15000, 5000, 60000);

  // ──────────────────────────────────────────────────────

  public GrimReduceVelocity(String name, Velocity parent) {
    super(name, parent);
  }

  @Override
  public void onEnable() {
    sessionActive = false;
    s32CancelTicks = 0;
    lastHurtTime = 0;
    lastHurtTimestamp = 0;
    timer.reset();
  }

  @Override
  public void onDisable() {
    killSession();
  }

  // ═══════════════════════════════════════════════════════
  //  KNOCKBACK EVENT  –  client-side velocity modification
  // ═══════════════════════════════════════════════════════

  @Override
  public void onKnockback(KnockbackEvent event) {
    if (bypassCheck()) return;
    if (Math.random() * 100 >= chance.getValue()) return;

    int horz = horizontal.getValue();
    int vert = vertical.getValue();

    if (horz == 0 && vert == 0) {
      // Full cancel – player takes absolutely no KB
      event.setX(0);
      event.setY(0);
      event.setZ(0);
    } else {
      // Partial reduction
      event.setX(event.getX() * horz / 100.0);
      event.setY(event.getY() * vert / 100.0);
      event.setZ(event.getZ() * horz / 100.0);
    }
  }

  // ═══════════════════════════════════════════════════════
  //  PACKET EVENT  –  cancel S32 to break Grim's sandwich
  // ═══════════════════════════════════════════════════════

  @Override
  public void onPacket(PacketEvent event) {
    if (event.getType() != EventType.RECEIVE) return;
    if (bypassCheck()) return;

    // Cancel incoming server transaction (S32).
    // Without S32, the client never sends C0F back to the server.
    // Grim's bread1 never confirms → firstBreadKB stays null → no KB check!
    if (event.getPacket() instanceof S32PacketConfirmTransaction && s32CancelTicks > 0) {
      event.setCancelled(true);
      s32CancelTicks--;
    }
  }

  // ═══════════════════════════════════════════════════════
  //  UPDATE  –  detect damage, start sessions
  // ═══════════════════════════════════════════════════════

  @Override
  public void onUpdate(UpdateEvent event) {
    if (event.getType() != EventType.PRE) return;
    if (bypassCheck()) return;

    int hurtTime = mc.thePlayer.hurtTime;
    boolean justGotHurt = hurtTime > lastHurtTime && hurtTime > 0;

    if (justGotHurt) {
      int modeVal = mode.getValue();
      boolean usePingSpoof = modeVal == 0 || modeVal == 2;
      boolean useCancel = modeVal == 1 || modeVal == 2;

      if (usePingSpoof) {
        startPingSpoofSession();
      }
      if (useCancel) {
        s32CancelTicks = s32CancelTicksProp.getValue();
      }
      lastHurtTimestamp = System.currentTimeMillis();
    }

    // ── End session when hurt is over ──
    if (sessionActive) {
      if (hurtTime <= 0 && lastHurtTime > 0) {
        // Hurt just ended – flush everything
        killSession();
      } else {
        // Safety: force-kill if we've been running way too long
        long elapsed = System.currentTimeMillis() - lastHurtTimestamp;
        if (elapsed >= safetyTimeoutMs.getValue()) {
          killSession();
        }
      }
    }

    lastHurtTime = hurtTime;
  }

  // ═══════════════════════════════════════════════════════
  //  INTERNALS
  // ═══════════════════════════════════════════════════════

  /**
   * @return true when the mode should stay quiet (not bypass this tick).
   */
  private boolean bypassCheck() {
    if (onSwingRequired.getValue() && !mc.thePlayer.isSwingInProgress) return true;
    if (mc.thePlayer.ticksExisted <= 20) return true;
    return false;
  }

  /**
   * Start a PingSpoof capture session. Captured packets: - C0FPacketConfirmTransaction (regular) –
   * prevents Grim from confirming bread - S12PacketEntityVelocity (velocity) – delays KB from
   * reaching the client - S27PacketExplosion (velocity) – same for explosion KB
   *
   * <p>After {@code delayMs} ms, the session auto-dispatches via PingSpoofComponent's internal
   * timer (UpdateEvent POST handler). C0F arrives late → bread stale. S12 arrives late → client KB
   * delayed → prediction engine already checked that tick without KB in the set.
   */
  private synchronized void startPingSpoofSession() {
    // Flush any stale session first
    if (sessionActive) {
      PingSpoofComponent.dispatch();
      PingSpoofComponent.disable();
      sessionActive = false;
    }

    int delay = delayMs.getValue();
    if (delay <= 0) return;

    PingSpoofComponent.spoof(
        delay,
        breakTransaction.getValue(), // regular (C0F + keepalive)
        true, // velocity (S12 + S27)
        false, // teleports
        false, // players
        false, // blink
        false // movement
        );
    PingSpoofComponent.enabled = true;
    sessionActive = true;
    timer.reset();
  }

  private synchronized void killSession() {
    if (sessionActive) {
      PingSpoofComponent.dispatch();
      PingSpoofComponent.disable();
      sessionActive = false;
    }
    s32CancelTicks = 0;
    timer.reset();
  }
}
