package miau.module.modules.ghost;

import miau.event.EventTarget;
import miau.event.impl.PacketEvent;
import miau.event.impl.UpdateEvent;
import miau.event.types.EventType;
import miau.module.Module;
import miau.property.properties.BooleanProperty;
import miau.property.properties.PercentProperty;
import miau.util.client.KeyBindUtil;
import miau.util.player.RayCastUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityOtherPlayerMP;
import net.minecraft.network.play.client.C03PacketPlayer;
import net.minecraft.potion.Potion;

public class JumpReset extends Module {

  private static final Minecraft mc = Minecraft.getMinecraft();
  private boolean setJump;
  private boolean ignoreNext;
  private boolean aiming;
  private int lastHurtTime;
  private double lastFallDistance;

  public final PercentProperty chance = new PercentProperty("chance", 100);
  public final BooleanProperty mouseDown = new BooleanProperty("mouse-down", false);
  public final BooleanProperty movingForward = new BooleanProperty("moving-forward", true);
  public final BooleanProperty aimingOnPlayer = new BooleanProperty("aiming-on-player", true);

  public JumpReset() {
    super("JumpReset", false);
  }

  @EventTarget
  public void onUpdate(UpdateEvent event) {
    if (event.getType() == EventType.PRE) {
      int hurtTime = mc.thePlayer.hurtTime;
      boolean onGround = mc.thePlayer.onGround;

      if (onGround && lastFallDistance > 3.0 && !mc.thePlayer.capabilities.allowFlying) {
        ignoreNext = true;
      }

      if (hurtTime > lastHurtTime) {
        boolean mouseDownCheck = KeyBindUtil.isKeyDown(-100) || !mouseDown.getValue();
        boolean aimingCheck = aiming || !aimingOnPlayer.getValue();
        boolean forwardCheck =
            KeyBindUtil.isKeyDown(mc.gameSettings.keyBindForward.getKeyCode())
                || !movingForward.getValue();

        if (!ignoreNext
            && onGround
            && aimingCheck
            && forwardCheck
            && mouseDownCheck
            && !mc.thePlayer.isBurning()
            && Math.random() * 100.0 < chance.getValue()
            && !hasBadEffect()) {
          setJump = true;
          KeyBindUtil.setKeyBindState(mc.gameSettings.keyBindJump.getKeyCode(), true);
        }
        ignoreNext = false;
      }

      lastHurtTime = hurtTime;
      lastFallDistance = mc.thePlayer.fallDistance;

    } else if (event.getType() == EventType.POST) {
      if (setJump && !KeyBindUtil.isKeyDown(mc.gameSettings.keyBindJump.getKeyCode())) {
        setJump = false;
        KeyBindUtil.setKeyBindState(mc.gameSettings.keyBindJump.getKeyCode(), false);
      }
    }
  }

  @EventTarget
  public void onPacket(PacketEvent event) {
    if (event.getType() != EventType.SEND) return;
    if (!(event.getPacket() instanceof C03PacketPlayer)) return;

    C03PacketPlayer packet = (C03PacketPlayer) event.getPacket();

    float yaw;
    float pitch;

    if (packet instanceof C03PacketPlayer.C06PacketPlayerPosLook) {
      yaw = ((C03PacketPlayer.C06PacketPlayerPosLook) packet).getYaw();
      pitch = ((C03PacketPlayer.C06PacketPlayerPosLook) packet).getPitch();
    } else if (packet instanceof C03PacketPlayer.C05PacketPlayerLook) {
      yaw = ((C03PacketPlayer.C05PacketPlayerLook) packet).getYaw();
      pitch = ((C03PacketPlayer.C05PacketPlayerLook) packet).getPitch();
    } else {
      return;
    }

    net.minecraft.util.MovingObjectPosition mop =
        RayCastUtil.rayCast(yaw, pitch, 5.0, 0.0f, mc.thePlayer);

    if (mop != null
        && mop.typeOfHit == net.minecraft.util.MovingObjectPosition.MovingObjectType.ENTITY
        && mop.entityHit instanceof EntityOtherPlayerMP) {
      aiming = true;
    } else {
      aiming = false;
    }
  }

  private boolean hasBadEffect() {
    for (net.minecraft.potion.PotionEffect effect : mc.thePlayer.getActivePotionEffects()) {
      int potionId = effect.getPotionID();
      if (potionId == Potion.jump.getId()
          || potionId == Potion.poison.getId()
          || potionId == Potion.wither.getId()) {
        return true;
      }
    }
    return false;
  }
}
