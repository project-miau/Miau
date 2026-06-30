package miau.module.modules.ghost;

import miau.event.EventTarget;
import miau.event.impl.AttackEvent;
import miau.event.impl.MoveInputEvent;
import miau.event.types.Priority;
import miau.module.Module;
import miau.property.properties.FloatProperty;
import miau.property.properties.ModeProperty;
import net.minecraft.client.Minecraft;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.network.play.client.C0BPacketEntityAction;

public class Wtap extends Module {
  private static final Minecraft mc = Minecraft.getMinecraft();

  public final ModeProperty mode = new ModeProperty("Mode", 0, new String[] {"Legit", "Silent"});
  public final FloatProperty chance = new FloatProperty("WTap Chance", 100.0F, 0.0F, 100.0F);

  private boolean wTap;
  private boolean unsprint;
  private EntityLivingBase silentTarget;

  public Wtap() {
    super("WTap", false);
  }

  @EventTarget
  public void onAttack(AttackEvent event) {
    if (!isEnabled()) return;

    Entity entity = event.getTarget();
    if (!(entity instanceof EntityLivingBase)) return;
    EntityLivingBase target = (EntityLivingBase) entity;

    switch (mode.getModeString()) {
      case "Legit":
        {
          wTap = Math.random() * 100 < chance.getValue() && target.hurtTime >= 6;
          if (!wTap || unsprint) return;
          if (mc.thePlayer.isSprinting() || mc.gameSettings.keyBindSprint.isKeyDown()) {
            KeyBinding.setKeyBindState(mc.gameSettings.keyBindSprint.getKeyCode(), true);
            unsprint = true;
          }
          break;
        }
      case "Silent":
        {
          silentTarget = target;
          break;
        }
    }
  }

  @EventTarget(Priority.LOWEST)
  public void onMoveInput(MoveInputEvent event) {
    if (!isEnabled()) return;

    switch (mode.getModeString()) {
      case "Legit":
        {
          if (!wTap) return;
          if (unsprint && Math.random() * 100 < chance.getValue()) {
            KeyBinding.setKeyBindState(mc.gameSettings.keyBindSprint.getKeyCode(), false);
            unsprint = false;
          }
          break;
        }
      case "Silent":
        {
          if (silentTarget != null) {
            if (silentTarget.hurtTime == 9) {
              mc.getNetHandler()
                  .addToSendQueue(
                      new C0BPacketEntityAction(
                          mc.thePlayer, C0BPacketEntityAction.Action.STOP_SPRINTING));
              silentTarget = null;
            } else if (silentTarget.isDead || silentTarget.hurtTime < 9) {
              silentTarget = null;
            }
          }
          break;
        }
    }
  }
}
