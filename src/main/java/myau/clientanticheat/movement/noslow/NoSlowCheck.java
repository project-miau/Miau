package myau.clientanticheat.movement.noslow;

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

public class NoSlowCheck {
  private final Map<String, CheckBuffer> sprintBuffers = new HashMap<>();
  private final Map<String, CheckBuffer> speedBuffers = new HashMap<>();
  private final Map<String, CheckBuffer> accelerationBuffers = new HashMap<>();
  private final Map<String, CheckBuffer> directionBuffers = new HashMap<>();
  private final Map<String, CheckBuffer> speedRatioBuffers = new HashMap<>();
  private final Map<String, Integer> consecutiveViolationTicks = new HashMap<>();
  private final Map<String, Double> lastUsingItemSpeed = new HashMap<>();
  private final Map<String, Double> lastNotUsingItemSpeed = new HashMap<>();

  private static final double ITEM_USE_THRESHOLD = 0.17D;
  private static final int CONSECUTIVE_VIOLATIONS_FOR_FLAG = 3;
  private static final double INSTANT_BLOCK_THRESHOLD = 0.25D;
  private static final double ACCELERATION_THRESHOLD = 0.0015D;

  public void check(
      EntityPlayer player, PlayerCheckData data, long currentTick, ClientAntiCheatContext context) {
    String name = player.getName();
    if (name == null) return;

    ItemStack heldItem = player.getHeldItem();
    boolean usingSlowItem =
        this.isSlowItem(heldItem)
            && (player.isBlocking() || player.isEating() || player.isUsingItem());
    boolean usingItemRaw = player.isUsingItem() || player.isBlocking() || player.isEating();

    CheckBuffer sprintBuffer = this.sprintBuffers.computeIfAbsent(name, key -> new CheckBuffer());
    CheckBuffer speedBuffer = this.speedBuffers.computeIfAbsent(name, key -> new CheckBuffer());
    CheckBuffer accelerationBuffer =
        this.accelerationBuffers.computeIfAbsent(name, key -> new CheckBuffer());
    CheckBuffer directionBuffer =
        this.directionBuffers.computeIfAbsent(name, key -> new CheckBuffer());
    CheckBuffer speedRatioBuffer =
        this.speedRatioBuffers.computeIfAbsent(name, key -> new CheckBuffer());

    if (!usingSlowItem || this.isExempt(player, data)) {
      sprintBuffer.decay(0.5D);
      speedBuffer.decay(0.5D);
      accelerationBuffer.decay(0.3D);
      directionBuffer.decay(0.3D);
      speedRatioBuffer.decay(0.3D);
      this.consecutiveViolationTicks.put(name, 0);
      return;
    }

    int ticksUsing = data != null ? data.usingItemTicks : 0;
    double horizontalSpeed =
        data != null
            ? data.horizontalDelta
            : Math.sqrt(player.motionX * player.motionX + player.motionZ * player.motionZ);

    double expected = getItemSpecificSpeed(player, heldItem, data);

    if (horizontalSpeed > INSTANT_BLOCK_THRESHOLD && data != null) {
      double acceleration = Math.abs(horizontalSpeed - data.lastHorizontalDelta);
      if (acceleration < ACCELERATION_THRESHOLD) {
        accelerationBuffer.decay(0.2D);
      } else if (acceleration > 0.05D && ticksUsing > 3) {
        if (accelerationBuffer.flag(1.5D, 4.0D)) {
          context.receiveSignal(name, "Noslow (Acceleration)");
          accelerationBuffer.reset();
        }
      } else {
        accelerationBuffer.decay(0.1D);
      }
    }

    if (data != null) {
      if (usingItemRaw) {
        this.lastUsingItemSpeed.put(name, horizontalSpeed);
      } else {
        this.lastNotUsingItemSpeed.put(name, horizontalSpeed);
      }

      Double usingSpeed = this.lastUsingItemSpeed.get(name);
      Double notUsingSpeed = this.lastNotUsingItemSpeed.get(name);
      if (usingSpeed != null && notUsingSpeed != null && notUsingSpeed > 0.1D) {
        double ratio = usingSpeed / notUsingSpeed;
        if (ratio > 0.8D && ticksUsing > 3) {
          if (speedRatioBuffer.flag(1.5D, 4.0D)) {
            context.receiveSignal(name, "Noslow (Speed Ratio)");
            speedRatioBuffer.reset();
          }
        } else {
          speedRatioBuffer.decay(0.2D);
        }
      }
    }

    if (data != null && ticksUsing > 2 && horizontalSpeed > INSTANT_BLOCK_THRESHOLD) {
      float yawDelta = data.yawDelta;
      float pitchDelta = data.pitchDelta;
      if (yawDelta > 15.0F && pitchDelta > 5.0F) {
        if (directionBuffer.flag(1.5D, 4.0D)) {
          context.receiveSignal(name, "Noslow (Direction Change)");
          directionBuffer.reset();
        }
      } else {
        directionBuffer.decay(0.25D);
      }
    }

    if (ticksUsing > 4 && player.isSprinting()) {
      if (sprintBuffer.flag(1.0D, 2.5D)) {
        context.receiveSignal(name, "Noslow");
        sprintBuffer.reset();
      }
    } else {
      sprintBuffer.decay(0.35D);
    }

    if (ticksUsing > 6 && horizontalSpeed > expected) {
      double over = horizontalSpeed - expected;

      int consecutive = this.consecutiveViolationTicks.getOrDefault(name, 0) + 1;
      this.consecutiveViolationTicks.put(name, consecutive);

      double weight = 1.0D + Math.min(1.5D, over * 6.0D);
      if (consecutive >= CONSECUTIVE_VIOLATIONS_FOR_FLAG) {
        weight += 0.5D;
      }

      if (speedBuffer.flag(weight, 4.0D)) {
        context.receiveSignal(name, "Noslow (Speed)");
        speedBuffer.reset();
      }
    } else {
      speedBuffer.decay(0.3D);
      this.consecutiveViolationTicks.put(name, 0);
    }
  }

  private double getItemSpecificSpeed(EntityPlayer player, ItemStack stack, PlayerCheckData data) {
    double baseExpected = player.onGround ? 0.155D : 0.245D;

    if (stack != null && stack.getItem() != null) {
      if (stack.getItem() instanceof ItemBow) {
        baseExpected *= 0.18D;
      } else if (stack.getItem() instanceof ItemSword) {
        baseExpected *= 0.55D;
      } else if (stack.getItem() instanceof ItemFood) {
        baseExpected *= 0.35D;
      } else if (stack.getItem() instanceof ItemPotion) {
        baseExpected *= 0.35D;
      } else if (stack.getItem() instanceof ItemBlock) {
        baseExpected *= 0.70D;
      }
    }

    if (player.isPotionActive(net.minecraft.potion.Potion.moveSpeed)) {
      baseExpected +=
          0.035D
              * (player.getActivePotionEffect(net.minecraft.potion.Potion.moveSpeed).getAmplifier()
                  + 1);
    }

    return baseExpected;
  }

  private boolean isExempt(EntityPlayer player, PlayerCheckData data) {
    return player.hurtTime > 0
        || player.hurtResistantTime > 10
        || player.isCollidedHorizontally
        || player.isInWater()
        || player.isInLava()
        || player.isOnLadder()
        || player.isRiding()
        || player.capabilities.isFlying
        || data != null && data.recentlyTeleported();
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
    this.sprintBuffers.clear();
    this.speedBuffers.clear();
    this.accelerationBuffers.clear();
    this.directionBuffers.clear();
    this.speedRatioBuffers.clear();
    this.consecutiveViolationTicks.clear();
    this.lastUsingItemSpeed.clear();
    this.lastNotUsingItemSpeed.clear();
  }
}
