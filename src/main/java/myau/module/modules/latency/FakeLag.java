package myau.module.modules.latency;

import java.util.Iterator;
import java.util.LinkedList;
import myau.Myau;
import myau.enums.BlinkModules;
import myau.event.EventTarget;
import myau.event.impl.LoadWorldEvent;
import myau.event.impl.PacketEvent;
import myau.event.impl.UpdateEvent;
import myau.event.types.EventType;
import myau.module.Module;
import myau.property.properties.BooleanProperty;
import myau.property.properties.FloatProperty;
import myau.property.properties.IntProperty;
import myau.property.properties.ModeProperty;
import myau.util.math.MathUtil;
import myau.util.network.PacketUtil;
import myau.util.player.TeamUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.AbstractClientPlayer;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.network.Packet;
import net.minecraft.network.handshake.client.C00Handshake;
import net.minecraft.network.play.INetHandlerPlayClient;
import net.minecraft.network.play.client.C01PacketChatMessage;
import net.minecraft.network.play.client.C02PacketUseEntity;
import net.minecraft.network.status.client.C00PacketServerQuery;
import net.minecraft.network.status.client.C01PacketPing;
import net.minecraft.network.status.server.S01PacketPong;

public class FakeLag extends Module {
  private static final Minecraft mc = Minecraft.getMinecraft();

  public final ModeProperty mode = new ModeProperty("mode", 0, new String[] {"Latence", "Dynamic"});

  public final IntProperty minIncomingPing =
      new IntProperty("Incoming MinPing", 150, 0, 1000, () -> mode.getValue() == 0);
  public final IntProperty maxIncomingPing =
      new IntProperty("Incoming MaxPing", 170, 0, 1000, () -> mode.getValue() == 0);
  public final IntProperty minIncomingPingRecalculateDelay =
      new IntProperty("Incoming MinRecalc (ms)", 400, 0, 2000, () -> mode.getValue() == 0);
  public final IntProperty maxIncomingPingRecalculateDelay =
      new IntProperty("Incoming MaxRecalc (ms)", 600, 0, 2000, () -> mode.getValue() == 0);
  public final ModeProperty incomingPingAccelerationMode =
      new ModeProperty(
          "Incoming AccelMode", 1, new String[] {"Instant", "Smooth"}, () -> mode.getValue() == 0);
  public final IntProperty minIncomingPingDecelerationTime =
      new IntProperty(
          "Incoming MinDecelTime",
          35,
          0,
          1000,
          () -> mode.getValue() == 0 && incomingPingAccelerationMode.getValue() == 1);
  public final IntProperty maxIncomingPingDecelerationTime =
      new IntProperty(
          "Incoming MaxDecelTime",
          45,
          0,
          1000,
          () -> mode.getValue() == 0 && incomingPingAccelerationMode.getValue() == 1);

  public final IntProperty minOutgoingPing =
      new IntProperty("Outgoing MinPing", 150, 0, 1000, () -> mode.getValue() == 0);
  public final IntProperty maxOutgoingPing =
      new IntProperty("Outgoing MaxPing", 200, 0, 1000, () -> mode.getValue() == 0);
  public final IntProperty minOutgoingPingRecalculateDelay =
      new IntProperty("Outgoing MinRecalc (ms)", 400, 0, 2000, () -> mode.getValue() == 0);
  public final IntProperty maxOutgoingPingRecalculateDelay =
      new IntProperty("Outgoing MaxRecalc (ms)", 600, 0, 2000, () -> mode.getValue() == 0);
  public final ModeProperty outgoingPingAccelerationMode =
      new ModeProperty(
          "Outgoing AccelMode", 1, new String[] {"Instant", "Smooth"}, () -> mode.getValue() == 0);
  public final IntProperty minOutgoingPingDecelerationTime =
      new IntProperty(
          "Outgoing MinDecelTime",
          35,
          0,
          1000,
          () -> mode.getValue() == 0 && outgoingPingAccelerationMode.getValue() == 1);
  public final IntProperty maxOutgoingPingDecelerationTime =
      new IntProperty(
          "Outgoing MaxDecelTime",
          45,
          0,
          1000,
          () -> mode.getValue() == 0 && outgoingPingAccelerationMode.getValue() == 1);

  public final IntProperty dynamicDelay =
      new IntProperty("dynamic-delay", 200, 25, 1000, () -> mode.getValue() == 1);
  public final IntProperty dynamicCooldown =
      new IntProperty("dynamic-cooldown", 120, 0, 500, () -> mode.getValue() == 1);
  public final BooleanProperty dynamicDebug =
      new BooleanProperty("dynamic-debug", false, () -> mode.getValue() == 1);
  public final BooleanProperty dynamicIgnoreTeammates =
      new BooleanProperty("dynamic-ignore-teammates", true, () -> mode.getValue() == 1);
  public final BooleanProperty dynamicStopOnHurt =
      new BooleanProperty("dynamic-stop-on-hurt", true, () -> mode.getValue() == 1);
  public final IntProperty dynamicStopOnHurtTime =
      new IntProperty(
          "dynamic-stop-on-hurt-time",
          500,
          0,
          1000,
          () -> mode.getValue() == 1 && dynamicStopOnHurt.getValue());
  public final FloatProperty dynamicStartRange =
      new FloatProperty("dynamic-start-range", 6.0F, 3.0F, 10.0F, () -> mode.getValue() == 1);
  public final FloatProperty dynamicStopRange =
      new FloatProperty("dynamic-stop-range", 3.5F, 1.0F, 6.0F, () -> mode.getValue() == 1);
  public final FloatProperty dynamicMaxTargetRange =
      new FloatProperty("dynamic-max-target-range", 15.0F, 6.0F, 20.0F, () -> mode.getValue() == 1);

  private final LinkedList<QueueData> incomingQueue = new LinkedList<>();
  private final LinkedList<QueueData> outgoingQueue = new LinkedList<>();

  private long incomingTargetPing;
  private long incomingCurrentPing;
  private long lastIncomingRecalculateTime;
  private long nextIncomingRecalculateDelay;

  private long outgoingTargetPing;
  private long outgoingCurrentPing;
  private long lastOutgoingRecalculateTime;
  private long nextOutgoingRecalculateDelay;

  private AbstractClientPlayer dynamicTarget;
  private long dynamicLastDisableTime = -1L;
  private long dynamicLastStopBlinkTime = -1L;
  private boolean dynamicLastHurt;
  private long dynamicLastStartBlinkTime = -1L;

  public FakeLag() {
    super("FakeLag", false);
  }

  @Override
  public void onEnabled() {
    this.clearPackets();
    long now = System.currentTimeMillis();

    lastIncomingRecalculateTime = now;
    nextIncomingRecalculateDelay =
        (long)
            MathUtil.getRandom(
                minIncomingPingRecalculateDelay.getValue(),
                maxIncomingPingRecalculateDelay.getValue());
    incomingTargetPing =
        (long) MathUtil.getRandom(minIncomingPing.getValue(), maxIncomingPing.getValue());
    incomingCurrentPing = incomingTargetPing;

    lastOutgoingRecalculateTime = now;
    nextOutgoingRecalculateDelay =
        (long)
            MathUtil.getRandom(
                minOutgoingPingRecalculateDelay.getValue(),
                maxOutgoingPingRecalculateDelay.getValue());
    outgoingTargetPing =
        (long) MathUtil.getRandom(minOutgoingPing.getValue(), maxOutgoingPing.getValue());
    outgoingCurrentPing = outgoingTargetPing;

    this.dynamicTarget = null;
    this.dynamicLastDisableTime = -1L;
    this.dynamicLastStopBlinkTime = -1L;
    this.dynamicLastHurt = false;
    this.dynamicLastStartBlinkTime = -1L;
  }

  @Override
  public void onDisabled() {
    stopDynamicBlink();
    if (mc.thePlayer != null) {
      this.handlePackets(true);
    } else {
      this.clearPackets();
    }
  }

  @EventTarget
  public void onPacket(PacketEvent event) {
    if (!this.isEnabled() || mc.thePlayer == null || mc.theWorld == null || event.isCancelled())
      return;

    Packet<?> packet = event.getPacket();

    if (this.mode.getValue() == 1) {
      if (event.getType() == EventType.SEND) {
        handleDynamicAttackTarget(packet);
      }
      return;
    }

    if (this.isIgnoredPacket(packet)) return;

    if (event.getType() == EventType.RECEIVE) {
      if (!packet.getClass().getName().startsWith("net.minecraft.network.play.server.")) return;
      event.setCancelled(true);
      incomingQueue.add(new QueueData(packet, System.currentTimeMillis(), incomingCurrentPing));
    } else if (event.getType() == EventType.SEND) {
      if (!packet.getClass().getName().startsWith("net.minecraft.network.play.client.")) return;
      event.setCancelled(true);
      outgoingQueue.add(new QueueData(packet, System.currentTimeMillis(), outgoingCurrentPing));
    }
  }

  @EventTarget
  public void onWorldLoad(LoadWorldEvent event) {
    stopDynamicBlink();
    this.handlePackets(true);
  }

  @EventTarget
  public void onUpdate(UpdateEvent event) {
    if (!this.isEnabled()
        || event.getType() != EventType.PRE
        || mc.thePlayer == null
        || mc.theWorld == null) return;

    if (this.mode.getValue() == 1) {
      handleDynamic();
      return;
    }

    long now = System.currentTimeMillis();

    if (now - lastIncomingRecalculateTime >= nextIncomingRecalculateDelay) {
      lastIncomingRecalculateTime = now;
      nextIncomingRecalculateDelay =
          (long)
              MathUtil.getRandom(
                  minIncomingPingRecalculateDelay.getValue(),
                  maxIncomingPingRecalculateDelay.getValue());
      incomingTargetPing =
          (long) MathUtil.getRandom(minIncomingPing.getValue(), maxIncomingPing.getValue());
    }

    if (now - lastOutgoingRecalculateTime >= nextOutgoingRecalculateDelay) {
      lastOutgoingRecalculateTime = now;
      nextOutgoingRecalculateDelay =
          (long)
              MathUtil.getRandom(
                  minOutgoingPingRecalculateDelay.getValue(),
                  maxOutgoingPingRecalculateDelay.getValue());
      outgoingTargetPing =
          (long) MathUtil.getRandom(minOutgoingPing.getValue(), maxOutgoingPing.getValue());
    }

    if (incomingPingAccelerationMode.getValue() == 0) {
      incomingCurrentPing = incomingTargetPing;
    } else {
      long decel =
          (long)
              MathUtil.getRandom(
                  minIncomingPingDecelerationTime.getValue(),
                  maxIncomingPingDecelerationTime.getValue());
      if (incomingCurrentPing < incomingTargetPing) {
        incomingCurrentPing = Math.min(incomingCurrentPing + decel, incomingTargetPing);
      } else if (incomingCurrentPing > incomingTargetPing) {
        incomingCurrentPing = Math.max(incomingCurrentPing - decel, incomingTargetPing);
      }
    }

    if (outgoingPingAccelerationMode.getValue() == 0) {
      outgoingCurrentPing = outgoingTargetPing;
    } else {
      long decel =
          (long)
              MathUtil.getRandom(
                  minOutgoingPingDecelerationTime.getValue(),
                  maxOutgoingPingDecelerationTime.getValue());
      if (outgoingCurrentPing < outgoingTargetPing) {
        outgoingCurrentPing = Math.min(outgoingCurrentPing + decel, outgoingTargetPing);
      } else if (outgoingCurrentPing > outgoingTargetPing) {
        outgoingCurrentPing = Math.max(outgoingCurrentPing - decel, outgoingTargetPing);
      }
    }

    this.handlePackets(false);
  }

  private void handleDynamicAttackTarget(Packet<?> packet) {
    if (!(packet instanceof C02PacketUseEntity)) return;
    C02PacketUseEntity useEntity = (C02PacketUseEntity) packet;
    if (useEntity.getAction() != C02PacketUseEntity.Action.ATTACK) return;
    Entity entity = useEntity.getEntityFromWorld(mc.theWorld);
    if (entity instanceof AbstractClientPlayer) {
      if (dynamicIgnoreTeammates.getValue() && TeamUtil.isSameTeam((EntityPlayer) entity)) return;
      dynamicTarget = (AbstractClientPlayer) entity;
    }
  }

  private void handleDynamic() {
    boolean blinking = isDynamicBlinking();
    long now = System.currentTimeMillis();

    if (dynamicStopOnHurt.getValue()
        && dynamicLastDisableTime > 0
        && now - dynamicLastDisableTime <= dynamicStopOnHurtTime.getValue()) {
      if (blinking) {
        dynamicMessage("stop lag: hurt cooldown.");
        stopDynamicBlink();
        blinking = false;
      }
      dynamicLastHurt = mc.thePlayer.hurtTime > 0;
      return;
    }

    if (blinking) {
      if (now - dynamicLastStartBlinkTime >= dynamicDelay.getValue()) {
        dynamicMessage("stop lag: time out.");
        stopDynamicBlink();
        blinking = false;
      } else if (!dynamicLastHurt && mc.thePlayer.hurtTime > 0 && dynamicStopOnHurt.getValue()) {
        dynamicMessage("stop lag: hurt.");
        dynamicLastDisableTime = now;
        stopDynamicBlink();
        blinking = false;
      }
    }

    if (!isValidDynamicTarget(dynamicTarget)) {
      if (dynamicTarget != null) {
        dynamicMessage("release target: invalid.");
        dynamicTarget = null;
      }
      stopDynamicBlink();
      dynamicLastHurt = mc.thePlayer.hurtTime > 0;
      return;
    }

    double distance = mc.thePlayer.getDistanceToEntity(dynamicTarget);
    float startRange = Math.max(dynamicStartRange.getValue(), dynamicStopRange.getValue());
    float stopRange = Math.min(dynamicStartRange.getValue(), dynamicStopRange.getValue());

    if (distance > dynamicMaxTargetRange.getValue()) {
      dynamicMessage("release target: " + dynamicTarget.getName());
      dynamicTarget = null;
      stopDynamicBlink();
    } else if (blinking && distance <= stopRange) {
      dynamicMessage("stop lag: too close.");
      stopDynamicBlink();
    } else if (blinking && distance >= startRange) {
      dynamicMessage("stop lag: out of range.");
      stopDynamicBlink();
    } else if (!blinking
        && distance > stopRange
        && distance < startRange
        && now - dynamicLastStopBlinkTime >= dynamicCooldown.getValue()) {
      dynamicMessage("start lag: in range.");
      dynamicLastStartBlinkTime = now;
      startDynamicBlink();
    }

    dynamicLastHurt = mc.thePlayer.hurtTime > 0;
  }

  private boolean isValidDynamicTarget(AbstractClientPlayer target) {
    return target != null
        && !target.isDead
        && target.getHealth() > 0.0F
        && mc.theWorld != null
        && mc.theWorld.loadedEntityList.contains(target)
        && (!dynamicIgnoreTeammates.getValue() || !TeamUtil.isSameTeam(target));
  }

  private boolean isDynamicBlinking() {
    return Myau.blinkManager.getBlinkingModule() == BlinkModules.FAKE_LAG;
  }

  private void startDynamicBlink() {
    if (isDynamicBlinking()) return;
    Myau.blinkManager.setBlinkState(false, Myau.blinkManager.getBlinkingModule());
    Myau.blinkManager.setBlinkState(true, BlinkModules.FAKE_LAG);
  }

  private void stopDynamicBlink() {
    if (Myau.blinkManager.setBlinkState(false, BlinkModules.FAKE_LAG)) {
      dynamicLastStopBlinkTime = System.currentTimeMillis();
    }
  }

  private void dynamicMessage(String message) {
    if (dynamicDebug.getValue()) {
      myau.util.client.ChatUtil.display(Myau.clientName + this.getName() + ": &7" + message);
    }
  }

  private boolean isIgnoredPacket(Packet<?> packet) {
    return packet instanceof C00Handshake
        || packet instanceof C00PacketServerQuery
        || packet instanceof C01PacketPing
        || packet instanceof C01PacketChatMessage
        || packet instanceof S01PacketPong;
  }

  @SuppressWarnings("unchecked")
  private void handlePackets(boolean clear) {
    long now = System.currentTimeMillis();

    Iterator<QueueData> inIter = this.incomingQueue.iterator();
    while (inIter.hasNext()) {
      QueueData data = inIter.next();
      if (clear || now - data.time >= data.delay) {
        try {
          PacketUtil.handlePacket((Packet<INetHandlerPlayClient>) data.packet);
        } catch (Exception e) {

        }
        inIter.remove();
      }
    }

    Iterator<QueueData> outIter = this.outgoingQueue.iterator();
    while (outIter.hasNext()) {
      QueueData data = outIter.next();
      if (clear || now - data.time >= data.delay) {
        PacketUtil.sendPacketNoEvent(data.packet);
        outIter.remove();
      }
    }
  }

  private void clearPackets() {
    this.incomingQueue.clear();
    this.outgoingQueue.clear();
  }

  @Override
  public String[] getSuffix() {
    if (this.mode.getValue() == 0) {
      return new String[] {
        this.mode.getModeString() + " In:" + incomingCurrentPing + " Out:" + outgoingCurrentPing
      };
    }
    return new String[] {this.mode.getModeString()};
  }

  private static class QueueData {
    private final Packet<?> packet;
    private final long time;
    private final long delay;

    private QueueData(Packet<?> packet, long time, long delay) {
      this.packet = packet;
      this.time = time;
      this.delay = delay;
    }
  }
}
