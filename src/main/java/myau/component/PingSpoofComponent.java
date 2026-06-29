package myau.component;

import java.util.Arrays;
import java.util.Iterator;
import java.util.concurrent.ConcurrentLinkedQueue;
import myau.event.EventTarget;
import myau.event.impl.LoadWorldEvent;
import myau.event.impl.PacketEvent;
import myau.event.impl.UpdateEvent;
import myau.event.types.EventType;
import myau.util.network.PacketUtil;
import myau.util.time.TimerUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiDownloadTerrain;
import net.minecraft.network.Packet;
import net.minecraft.network.play.client.*;
import net.minecraft.network.play.server.*;

public final class PingSpoofComponent {

  private static final Minecraft mc = Minecraft.getMinecraft();

  public static ConcurrentLinkedQueue<TimedPacket> packets = new ConcurrentLinkedQueue<>();
  static TimerUtil enabledTimer = new TimerUtil();
  public static boolean enabled;
  static long amount;

  /** Non-null when an external module is managing the capture session lifecycle. */
  private static String sessionOwner = null;

  static Class<?>[] regular = {
    C0FPacketConfirmTransaction.class, C00PacketKeepAlive.class, S1CPacketEntityMetadata.class
  };
  static Class<?>[] velocity = {S12PacketEntityVelocity.class, S27PacketExplosion.class};
  static Class<?>[] teleports = {
    S08PacketPlayerPosLook.class, S39PacketPlayerAbilities.class, S09PacketHeldItemChange.class
  };
  static Class<?>[] players = {
    S13PacketDestroyEntities.class,
    S14PacketEntity.class,
    S14PacketEntity.S16PacketEntityLook.class,
    S14PacketEntity.S15PacketEntityRelMove.class,
    S14PacketEntity.S17PacketEntityLookMove.class,
    S18PacketEntityTeleport.class,
    S20PacketEntityProperties.class,
    S19PacketEntityHeadLook.class
  };
  static Class<?>[] blink = {
    C02PacketUseEntity.class,
    C0DPacketCloseWindow.class,
    C0EPacketClickWindow.class,
    C0CPacketInput.class,
    C0BPacketEntityAction.class,
    C08PacketPlayerBlockPlacement.class,
    C07PacketPlayerDigging.class,
    C09PacketHeldItemChange.class,
    C13PacketPlayerAbilities.class,
    C15PacketClientSettings.class,
    C16PacketClientStatus.class,
    C17PacketCustomPayload.class,
    C18PacketSpectate.class,
    C19PacketResourcePackStatus.class,
    C03PacketPlayer.class,
    C03PacketPlayer.C04PacketPlayerPosition.class,
    C03PacketPlayer.C05PacketPlayerLook.class,
    C03PacketPlayer.C06PacketPlayerPosLook.class,
    C0APacketAnimation.class
  };
  static Class<?>[] movement = {
    C03PacketPlayer.class,
    C03PacketPlayer.C04PacketPlayerPosition.class,
    C03PacketPlayer.C05PacketPlayerLook.class,
    C03PacketPlayer.C06PacketPlayerPosLook.class
  };

  static boolean regularEnabled,
      velocityEnabled,
      teleportsEnabled,
      playersEnabled,
      blinkEnabled,
      movementEnabled;

  @EventTarget
  public void onPacket(PacketEvent event) {
    Packet<?> packet = event.getPacket();

    if (!event.isCancelled() && enabled) {
      boolean shouldCancel = false;

      if (regularEnabled && Arrays.stream(regular).anyMatch(c -> c == packet.getClass())) {
        shouldCancel = true;
      } else if (velocityEnabled && Arrays.stream(velocity).anyMatch(c -> c == packet.getClass())) {
        shouldCancel = true;
      } else if (teleportsEnabled
          && Arrays.stream(teleports).anyMatch(c -> c == packet.getClass())) {
        shouldCancel = true;
      } else if (playersEnabled && Arrays.stream(players).anyMatch(c -> c == packet.getClass())) {
        shouldCancel = true;
      } else if (blinkEnabled && Arrays.stream(blink).anyMatch(c -> c == packet.getClass())) {
        shouldCancel = true;
      } else if (movementEnabled && Arrays.stream(movement).anyMatch(c -> c == packet.getClass())) {
        shouldCancel = true;
      }

      if (shouldCancel) {
        event.setCancelled(true);
        packets.add(new TimedPacket(packet));
      }
    }
  }

  public static void dispatch() {
    if (!packets.isEmpty()) {
      boolean wasEnabled = enabled;
      enabled = false;
      for (TimedPacket timedPacket : packets) {
        queuePacket(timedPacket.getPacket());
      }
      enabled = wasEnabled;
      packets.clear();
    }
  }

  private static void queuePacket(Packet<?> packet) {
    String className = packet.getClass().getName();
    if (className.startsWith("net.minecraft.network.play.server")) {
      // Server packet -> process through handler
      @SuppressWarnings("unchecked")
      Packet<net.minecraft.network.play.INetHandlerPlayClient> serverPacket =
          (Packet<net.minecraft.network.play.INetHandlerPlayClient>) packet;
      PacketUtil.handlePacket(serverPacket);
    } else {
      // Client packet -> send to server
      if (mc.getNetHandler() != null && mc.getNetHandler().getNetworkManager() != null) {
        mc.getNetHandler().getNetworkManager().sendPacket(packet, null);
      }
    }
  }

  public static void disable() {
    enabled = false;
    enabledTimer.setTime(System.currentTimeMillis() - 999999999L);
  }

  // ────────── Session management API ───────────────────────────────────

  /**
   * Start a named capture session. The caller is responsible for calling {@link #finishSession}.
   * While a session is active the timer-based auto-disable in onUpdate is bypassed.
   */
  public static void beginSession(
      String owner,
      int amount,
      boolean regular,
      boolean velocity,
      boolean teleports,
      boolean players,
      boolean blink,
      boolean movement) {
    // Flush any stale session from a different owner
    if (sessionOwner != null && !sessionOwner.equals(owner)) {
      finishSession(sessionOwner, true);
    }
    // Flush leftover packets from a prior session of the same owner
    if (sessionOwner != null && sessionOwner.equals(owner) && !packets.isEmpty()) {
      finishSession(owner, true);
    }

    enabledTimer.reset();
    PingSpoofComponent.regularEnabled = regular;
    PingSpoofComponent.velocityEnabled = velocity;
    PingSpoofComponent.teleportsEnabled = teleports;
    PingSpoofComponent.playersEnabled = players;
    PingSpoofComponent.blinkEnabled = blink;
    PingSpoofComponent.movementEnabled = movement;
    PingSpoofComponent.amount = amount;
    enabled = true;
    sessionOwner = owner;
  }

  /**
   * End a named capture session. If {@code flush} is true all remaining queued packets are sent
   * before capture is disabled. Has no effect if {@code owner} does not match.
   */
  public static void finishSession(String owner, boolean flush) {
    if (!owner.equals(sessionOwner)) return;
    if (flush && !packets.isEmpty()) {
      enabled = false;
      for (TimedPacket timedPacket : packets) {
        queuePacket(timedPacket.getPacket());
      }
      packets.clear();
    }
    disable();
    sessionOwner = null;
  }

  /** Returns true if the current session is owned by {@code owner}. */
  public static boolean isOwnedBy(String owner) {
    return owner != null && owner.equals(sessionOwner);
  }

  /** Returns the number of packets currently queued (for diagnostics). */
  public static int getQueuedPacketCount() {
    return packets.size();
  }

  @EventTarget
  public void onWorldLoad(LoadWorldEvent event) {
    dispatch();
  }

  @EventTarget
  public void onUpdate(UpdateEvent event) {
    if (event.getType() != EventType.POST) return;

    if (sessionOwner != null) {
      // Externally managed session - only flush expired packets, do NOT auto-disable.
      boolean wasEnabled = enabled;
      enabled = false;
      Iterator<TimedPacket> iterator = packets.iterator();
      while (iterator.hasNext()) {
        TimedPacket timedPacket = iterator.next();
        if (timedPacket.getTime() + amount < System.currentTimeMillis()) {
          queuePacket(timedPacket.getPacket());
          iterator.remove();
        }
      }
      enabled = wasEnabled;
      return;
    }

    if (!(enabled =
        !enabledTimer.hasTimeElapsed(100) && !(mc.currentScreen instanceof GuiDownloadTerrain))) {
      dispatch();
    } else {
      // Temporarily disable to prevent re-capturing dispatched packets
      enabled = false;

      Iterator<TimedPacket> iterator = packets.iterator();
      while (iterator.hasNext()) {
        TimedPacket timedPacket = iterator.next();
        if (timedPacket.getTime() + amount < System.currentTimeMillis()) {
          queuePacket(timedPacket.getPacket());
          iterator.remove();
        }
      }

      enabled = true;
    }
  }

  public static void spoof(
      int amount, boolean regular, boolean velocity, boolean teleports, boolean players) {
    spoof(amount, regular, velocity, teleports, players, false);
  }

  public static void spoof(
      int amount,
      boolean regular,
      boolean velocity,
      boolean teleports,
      boolean players,
      boolean blink) {
    spoof(amount, regular, velocity, teleports, players, blink, false);
  }

  public static void spoof(
      int amount,
      boolean regular,
      boolean velocity,
      boolean teleports,
      boolean players,
      boolean blink,
      boolean movement) {
    enabledTimer.reset();
    PingSpoofComponent.regularEnabled = regular;
    PingSpoofComponent.velocityEnabled = velocity;
    PingSpoofComponent.teleportsEnabled = teleports;
    PingSpoofComponent.playersEnabled = players;
    PingSpoofComponent.blinkEnabled = blink;
    PingSpoofComponent.movementEnabled = movement;
    PingSpoofComponent.amount = amount;
  }

  public static void blink() {
    spoof(9999999, true, false, false, false, true);
  }

  public static class TimedPacket {
    private final Packet<?> packet;
    private final long time;

    public TimedPacket(Packet<?> packet) {
      this.packet = packet;
      this.time = System.currentTimeMillis();
    }

    public Packet<?> getPacket() {
      return packet;
    }

    public long getTime() {
      return time;
    }
  }
}
