package miau.module.modules.combat.killaura.rotation;

import miau.component.RotationComponent;
import miau.event.impl.UpdateEvent;
import miau.module.modules.combat.KillAura;
import miau.util.player.RotationUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.util.MathHelper;

public class NormalRotation extends RotationMode {

  private static final Minecraft mc = Minecraft.getMinecraft();

  public NormalRotation(KillAura killAura) {
    super(killAura, "NORMAL");
  }

  @Override
  public float[] processRotations(
      float[] targetRots, float[] lastRots, double rotSpeed, UpdateEvent event) {
    if (rotSpeed != 0) {
      // Activate RotationComponent for state tracking + movement correction
      RotationComponent.setActive(true, this.killAura.moveFix.getValue());

      // Rise 6.2.4 Legit/Normal rotation with raycast callback for wobble validation
      float[] result =
          RotationUtil.smooth(
              lastRots,
              targetRots,
              rotSpeed,
              this.killAura.getTarget(),
              this.killAura.attackRange.getValue());

      // Vulcan Bypass: apply additional GCD noise after smoothing
      if (this.killAura.vulcanBypass.getValue()) {
        result = applyVulcanGCDNoise(result, lastRots, event);
      }

      // Notify component of new smoothed rotations
      RotationComponent.markSmoothed(result);
      return result;
    } else {
      return lastRots;
    }
  }

  /**
   * Adds subtle GCD-busting noise to prevent Vulcan Aim(Q), Aim(S), Aim(O) detections. The noise is
   * sub-GCD so it won't affect legitimate aim but breaks the perfect yawDifference==0 ||
   * pitchDifference==0 check.
   */
  private float[] applyVulcanGCDNoise(float[] rotations, float[] lastRots, UpdateEvent event) {
    float yaw = rotations[0];
    float pitch = rotations[1];

    // Calculate current GCD step
    float sensitivity = (float) (mc.gameSettings.mouseSensitivity * 0.6F + 0.2F);
    double gcd = sensitivity * sensitivity * sensitivity * 1.2F;
    if (gcd < 0.001) return rotations;

    // Get yaw/pitch differences from last rotation
    float lastYaw = lastRots[0];
    float lastPitch = lastRots[1];
    float deltaYaw = MathHelper.wrapAngleTo180_float(yaw - lastYaw);
    float deltaPitch = pitch - lastPitch;

    // Check if delta is a perfect GCD multiple (triggers AimQ)
    double yawQuotient = deltaYaw / gcd;
    double pitchQuotient = deltaPitch / gcd;
    double yawRemainder = Math.abs(Math.round(yawQuotient) - yawQuotient);
    double pitchRemainder = Math.abs(Math.round(pitchQuotient) - pitchQuotient);

    // If either axis is too perfectly aligned, add sub-GCD perturbation
    if (yawRemainder < 0.001 || pitchRemainder < 0.001 || deltaYaw == 0 || deltaPitch == 0) {
      double yawNoise = (Math.random() - 0.5) * gcd * 0.3;
      double pitchNoise = (Math.random() - 0.5) * gcd * 0.3;

      // Only add noise if it doesn't break raycast
      float testYaw = (float) (yaw + yawNoise);
      float testPitch = MathHelper.clamp_float((float) (pitch + pitchNoise), -90f, 90f);

      // Apply the GCD fix after adding noise
      float[] noisy = RotationUtil.applySensitivityPatch(testYaw, testPitch, lastYaw, lastPitch);
      return new float[] {noisy[0], MathHelper.clamp_float(noisy[1], -90f, 90f)};
    }

    return rotations;
  }
}
