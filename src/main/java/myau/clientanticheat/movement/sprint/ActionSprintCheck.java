package myau.clientanticheat.movement.sprint;

import java.util.HashMap;
import java.util.Map;
import myau.clientanticheat.CheckBuffer;
import myau.clientanticheat.ClientAntiCheatContext;
import myau.clientanticheat.PlayerCheckData;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemBow;
import net.minecraft.item.ItemFood;
import net.minecraft.item.ItemPotion;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemSword;

public class ActionSprintCheck {
  private final Map<String, CheckBuffer> buffers = new HashMap<>();

  public void check(EntityPlayer player, PlayerCheckData data, ClientAntiCheatContext context) {
    String name = player.getName();
    if (name == null || data == null) return;

    CheckBuffer buffer = this.buffers.computeIfAbsent(name, key -> new CheckBuffer());

    if (isExempt(player, data)) {
      buffer.decay(0.5D);
      return;
    }

    ItemStack heldItem = player.getHeldItem();
    boolean usingSlowItem =
        this.isSlowItem(heldItem)
            && (player.isBlocking() || player.isEating() || player.isUsingItem());

    if (usingSlowItem && player.isSprinting() && data.usingItemTicks > 3) {
      if (buffer.flag(1.0D, 3.0D)) {
        context.receiveSignal(name, "OmniSprint (Action)");
        buffer.reset();
      }
    } else {
      buffer.decay(0.2D);
    }
  }

  private boolean isExempt(EntityPlayer player, PlayerCheckData data) {
    return player.isDead
        || player.hurtTime > 0
        || player.ticksExisted < 40
        || data.recentlyTeleported()
        || player.isInWater()
        || player.isInLava()
        || player.isOnLadder()
        || player.isRiding()
        || player.capabilities.isFlying;
  }

  private boolean isSlowItem(ItemStack stack) {
    if (stack == null || stack.getItem() == null) {
      return false;
    }
    return stack.getItem() instanceof ItemSword
        || stack.getItem() instanceof ItemBow
        || stack.getItem() instanceof ItemFood
        || stack.getItem() instanceof ItemPotion
        || stack.getItem() instanceof ItemBlock;
  }

  public void reset() {
    this.buffers.clear();
  }
}
