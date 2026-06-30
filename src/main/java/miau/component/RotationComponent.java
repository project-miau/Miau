package miau.component;

import miau.event.EventTarget;
import miau.event.impl.*;
import miau.event.types.EventType;
import miau.management.RotationState;
import miau.util.player.MoveUtil;
import net.minecraft.client.Minecraft;

/**
 * Centralized rotation state + movefix component (Rise-style architecture).
 *
 * <p>Module nào cần silent rotation chỉ cần gọi: RotationComponent.update(yaw, pitch, moveFixMode);
 *
 * <p>Component này tự động handle: - RotationState sync (cho movefix engine) - MoveFix qua
 * StrafeEvent, JumpEvent, MoveInputEvent
 *
 * <p>KHÔNG cần quan tâm event priority — component này KHÔNG set rotation vào UpdateEvent. Module
 * tự set rotation qua event.setRotation() ở priority phù hợp.
 *
 * <p>MoveFix modes: 0 = OFF 1 = MIAU (fixMovement) 2 = TRADITIONAL (chỉ set yaw trên event) 3 =
 * BACKWARDS_SPRINT (check sprint state dựa trên góc)
 */
public final class RotationComponent {

  private static final Minecraft mc = Minecraft.getMinecraft();

  private static boolean active;
  private static int moveFixMode;

  // ================================================================
  //  PUBLIC API
  // ================================================================

  /**
   * Update rotation state. Gọi SAU KHI module đã setRotation() vào event. RotationComponent chỉ lưu
   * state để movefix hoạt động.
   */
  public static void update(float yaw, float pitch, int moveFix) {
    RotationState.applyState(true, yaw, pitch, yaw, 1);
    moveFixMode = moveFix;
    active = true;
  }

  /** Reset rotation state (khi mất target, tắt module, ...). */
  public static void reset() {
    active = false;
    moveFixMode = 0;
    RotationState.applyState(false, 0, 0, 0, 0);
  }

  /** Component đang active? */
  public static boolean isActive() {
    return active;
  }

  // ================================================================
  //  MOVEFIX EVENT HANDLERS
  // ================================================================

  /** MoveFix cho StrafeEvent (MIAU mode 1, TRADITIONAL mode 2). */
  @EventTarget
  public void onStrafe(StrafeEvent event) {
    if (!active) return;
    if (moveFixMode == 1 || moveFixMode == 2) {
      event.setYaw(RotationState.getRotationYawHead());
    }
  }

  /** MoveFix cho JumpEvent (modes 1, 2, 3). */
  @EventTarget
  public void onJump(JumpEvent event) {
    if (!active) return;
    if (moveFixMode == 1 || moveFixMode == 2 || moveFixMode == 3) {
      event.setYaw(RotationState.getRotationYawHead());
    }
  }

  /** MoveFix cho MoveInputEvent (MIAU mode — fixMovement). */
  @EventTarget
  public void onMove(MoveInputEvent event) {
    if (!active) return;
    if (moveFixMode == 1 && RotationState.isActived()) {
      MoveUtil.fixMovement(RotationState.getRotationYawHead());
    }
  }

  /** Backwards Sprint check (mode 3) — xử lý trong UpdateEvent. */
  @EventTarget
  public void onUpdate(miau.event.impl.UpdateEvent event) {
    if (event.getType() != EventType.PRE) return;
    if (!active) return;
    if (moveFixMode != 3) return;

    float moveYaw =
        MoveUtil.adjustYaw(
            mc.thePlayer.rotationYaw,
            mc.thePlayer.movementInput.moveForward,
            mc.thePlayer.movementInput.moveStrafe);
    float serverYaw = RotationState.getRotationYawHead();
    if (Math.abs(net.minecraft.util.MathHelper.wrapAngleTo180_float(serverYaw - moveYaw)) > 45.0F) {
      net.minecraft.client.settings.KeyBinding.setKeyBindState(
          mc.gameSettings.keyBindSprint.getKeyCode(), false);
      mc.thePlayer.setSprinting(false);
    }
  }
}
