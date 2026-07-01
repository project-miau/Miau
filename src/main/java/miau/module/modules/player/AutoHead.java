package miau.module.modules.player;

import miau.Miau;
import miau.component.BadPacketsComponent;
import miau.event.EventTarget;
import miau.event.impl.UpdateEvent;
import miau.event.types.EventType;
import miau.event.types.Priority;
import miau.mixin.IAccessorPlayerControllerMP;
import miau.module.Module;
import miau.property.properties.IntProperty;
import miau.util.math.MathUtil;
import miau.util.network.PacketUtil;
import miau.util.time.TimerUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.item.ItemSkull;
import net.minecraft.item.ItemStack;
import net.minecraft.network.play.client.C08PacketPlayerBlockPlacement;

public class AutoHead extends Module {

  private static final Minecraft mc = Minecraft.getMinecraft();
  private final TimerUtil timer = new TimerUtil();
  private long nextUse;

  public final IntProperty health = new IntProperty("health", 15, 1, 20);
  public final IntProperty minDelay = new IntProperty("min-delay", 500, 50, 5000);
  public final IntProperty maxDelay = new IntProperty("max-delay", 1000, 50, 5000);

  public AutoHead() {
    super("AutoHead", false);
  }

  /**
   * Checks whether AutoHead is about to heal (or in the middle of healing). Used by KillAura to
   * pause attacking so AutoWeapon doesn't interrupt the head slot.
   */
  public boolean isHealing() {
    if (!this.isEnabled()) return false;
    if (!timer.hasTimeElapsed(nextUse)) return true; // still in cooldown from last head use
    if (mc.thePlayer.getAbsorptionAmount() > 0) return false;
    if (mc.thePlayer.getHealth() > this.health.getValue().floatValue()) return false;
    if (Miau.moduleManager.modules.get(Scaffold.class).isEnabled()) return false;

    // Check if there's a head in hotbar
    for (int i = 0; i < 9; i++) {
      final ItemStack stack = mc.thePlayer.inventory.getStackInSlot(i);
      if (stack != null && stack.getItem() instanceof ItemSkull) {
        return true; // has heads and needs healing
      }
    }
    return false;
  }

  @EventTarget(Priority.HIGH)
  public void onPreUpdate(UpdateEvent event) {
    if (!this.isEnabled() || event.getType() != EventType.PRE) return;

    if (!timer.hasTimeElapsed(nextUse)
        || mc.thePlayer.getAbsorptionAmount() > 0
        || ((Scaffold) Miau.moduleManager.modules.get(Scaffold.class)).isEnabled()) {
      return;
    }

    for (int i = 0; i < 9; i++) {
      final ItemStack stack = mc.thePlayer.inventory.getStackInSlot(i);

      if (stack == null) {
        continue;
      }

      if (stack.getItem() instanceof ItemSkull) {
        if (mc.thePlayer.getHealth() > this.health.getValue().floatValue()) {
          continue;
        }

        Miau.slotComponent.setSlot(i);

        if (!BadPacketsComponent.bad()) {
          ((IAccessorPlayerControllerMP) mc.playerController).callSyncCurrentPlayItem();
          PacketUtil.sendPacket(
              new C08PacketPlayerBlockPlacement(Miau.slotComponent.getItemStack()));

          nextUse =
              Math.round(MathUtil.getRandom(this.minDelay.getValue(), this.maxDelay.getValue()));
          timer.reset();
        }
      }
    }
  }
}
