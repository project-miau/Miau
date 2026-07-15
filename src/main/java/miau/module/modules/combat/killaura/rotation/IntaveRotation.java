package miau.module.modules.combat.killaura.rotation;

import miau.component.RotationComponent;
import miau.event.impl.UpdateEvent;
import miau.module.modules.combat.KillAura;
import miau.util.player.RotationUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.util.MathHelper;

public class IntaveRotation extends RotationMode {

  private static final Minecraft mc = Minecraft.getMinecraft();
  private double currentYawOffset = 0.0;
  private double currentPitchOffset = 0.0;
  private double targetYawOffset = 0.0;
  private double targetPitchOffset = 0.0;
  private int offsetChangeTimer = 0;

  // Pitch sine modulation
  private double pitchWavePhase = 0.0;
  private double pitchWaveFreq = 0.2;

  // Period counter
  private int tick = 0;

  public IntaveRotation(KillAura killAura) {
    super(killAura, "INTAVE");
  }

  @Override
  public float[] processRotations(
      float[] targetRots, float[] lastRots, double rotSpeed, UpdateEvent event) {
    if (rotSpeed == 0) return lastRots;

    RotationComponent.setActive(true, this.killAura.moveFix.getValue());
    tick++;
    offsetChangeTimer--;
    if (offsetChangeTimer <= 0) {
      pickNewOffsetTargets(targetRots, lastRots);
      offsetChangeTimer = 3 + (int) (Math.random() * 6);
    }

    double yawLerp = 0.25 + Math.random() * 0.35;
    double pitchLerp = 0.15 + Math.random() * 0.25;
    currentYawOffset += (targetYawOffset - currentYawOffset) * yawLerp;
    currentPitchOffset += (targetPitchOffset - currentPitchOffset) * pitchLerp;

    pitchWavePhase += pitchWaveFreq + (Math.random() - 0.5) * 0.05;
    if (tick % 80 == 0) {
      pitchWaveFreq = 0.1 + Math.random() * 0.3;
    }
    double pitchSine = MathHelper.sin((float) pitchWavePhase) * 0.5;
    double totalPitchOffset = currentPitchOffset + pitchSine;

    float adjYaw = (float) (targetRots[0] + currentYawOffset);
    float adjPitch = MathHelper.clamp_float((float) (targetRots[1] + totalPitchOffset), -90f, 90f);

    double speedMultiplier = 0.6 + Math.random() * 0.8;
    double yawDist = Math.abs(MathHelper.wrapAngleTo180_float(adjYaw - lastRots[0]));
    double pitchDist = Math.abs(adjPitch - lastRots[1]);
    double distFactor = Math.min(1.0, Math.sqrt(yawDist * yawDist + pitchDist * pitchDist) / 5.0);
    double effectiveSpeed = rotSpeed * speedMultiplier * (0.8 + 0.4 * distFactor);

    float yaw = lastRots[0];
    float pitch = lastRots[1];

    if (effectiveSpeed > 0) {
      double deltaYaw = MathHelper.wrapAngleTo180_float(adjYaw - yaw);
      double deltaPitch = adjPitch - pitch;
      double distance = Math.sqrt(deltaYaw * deltaYaw + deltaPitch * deltaPitch);

      if (distance > 0.001) {
        double distYaw = Math.abs(deltaYaw / distance);
        double distPitch = Math.abs(deltaPitch / distance);
        double maxYaw = effectiveSpeed * distYaw;
        double maxPitch = effectiveSpeed * distPitch;
        float moveYaw = (float) Math.max(Math.min(deltaYaw, maxYaw), -maxYaw);
        float movePitch = (float) Math.max(Math.min(deltaPitch, maxPitch), -maxPitch);
        yaw += moveYaw;
        pitch += movePitch;
      }
    }

    int iterations = (int) (Minecraft.getDebugFPS() / 20 + Math.random() * 10);
    for (int i = 1; i <= iterations; i++) {
      if (Math.abs(yaw - lastRots[0]) + Math.abs(pitch - lastRots[1]) > 0.0001) {
        yaw += (float) ((Math.random() - 0.5) / 1000.0);
        pitch -= (float) (Math.random() / 200.0);
      }
      float[] fixed = RotationUtil.applySensitivityPatch(yaw, pitch, lastRots[0], lastRots[1]);
      yaw = fixed[0];
      pitch = Math.max(-90f, Math.min(90f, fixed[1]));
    }

    float[] result = new float[] {yaw, pitch};
    RotationComponent.markSmoothed(result);
    return result;
  }


  private void pickNewOffsetTargets(float[] targetRots, float[] lastRots) {
    if (Math.random() < 0.2) {
      targetYawOffset = (Math.random() < 0.5 ? -1 : 1) * (3.5 + Math.random() * 2.0);
    } else {
      targetYawOffset += (Math.random() - 0.5) * 2.0;
      targetYawOffset = Math.max(-3.5, Math.min(3.5, targetYawOffset));
    }
    targetPitchOffset = (Math.random() - 0.5) * 8.0;
  }
}
