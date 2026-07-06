package miau.module.modules.misc.disabler;

import java.util.*;
import miau.event.impl.*;
import miau.event.types.EventType;
import miau.module.modules.misc.Disabler;
import miau.property.properties.BooleanProperty;
import miau.util.network.PacketUtil;
import miau.util.time.TimerUtil;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.network.Packet;
import net.minecraft.network.play.client.*;
import net.minecraft.util.BlockPos;
import net.minecraft.util.EnumFacing;

/**
 * Watchdog disabler: sprint flicker, jump disabler, inventory blink. Ported from OpenRise (Rise 6)
 *
 * <p>Toggle buttons: - Strafe: Strafing packet manipulation - Jump: Jump check disabler -
 * Inventory: Inventory check bypass
 */
public class WatchdogDisabler extends DisablerMode {

  public final BooleanProperty scaffold = new BooleanProperty("Strafe", false);
  public final BooleanProperty ban = new BooleanProperty("Jump", false);
  public final BooleanProperty inventory = new BooleanProperty("Inventory", false);

  private float forward, strafe;
  private int time = 0;
  private boolean jump, set;
  private Integer ticks;
  private final TimerUtil timer = new TimerUtil();

  public WatchdogDisabler(String name, Disabler parent) {
    super(name, parent);
  }

  @Override
  public void onEnable() {
    timer.reset();
    jump = true;
    set = false;
    ticks = 0;
  }

  @Override
  public void onLoadWorld(LoadWorldEvent event) {
    jump = true;
    set = false;
    ticks = 0;
  }

  @Override
  public void onTick(TickEvent event) {
    if (event.getType() != EventType.PRE) return;
    if (mc.thePlayer == null || mc.theWorld == null) return;

    // Sprint flicker
    time++;
    switch (time) {
      case 1:
        mc.thePlayer.setSprinting(true);
        break;
      case 2:
        mc.thePlayer.setSprinting(false);
        time = (int) Math.round(-(Math.random() + 1));
        break;
    }

    // Jump disabler with water bucket
    if (ban.getValue()
        && (mc.thePlayer.inventory.getStackInSlot(0) == null
            || mc.thePlayer.inventory.getStackInSlot(0).getItem() != Items.compass)) {

      if (jump && mc.thePlayer.onGround) {
        mc.thePlayer.jump();
        jump = false;
        set = true;
      } else if (!mc.thePlayer.onGround && this.ticks != null && ++this.ticks >= 10 && set) {
        Random random = new Random();
        float hitX = random.nextFloat();
        float hitZ = random.nextFloat();
        PacketUtil.sendPacketNoEvent(
            new C08PacketPlayerBlockPlacement(
                new BlockPos(mc.thePlayer.posX, mc.thePlayer.posY, mc.thePlayer.posZ),
                EnumFacing.UP.getIndex(),
                new ItemStack(Items.water_bucket),
                hitX,
                1.0F,
                hitZ));
      }
    }

    // Strafe mode: send water bucket randomly
    if (scaffold.getValue()) {
      if (Math.random() < 0.2) {
        Random random = new Random();
        float hitX = random.nextFloat();
        float hitZ = random.nextFloat();
        PacketUtil.sendPacketNoEvent(
            new C08PacketPlayerBlockPlacement(
                new BlockPos(mc.thePlayer.posX, mc.thePlayer.posY, mc.thePlayer.posZ),
                EnumFacing.UP.getIndex(),
                new ItemStack(Items.water_bucket),
                hitX,
                1.0F,
                hitZ));
      }
    }
  }

  @Override
  public void onPacket(PacketEvent event) {
    if (event.getType() == EventType.SEND && inventory.getValue()) {
      Packet<?> packet = event.getPacket();
      if (packet instanceof C0EPacketClickWindow
          && ((C0EPacketClickWindow) packet).getWindowId()
              == mc.thePlayer.inventoryContainer.windowId
          && !(mc.currentScreen instanceof net.minecraft.client.gui.inventory.GuiInventory)) {
        event.setCancelled(true);
        PacketUtil.sendPacketNoEvent(
            new C16PacketClientStatus(C16PacketClientStatus.EnumState.OPEN_INVENTORY_ACHIEVEMENT));
        PacketUtil.sendPacketNoEvent(packet);
      }
    }
  }

  @Override
  public void onStrafe(StrafeEvent event) {
    forward = event.getForward();
    strafe = event.getStrafe();
  }
}
