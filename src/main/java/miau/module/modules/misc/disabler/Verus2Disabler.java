package miau.module.modules.misc.disabler;

import java.util.concurrent.ConcurrentLinkedQueue;
import miau.event.impl.*;
import miau.event.types.EventType;
import miau.module.modules.misc.Disabler;
import miau.property.properties.BooleanProperty;
import miau.util.network.PacketUtil;
import miau.util.time.TimerUtil;
import net.minecraft.network.Packet;
import net.minecraft.network.play.client.*;

/**
 * Verus 2 disabler with toggle buttons for movement, sprint, and damage. Ported from OpenRise (Rise
 * 6)
 *
 * <p>Toggle buttons: - Movement: Transaction/keepalive queue, position flag, teleport cancel -
 * Sprint: Cancel & spoof sprint packets - Damage: Auto-damage player for disabler
 */
public class Verus2Disabler extends DisablerMode {

  public final BooleanProperty movementDisabler = new BooleanProperty("Movement", false);
  public final BooleanProperty sprintDisabler = new BooleanProperty("Sprint", false);
  public final BooleanProperty DamagePlayer = new BooleanProperty("Damage", false);

  private final ConcurrentLinkedQueue<Packet<?>> transactions = new ConcurrentLinkedQueue<>();
  private final ConcurrentLinkedQueue<Packet<?>> keepAlives = new ConcurrentLinkedQueue<>();
  private boolean teleported;
  private final TimerUtil timer = new TimerUtil();
  private int lastHurtTime = 0;

  public Verus2Disabler(String name, Disabler parent) {
    super(name, parent);
  }

  @Override
  public void onEnable() {
    timer.reset();
  }

  @Override
  public void onTick(TickEvent event) {
    if (event.getType() != EventType.PRE) return;
    if (mc.thePlayer == null || mc.theWorld == null) return;

    lastHurtTime++;

    if (movementDisabler.getValue()) {
      // Position flag every 100 ticks
      if (mc.thePlayer.ticksExisted % 100 == 0) {
        PacketUtil.sendPacketNoEvent(
            new C03PacketPlayer.C04PacketPlayerPosition(
                mc.thePlayer.posX, mc.thePlayer.posY, mc.thePlayer.posZ, mc.thePlayer.onGround));
        PacketUtil.sendPacketNoEvent(
            new C03PacketPlayer.C04PacketPlayerPosition(
                mc.thePlayer.posX, mc.thePlayer.posY - 0.015625, mc.thePlayer.posZ, false));
        PacketUtil.sendPacketNoEvent(
            new C03PacketPlayer.C04PacketPlayerPosition(
                mc.thePlayer.posX, mc.thePlayer.posY, mc.thePlayer.posZ, mc.thePlayer.onGround));
        teleported = true;
      }

      // Send queued transactions at 500ms
      if (timer.hasTimeElapsed(500L) && !transactions.isEmpty()) {
        PacketUtil.sendPacketNoEvent(transactions.poll());
        timer.reset();
      }

      // Auto-damage
      if (DamagePlayer.getValue() && mc.thePlayer.ticksExisted % 200 == 0 && lastHurtTime >= 100) {
        if (mc.thePlayer.onGround) {
          PacketUtil.sendPacketNoEvent(
              new C03PacketPlayer.C04PacketPlayerPosition(
                  mc.thePlayer.posX, mc.thePlayer.posY + 3.42, mc.thePlayer.posZ, false));
          PacketUtil.sendPacketNoEvent(
              new C03PacketPlayer.C04PacketPlayerPosition(
                  mc.thePlayer.posX, mc.thePlayer.posY, mc.thePlayer.posZ, true));
        }
      }
    }

    if (mc.thePlayer.hurtTime > 0) {
      lastHurtTime = 0;
    }

    // Send queued keepalives at 400ms
    if (timer.hasTimeElapsed(400L) && !keepAlives.isEmpty()) {
      PacketUtil.sendPacketNoEvent(keepAlives.poll());
      timer.reset();
    }

    // Sprint spoof
    if (sprintDisabler.getValue() && isMoving()) {
      PacketUtil.sendPacketNoEvent(
          new C0BPacketEntityAction(
              mc.thePlayer,
              mc.thePlayer.ticksExisted % 2 == 0
                  ? C0BPacketEntityAction.Action.STOP_SPRINTING
                  : C0BPacketEntityAction.Action.START_SPRINTING));
    }
  }

  @Override
  public void onPacket(PacketEvent event) {
    if (event.getType() == EventType.SEND) {
      Packet<?> packet = event.getPacket();

      // Cancel sprint packets
      if (sprintDisabler.getValue() && packet instanceof C0BPacketEntityAction) {
        C0BPacketEntityAction c0b = (C0BPacketEntityAction) packet;
        if (c0b.getAction() == C0BPacketEntityAction.Action.START_SPRINTING
            || c0b.getAction() == C0BPacketEntityAction.Action.STOP_SPRINTING) {
          event.setCancelled(true);
        }
      }

      if (movementDisabler.getValue()) {
        // Queue transactions
        if (packet instanceof C0FPacketConfirmTransaction) {
          transactions.add(packet);
          event.setCancelled(true);
          if (transactions.size() > 300) {
            PacketUtil.sendPacketNoEvent(transactions.poll());
          }
        }

        // Queue keepalives (with key modification)
        if (packet instanceof C00PacketKeepAlive) {
          keepAlives.add(packet);
          event.setCancelled(true);
        }
      }
    }
  }

  @Override
  public void onLoadWorld(LoadWorldEvent event) {
    transactions.clear();
    keepAlives.clear();
    teleported = false;
  }

  private boolean isMoving() {
    return mc.thePlayer != null
        && (mc.thePlayer.movementInput.moveForward != 0
            || mc.thePlayer.movementInput.moveStrafe != 0);
  }
}
