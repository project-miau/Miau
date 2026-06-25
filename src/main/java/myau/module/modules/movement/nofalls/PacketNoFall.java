package myau.module.modules.movement.nofalls;

import myau.event.impl.PacketEvent;
import myau.event.impl.TickEvent;
import myau.event.types.EventType;
import myau.mixin.IAccessorMinecraft;
import myau.module.modules.movement.NoFall;
import myau.util.network.PacketUtil;
import myau.util.player.PlayerUtil;
import net.minecraft.network.play.client.C03PacketPlayer;
import net.minecraft.util.AxisAlignedBB;

public class PacketNoFall extends NoFallMode {
  private boolean slowFalling = false;

  public PacketNoFall(String name, NoFall parent) {
    super(name, parent);
  }

  @Override
  public void onPacket(PacketEvent event) {
    if (event.getType() == EventType.SEND && event.getPacket() instanceof C03PacketPlayer) {
      C03PacketPlayer packet = (C03PacketPlayer) event.getPacket();
      if (this.slowFalling) {
        this.slowFalling = false;
        ((IAccessorMinecraft) mc).getTimer().timerSpeed = 1.0F;
      } else if (!packet.isOnGround()) {
        AxisAlignedBB aabb = mc.thePlayer.getEntityBoundingBox().expand(2.0, 0.0, 2.0);
        if (PlayerUtil.canFly(parent.distance.getValue())
            && !PlayerUtil.checkInWater(aabb)
            && parent.canTrigger()) {
          parent.packetDelayTimer.reset();
          this.slowFalling = true;
          ((IAccessorMinecraft) mc).getTimer().timerSpeed = 0.5F;
        }
      }
    }
  }

  @Override
  public void onTick(TickEvent event) {
    if (event.getType() == EventType.PRE) {
      if (this.slowFalling) {
        PacketUtil.sendPacketNoEvent(new C03PacketPlayer(true));
        mc.thePlayer.fallDistance = 0.0F;
      }
    }
  }

  @Override
  public void onDisable() {
    if (this.slowFalling) {
      this.slowFalling = false;
      ((IAccessorMinecraft) mc).getTimer().timerSpeed = 1.0F;
    }
  }
}
