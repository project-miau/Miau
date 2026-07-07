package miau.component;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedQueue;
import miau.event.EventTarget;
import miau.event.impl.LoadWorldEvent;
import miau.event.impl.PacketEvent;
import miau.event.impl.UpdateEvent;
import miau.event.types.EventType;
import miau.util.network.PacketUtil;
import miau.util.time.TimerUtil;
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

  private static final Map<String, String> sessionOwners = new HashMap<>();

  public static void registerSessionOwner(String sessionId, String owner) {
    sessionOwners.put(sessionId, owner);
  }

  public static String getSessionOwner(String sessionId) {
    return sessionOwners.get(sessionId);
  }

  public static void clearSessionOwners() {
    sessionOwners.clear();
  }

  static PacketCategory regular =
      new PacketCategory(
          new Class[] {
            C0FPacketConfirmTransaction.class, C00PacketKeepAlive.class,
                S1CPacketEntityMetadata.class
          },
          false);
  static PacketCategory velocity =
      new PacketCategory(
          new Class[] {S12PacketEntityVelocity.class, S27PacketExplosion.class}, false);
  static PacketCategory teleports =
      new PacketCategory(
          new Class[] {
            S08PacketPlayerPosLook.class, S39PacketPlayerAbilities.class,
                S09PacketHeldItemChange.class
          },
          false);
  static PacketCategory players =
      new PacketCategory(
          new Class[] {
            S13PacketDestroyEntities.class,
            S14PacketEntity.class,
            S14PacketEntity.S16PacketEntityLook.class,
            S14PacketEntity.S15PacketEntityRelMove.class,
            S14PacketEntity.S17PacketEntityLookMove.class,
            S18PacketEntityTeleport.class,
            S20PacketEntityProperties.class,
            S19PacketEntityHeadLook.class
          },
          false);
  static PacketCategory blink =
      new PacketCategory(
          new Class[] {
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
          },
          false);
  static PacketCategory movement =
      new PacketCategory(
          new Class[] {
            C03PacketPlayer.class,
            C03PacketPlayer.C04PacketPlayerPosition.class,
            C03PacketPlayer.C05PacketPlayerLook.class,
            C03PacketPlayer.C06PacketPlayerPosLook.class
          },
          false);

  static PacketCategory[] types =
      new PacketCategory[] {regular, velocity, teleports, players, blink, movement};

  @EventTarget
  public void onPacket(PacketEvent event) {
    if (!event.isCancelled()
        && enabled
        && Arrays.stream(types)
            .anyMatch(
                category ->
                    category.enabled
                        && Arrays.stream(category.packetClasses)
                            .anyMatch(clazz -> clazz == event.getPacket().getClass()))) {
      event.setCancelled(true);
      packets.add(new TimedPacket(event.getPacket()));
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
      @SuppressWarnings("unchecked")
      Packet<net.minecraft.network.play.INetHandlerPlayClient> serverPacket =
          (Packet<net.minecraft.network.play.INetHandlerPlayClient>) packet;
      PacketUtil.handlePacket(serverPacket);
    } else {
      if (mc.getNetHandler() != null && mc.getNetHandler().getNetworkManager() != null) {
        mc.getNetHandler().getNetworkManager().sendPacket(packet, null);
      }
    }
  }

  public static void disable() {
    enabled = false;
    enabledTimer.setTime(System.currentTimeMillis() - 999999999L);
  }

  @EventTarget
  public void onWorldLoad(LoadWorldEvent event) {
    dispatch();
  }

  @EventTarget
  public void onUpdate(UpdateEvent event) {
    if (event.getType() != EventType.POST) return;

    if (!(enabled =
        !enabledTimer.hasTimeElapsed(100)
            && !(mc.currentScreen instanceof GuiDownloadTerrain))) {
      dispatch();
    } else {
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

    PingSpoofComponent.regular.enabled = regular;
    PingSpoofComponent.velocity.enabled = velocity;
    PingSpoofComponent.teleports.enabled = teleports;
    PingSpoofComponent.players.enabled = players;
    PingSpoofComponent.blink.enabled = blink;
    PingSpoofComponent.movement.enabled = movement;
    PingSpoofComponent.amount = amount;
  }

  public static void blink() {
    spoof(9999999, true, false, false, false, true);
  }

  private static final java.util.Map<String, Boolean> activeSessions = new java.util.HashMap<>();

  public static boolean isOwnedBy(String sessionId) {
    return activeSessions.containsKey(sessionId) && activeSessions.get(sessionId);
  }

  public static void beginSession(
      String sessionId,
      int amount,
      boolean regularPackets,
      boolean velocityPackets,
      boolean teleportPackets,
      boolean playerPackets,
      boolean blinkPackets,
      boolean movementPackets) {
    activeSessions.put(sessionId, true);
    spoof(amount, regularPackets, velocityPackets, teleportPackets, playerPackets, blinkPackets, movementPackets);
  }

  public static void finishSession(String sessionId, boolean dispatchImmediately) {
    activeSessions.put(sessionId, false);
    if (dispatchImmediately) {
      dispatch();
    }
    disable();
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

  static class PacketCategory {
    private final Class[] packetClasses;
    private boolean enabled;

    public PacketCategory(Class[] packetClasses, boolean enabled) {
      this.packetClasses = packetClasses;
      this.enabled = enabled;
    }
  }
}
