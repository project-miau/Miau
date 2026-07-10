package miau.module.modules.ghost;

import miau.event.EventTarget;
import miau.event.impl.AttackEvent;
import miau.event.impl.TickEvent;
import miau.event.types.EventType;
import miau.module.Module;
import miau.property.properties.BooleanProperty;
import miau.property.properties.ModeProperty;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.network.play.client.C0BPacketEntityAction;
import net.minecraft.util.MathHelper;
import net.minecraft.util.MovingObjectPosition;

public class MoreKB extends Module {

  private static final Minecraft mc = Minecraft.getMinecraft();
  public final ModeProperty mode =
      new ModeProperty(
          "mode",
          0,
          new String[] {"LEGIT", "LEGIT_FAST", "LESS_PACKET", "PACKET", "DOUBLE_PACKET"});
  public final BooleanProperty intelligent = new BooleanProperty("intelligent", false);
  public final BooleanProperty onlyGround = new BooleanProperty("only-ground", true);

  private EntityLivingBase target;
  private boolean shouldSprintReset;

  public MoreKB() {
    super("MoreKB", false);
  }

  @Override
  public void onEnabled() {
    shouldSprintReset = false;
    target = null;
  }

  @Override
  public void onDisabled() {
    shouldSprintReset = false;
    target = null;
  }

  @EventTarget
  public void onAttack(AttackEvent event) {
    if (!isEnabled()) return;
    if (event.getTarget() instanceof EntityLivingBase) {
      this.target = (EntityLivingBase) event.getTarget();
    }
  }

  @EventTarget
  public void onTick(TickEvent event) {
    if (!isEnabled()) return;
    if (event.getType() != EventType.PRE) return;
    if (mc.thePlayer == null || mc.theWorld == null) return;

    int currentMode = mode.getValue();

    if (currentMode == 1) {
      if (this.target != null && isMoving()) {
        if ((onlyGround.getValue() && mc.thePlayer.onGround) || !onlyGround.getValue()) {
          mc.thePlayer.sprintingTicksLeft = 0;
        }
        this.target = null;
      }
      return;
    }

    EntityLivingBase entity = null;
    if (mc.objectMouseOver != null
        && mc.objectMouseOver.typeOfHit == MovingObjectPosition.MovingObjectType.ENTITY
        && mc.objectMouseOver.entityHit instanceof EntityLivingBase) {
      entity = (EntityLivingBase) mc.objectMouseOver.entityHit;
    }

    if (entity == null) return;

    if (intelligent.getValue()) {
      float calcYaw =
          (float)
                  (Math.atan2(entity.posZ - mc.thePlayer.posZ, entity.posX - mc.thePlayer.posX)
                      * 180.0D
                      / Math.PI)
              - 90.0F;
      float diffY = Math.abs(MathHelper.wrapAngleTo180_float(calcYaw - entity.rotationYawHead));
      if (diffY > 120.0F) return;
    }

    if (entity.hurtTime == 10) {
      switch (currentMode) {
        case 0:
          shouldSprintReset = true;
          if (mc.thePlayer.isSprinting()) {
            mc.thePlayer.setSprinting(false);
            mc.thePlayer.setSprinting(true);
          }
          shouldSprintReset = false;
          break;
        case 2:
          if (mc.thePlayer.isSprinting()) {
            mc.thePlayer.setSprinting(false);
          }
          mc.getNetHandler()
              .addToSendQueue(
                  new C0BPacketEntityAction(
                      mc.thePlayer, C0BPacketEntityAction.Action.START_SPRINTING));
          mc.thePlayer.setSprinting(true);
          break;
        case 3:
          mc.thePlayer.sendQueue.addToSendQueue(
              new C0BPacketEntityAction(mc.thePlayer, C0BPacketEntityAction.Action.STOP_SPRINTING));
          mc.thePlayer.sendQueue.addToSendQueue(
              new C0BPacketEntityAction(
                  mc.thePlayer, C0BPacketEntityAction.Action.START_SPRINTING));
          mc.thePlayer.setSprinting(true);
          break;
        case 4:
          mc.thePlayer.sendQueue.addToSendQueue(
              new C0BPacketEntityAction(mc.thePlayer, C0BPacketEntityAction.Action.STOP_SPRINTING));
          mc.thePlayer.sendQueue.addToSendQueue(
              new C0BPacketEntityAction(
                  mc.thePlayer, C0BPacketEntityAction.Action.START_SPRINTING));
          mc.thePlayer.sendQueue.addToSendQueue(
              new C0BPacketEntityAction(mc.thePlayer, C0BPacketEntityAction.Action.STOP_SPRINTING));
          mc.thePlayer.sendQueue.addToSendQueue(
              new C0BPacketEntityAction(
                  mc.thePlayer, C0BPacketEntityAction.Action.START_SPRINTING));
          mc.thePlayer.setSprinting(true);
          break;
      }
    }
  }

  private boolean isMoving() {
    return mc.thePlayer.movementInput.moveForward != 0.0F
        || mc.thePlayer.movementInput.moveStrafe != 0.0F;
  }
}
