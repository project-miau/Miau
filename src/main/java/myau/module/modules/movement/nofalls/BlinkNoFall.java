package myau.module.modules.movement.nofalls;

import myau.Myau;
import myau.enums.BlinkModules;
import myau.event.impl.PacketEvent;
import myau.event.types.EventType;
import myau.mixin.IAccessorC03PacketPlayer;
import myau.module.modules.movement.NoFall;
import myau.util.client.ChatUtil;
import myau.util.player.PlayerUtil;
import net.minecraft.network.Packet;
import net.minecraft.network.play.client.C03PacketPlayer;

public class BlinkNoFall extends NoFallMode {
  private boolean lastOnGround = false;

  public BlinkNoFall(String name, NoFall parent) {
    super(name, parent);
  }

  @Override
  public void onPacket(PacketEvent event) {
    if (event.getType() == EventType.SEND && event.getPacket() instanceof C03PacketPlayer) {
      C03PacketPlayer packet = (C03PacketPlayer) event.getPacket();
      boolean allowed =
          !mc.thePlayer.isOnLadder()
              && !mc.thePlayer.capabilities.allowFlying
              && mc.thePlayer.hurtTime == 0;
      if (Myau.blinkManager.getBlinkingModule() != BlinkModules.NO_FALL) {
        if (this.lastOnGround
            && !packet.isOnGround()
            && allowed
            && PlayerUtil.canFly(parent.distance.getValue().intValue())
            && mc.thePlayer.motionY < 0.0) {
          Myau.blinkManager.setBlinkState(false, Myau.blinkManager.getBlinkingModule());
          Myau.blinkManager.setBlinkState(true, BlinkModules.NO_FALL);
        }
      } else if (!allowed) {
        Myau.blinkManager.setBlinkState(false, BlinkModules.NO_FALL);
        ChatUtil.display("%s%s: &cFailed player check!&r", parent.getName());
      } else if (PlayerUtil.checkInWater(
          mc.thePlayer.getEntityBoundingBox().expand(2.0, 0.0, 2.0))) {
        Myau.blinkManager.setBlinkState(false, BlinkModules.NO_FALL);
        ChatUtil.display("%s%s: &cFailed void check!&r", parent.getName());
      } else if (packet.isOnGround()) {
        for (Packet<?> blinkedPacket : Myau.blinkManager.blinkedPackets) {
          if (blinkedPacket instanceof C03PacketPlayer) {
            ((IAccessorC03PacketPlayer) blinkedPacket).setOnGround(true);
          }
        }
        Myau.blinkManager.setBlinkState(false, BlinkModules.NO_FALL);
        parent.packetDelayTimer.reset();
      }
      this.lastOnGround = packet.isOnGround() && allowed && parent.canTrigger();
    }
  }

  @Override
  public void onDisable() {
    this.lastOnGround = false;
    Myau.blinkManager.setBlinkState(false, BlinkModules.NO_FALL);
  }
}
