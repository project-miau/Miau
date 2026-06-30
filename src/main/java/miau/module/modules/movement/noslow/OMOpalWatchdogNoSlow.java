package miau.module.modules.movement.noslow;

import miau.event.impl.PacketEvent;
import miau.event.impl.RightClickMouseEvent;
import miau.event.impl.UpdateEvent;
import miau.event.types.EventType;
import miau.mixin.IAccessorKeyBinding;
import miau.mixin.IAccessorPlayerControllerMP;
import miau.module.modules.movement.NoSlow;
import miau.util.network.PacketUtil;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemSword;
import net.minecraft.network.play.client.C07PacketPlayerDigging;
import net.minecraft.network.play.client.C08PacketPlayerBlockPlacement;
import net.minecraft.network.play.client.C09PacketHeldItemChange;
import net.minecraft.network.play.server.S19PacketEntityStatus;
import net.minecraft.util.BlockPos;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.MovingObjectPosition;

/**
 * Port of Opal's WatchdogNoSlow (Fabric 1.21 -> Forge 1.8.9).
 *
 * <p>Core mechanic: 3-tick block/release tap-cycle. - Right mouse held: cycle block/release every 3
 * ticks (age+2) - On cycle tick: release then set runThisTick; off ticks: re-block - On cycle tick
 * (if right-click held + blockable action): --> If not using-item or not blocking: stopUse=true,
 * force item-use (right-click press) --> If already using+blocking: cancel right-click (KeyBinding
 * release) - stopUse on next tick: stopUsingItem + re-block (1-tick item use flicker) - Release
 * triggers: slot change (C09 sent), entity status=9 (finish eat), screen/overlay opened, onDisable.
 */
public class OMOpalWatchdogNoSlow extends NoSlowMode {

  private int nextCycleTick = -1;
  private boolean runThisTick = false;
  private boolean stopUse = false;
  private boolean blocking = false;
  private int slotChangeTick = -1;

  public OMOpalWatchdogNoSlow(String name, NoSlow parent) {
    super(name, parent);
  }

  @Override
  public void onUpdate(UpdateEvent event) {
    if (event.getType() != EventType.PRE) return;
    if (mc.thePlayer == null || mc.currentScreen != null || mc.theWorld == null) {
      resetCycle();
      release();
      return;
    }

    // ---- stopUse: finish 1-tick item-use flicker ----
    if (stopUse) {
      if (mc.thePlayer.isUsingItem()) {
        block();
        mc.thePlayer.stopUsingItem();
      }
      stopUse = false;
    } else if (!parent.isSwordActive()) {
      if (!mc.thePlayer.isUsingItem()) {
        release();
      }
    }

    // ---- Right-click state machine ----
    int age = mc.thePlayer.ticksExisted;

    // Simulating: rightButton.isPressed() -> we treat right-click as "active"
    // when the player is holding right-click OR we force-pressed it ourselves.
    boolean rightPressed = mc.gameSettings.keyBindUseItem.isKeyDown();

    if (parent.isSwordActive()) {
      if (rightPressed) {
        if (nextCycleTick < 0) {
          nextCycleTick = age;
        }

        if (age >= nextCycleTick) {
          // Cycle tick: release block
          if (blocking) {
            release();
          }
          runThisTick = true;
          nextCycleTick = age + 2; // happen again in 3 ticks
        } else if (!blocking) {
          // Off-cycle: keep blocked
          block();
        }
      } else {
        // Right-click not held
        resetCycle();
        if (!mc.thePlayer.isUsingItem()) {
          release();
        }
      }

      // ---- runThisTick: simulate right-click press or cancel ----
      if (runThisTick && parent.isSwordActive()) {
        if (rightPressed) {
          if (!mc.thePlayer.isUsingItem() || !blocking) {
            // Check looking at interactable block or breaking -> skip
            if (mc.objectMouseOver != null
                && mc.objectMouseOver.typeOfHit == MovingObjectPosition.MovingObjectType.BLOCK
                && mc.objectMouseOver.getBlockPos() != null) {
              net.minecraft.block.Block block =
                  mc.theWorld.getBlockState(mc.objectMouseOver.getBlockPos()).getBlock();
              IAccessorPlayerControllerMP accessor =
                  (IAccessorPlayerControllerMP) mc.playerController;
              if (isInteractableBlock(block) || accessor.getIsHittingBlock()) {
                runThisTick = false;
                return;
              }
            }

            // stopUse: will trigger item-stop on next update
            this.stopUse = true;
            // Force right-click press (like Opal setPressed)
            ((IAccessorKeyBinding) mc.gameSettings.keyBindUseItem).setPressed(true);
          } else {
            // Using item and already blocking: cancel right-click
            ((IAccessorKeyBinding) mc.gameSettings.keyBindUseItem).setPressed(false);
          }
        } else {
          this.stopUse = false;
        }
        runThisTick = false;
      }
    } else {
      // Non-sword items: cancel slowdown
      if (parent.isFoodActive() || parent.isBowActive() || parent.isPotionActive()) {
        mc.thePlayer.movementInput.moveForward *= 5.0f;
        mc.thePlayer.movementInput.moveStrafe *= 5.0f;
      }
    }
  }

  @Override
  public void onRightClick(RightClickMouseEvent event) {
    if (mc.thePlayer == null) return;
    if (parent.isSwordActive()) {
      event.setCancelled(true);
    }
  }

  @Override
  public void onPacket(PacketEvent event) {
    if (mc.thePlayer == null) return;

    // Detect slot change (C09)
    if (event.getPacket() instanceof C09PacketHeldItemChange) {
      if (mc.thePlayer.ticksExisted - slotChangeTick != 1) {
        release();
        resetCycle();
      }
      slotChangeTick = mc.thePlayer.ticksExisted;
    }

    // Server says player finished eating (status=9)
    if (event.getPacket() instanceof S19PacketEntityStatus) {
      S19PacketEntityStatus statusPacket = (S19PacketEntityStatus) event.getPacket();
      if (statusPacket.getEntity(mc.theWorld) == mc.thePlayer && statusPacket.getOpCode() == 9) {
        release();
      }
    }
  }

  @Override
  public void onDisable() {
    release();
    resetCycle();
  }

  // ============ helpers ============

  private void block() {
    if (mc.thePlayer.getHeldItem() != null
        && mc.thePlayer.getHeldItem().getItem() instanceof ItemSword) {
      PacketUtil.sendPacket(new C08PacketPlayerBlockPlacement(mc.thePlayer.getHeldItem()));
      mc.thePlayer.setItemInUse(
          mc.thePlayer.getHeldItem(), mc.thePlayer.getHeldItem().getMaxItemUseDuration());
      blocking = true;
    }
  }

  private void release() {
    PacketUtil.sendPacket(
        new C07PacketPlayerDigging(
            C07PacketPlayerDigging.Action.RELEASE_USE_ITEM, BlockPos.ORIGIN, EnumFacing.DOWN));
    mc.thePlayer.stopUsingItem();
    blocking = false;
  }

  private void resetCycle() {
    this.stopUse = false;
    this.runThisTick = false;
    this.nextCycleTick = -1;
  }

  private boolean isInteractableBlock(net.minecraft.block.Block block) {
    return block instanceof net.minecraft.block.BlockDoor
        || block instanceof net.minecraft.block.BlockChest
        || block instanceof net.minecraft.block.BlockFurnace
        || block instanceof net.minecraft.block.BlockWorkbench
        || block instanceof net.minecraft.block.BlockAnvil
        || block instanceof net.minecraft.block.BlockEnchantmentTable
        || block instanceof net.minecraft.block.BlockBrewingStand
        || block instanceof net.minecraft.block.BlockBeacon
        || block instanceof net.minecraft.block.BlockLever
        || block instanceof net.minecraft.block.BlockButtonWood
        || block instanceof net.minecraft.block.BlockButtonStone
        || block instanceof net.minecraft.block.BlockTrapDoor
        || block instanceof net.minecraft.block.BlockFenceGate
        || block instanceof net.minecraft.block.BlockRedstoneRepeater
        || block instanceof net.minecraft.block.BlockRedstoneComparator
        || block instanceof net.minecraft.block.BlockHopper
        || block instanceof net.minecraft.block.BlockDropper
        || block instanceof net.minecraft.block.BlockDispenser
        || block instanceof net.minecraft.block.BlockEnderChest
        || block == Blocks.anvil
        || block == Blocks.enchanting_table
        || block == Blocks.brewing_stand;
  }
}
