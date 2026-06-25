package myau.module.modules.ghost;

import java.util.Objects;
import myau.event.EventTarget;
import myau.event.impl.MoveInputEvent;
import myau.event.impl.TickEvent;
import myau.event.types.EventType;
import myau.event.types.Priority;
import myau.module.Module;
import myau.property.properties.BooleanProperty;
import myau.property.properties.FloatProperty;
import myau.util.player.ItemUtil;
import myau.util.player.MoveUtil;
import myau.util.player.PlayerUtil;
import net.minecraft.client.Minecraft;
import org.apache.commons.lang3.RandomUtils;
import org.lwjgl.input.Keyboard;

public class Eagle extends Module {
  private static final Minecraft mc = Minecraft.getMinecraft();
  private int sneakDelay = 0;
  public final FloatProperty delayMs = new FloatProperty("delay", 2.0F, 3.0F, 0.0F, 10.0F);
  public final BooleanProperty directionCheck = new BooleanProperty("direction-check", true);
  public final BooleanProperty jumpCheck = new BooleanProperty("jump-check", true);
  public final BooleanProperty pitchCheck = new BooleanProperty("pitch-check", true);
  public final BooleanProperty blocksOnly = new BooleanProperty("blocks-only", true);
  public final BooleanProperty sneakOnly = new BooleanProperty("sneaking-only", false);

  private boolean canMoveSafely() {
    double[] offset = MoveUtil.predictMovement();
    return PlayerUtil.canMove(mc.thePlayer.motionX + offset[0], mc.thePlayer.motionZ + offset[1]);
  }

  private boolean shouldSneak() {
    if (this.directionCheck.getValue() && mc.gameSettings.keyBindForward.isKeyDown()) {
      return false;
    } else if (this.jumpCheck.getValue() && mc.gameSettings.keyBindJump.isKeyDown()) {
      return false;
    } else if (this.pitchCheck.getValue() && mc.thePlayer.rotationPitch < 69.0F) {
      return false;
    } else if (sneakOnly.getValue()
        && !Keyboard.isKeyDown(mc.gameSettings.keyBindSneak.getKeyCode())) {
      return false;
    } else {
      return (!this.blocksOnly.getValue() || ItemUtil.isHoldingBlock()) && mc.thePlayer.onGround;
    }
  }

  public Eagle() {
    super("Eagle", false);
  }

  @EventTarget(Priority.LOWEST)
  public void onTick(TickEvent event) {
    if (this.isEnabled() && event.getType() == EventType.PRE) {
      if (this.sneakDelay > 0) {
        this.sneakDelay--;
      }
      if (this.sneakDelay == 0 && this.canMoveSafely()) {
        this.sneakDelay =
            RandomUtils.nextInt(
                this.delayMs.getValue().intValue(), this.delayMs.getSecondValue().intValue() + 1);
      }
    }
  }

  @EventTarget(Priority.LOWEST)
  public void onMoveInput(MoveInputEvent event) {
    if (this.isEnabled() && mc.currentScreen == null) {

      if (sneakOnly.getValue()
          && Keyboard.isKeyDown(mc.gameSettings.keyBindSneak.getKeyCode())
          && shouldSneak()) {
        mc.thePlayer.movementInput.sneak = false;
        mc.thePlayer.movementInput.moveForward /= 0.3F;
        mc.thePlayer.movementInput.moveStrafe /= 0.3F;
      }

      if (!mc.thePlayer.movementInput.sneak) {
        if (this.shouldSneak() && (this.sneakDelay > 0 || this.canMoveSafely())) {
          mc.thePlayer.movementInput.sneak = true;
          mc.thePlayer.movementInput.moveStrafe *= 0.3F;
          mc.thePlayer.movementInput.moveForward *= 0.3F;
        }
      }
    }
  }

  @Override
  public void onDisabled() {
    this.sneakDelay = 0;
  }

  @Override
  public String[] getSuffix() {
    return Objects.equals(this.delayMs.getValue(), this.delayMs.getSecondValue())
        ? new String[] {String.valueOf(this.delayMs.getValue().intValue())}
        : new String[] {
          String.format(
              "%d-%d", this.delayMs.getValue().intValue(), this.delayMs.getSecondValue().intValue())
        };
  }
}
