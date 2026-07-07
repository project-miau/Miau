package miau.module.modules.misc.disabler;

import miau.event.impl.PacketEvent;
import miau.event.impl.TickEvent;
import miau.event.types.EventType;
import miau.module.modules.misc.Disabler;
import miau.property.properties.BooleanProperty;
import net.minecraft.network.play.client.C07PacketPlayerDigging;
import net.minecraft.network.play.client.C0BPacketEntityAction;
import net.minecraft.util.BlockPos;
import net.minecraft.util.EnumFacing;

public class VulcanDisabler extends DisablerMode {

  public final BooleanProperty scaffold = new BooleanProperty("Scaffold", true);
  public final BooleanProperty keepSprint = new BooleanProperty("Keep Sprint", true);
  public final BooleanProperty miscellaneous =
      new BooleanProperty("Miscellaneous (Auto-Block, BadPackets)", true);

  public VulcanDisabler(String name, Disabler parent) {
    super(name, parent);
  }

  @Override
  public void onEnable() {}

  @Override
  public void onDisable() {}

  @Override
  public void onTick(TickEvent event) {
    if (event.getType() != EventType.PRE) return;
    if (mc.thePlayer == null) return;

    if (keepSprint.getValue()) {
      mc.getNetHandler()
          .addToSendQueue(
              new C0BPacketEntityAction(
                  mc.thePlayer, C0BPacketEntityAction.Action.START_SPRINTING));
      mc.getNetHandler()
          .addToSendQueue(
              new C0BPacketEntityAction(mc.thePlayer, C0BPacketEntityAction.Action.STOP_SPRINTING));
    }

    if (scaffold.getValue() && mc.thePlayer.ticksExisted % 5 == 0) {
      mc.getNetHandler()
          .addToSendQueue(
              new C07PacketPlayerDigging(
                  C07PacketPlayerDigging.Action.STOP_DESTROY_BLOCK,
                  new BlockPos(mc.thePlayer),
                  EnumFacing.UP));
    }
  }

  @Override
  public void onPacket(PacketEvent event) {
    if (miscellaneous.getValue()) {
      if (event.getPacket() instanceof net.minecraft.network.play.client.C17PacketCustomPayload) {
        event.setCancelled(true);
      }
    }
  }
}
