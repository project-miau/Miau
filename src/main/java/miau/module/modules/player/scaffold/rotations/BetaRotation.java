package miau.module.modules.player.scaffold.rotations;

import miau.event.impl.UpdateEvent;
import miau.module.modules.player.Scaffold;
import miau.util.player.RotationUtil;

public class BetaRotation implements IRotationLogic {
  @Override
  public void handleInitialRotation(
      Scaffold scaffold,
      UpdateEvent event,
      float currentYaw,
      float yawDiffTo180,
      float diagonalYaw) {
    if (scaffold.yaw == -180.0F && scaffold.pitch == 0.0F) {
      float keepYaw = RotationUtil.quantizeAngle(event.getYaw());
      float keepPitch = RotationUtil.quantizeAngle(event.getPitch());
      scaffold.yaw = keepYaw;
      scaffold.pitch = keepPitch;
      scaffold.betaFeature.lastBetaSentYaw = event.getYaw();
      scaffold.betaFeature.lastBetaSentPitch = event.getPitch();
      scaffold.betaFeature.initTicks = 1;
    }
  }
}
