package miau.module.modules.misc.cheatdetector;

import miau.Miau;
import miau.module.modules.misc.CheatDetector;
import miau.module.modules.misc.cheatdetector.impl.combat.*;
import miau.module.modules.misc.cheatdetector.impl.movement.*;
import miau.module.modules.misc.cheatdetector.impl.world.*;
import miau.module.modules.misc.cheatdetector.impl.world.placementanalysis.*;
import net.minecraft.entity.player.EntityPlayer;

public class CheatDetectorData {
  // combat
  public AutoBlockCheck autoBlockCheck = new AutoBlockCheck();
  public KillauraCheck killauraCheck = new KillauraCheck();
  public AttackRaytraceCheck attackRaytraceCheck = new AttackRaytraceCheck();
  public ClickSpeedLimiterCheck clickSpeedLimiterCheck = new ClickSpeedLimiterCheck();
  public ClickPatternsCheck clickPatternsCheck = new ClickPatternsCheck();
  public HeuristicsCheck heuristicsCheck = new HeuristicsCheck();

  // movement
  public NoSlowCheck noSlowCheck = new NoSlowCheck();
  public PhysicsCheck physicsCheck = new PhysicsCheck();
  public TimerCheck timerCheck = new TimerCheck();

  // world
  public BreakSpeedLimiterCheck breakSpeedLimiterCheck = new BreakSpeedLimiterCheck();
  public InteractionRaytraceCheck interactionRaytraceCheck = new InteractionRaytraceCheck();

  // placementanalysis
  public ScaffoldSneakCheck scaffoldSneakCheck = new ScaffoldSneakCheck();
  public ScaffoldAngleSnapCheck scaffoldAngleSnapCheck = new ScaffoldAngleSnapCheck();
  public ScaffoldRotationSpeedCheck scaffoldRotationSpeedCheck = new ScaffoldRotationSpeedCheck();
  public ScaffoldJumpAndPlaceCheck scaffoldJumpAndPlaceCheck = new ScaffoldJumpAndPlaceCheck();

  public void onUpdate(EntityPlayer player) {
    CheatDetector cd = (CheatDetector) Miau.moduleManager.getModule(CheatDetector.class);
    if (cd == null) return;

    if (cd.isCheckEnabled(autoBlockCheck.getName())) autoBlockCheck.onUpdate(player);
    if (cd.isCheckEnabled(killauraCheck.getName())) killauraCheck.onUpdate(player);
    if (cd.isCheckEnabled(attackRaytraceCheck.getName())) attackRaytraceCheck.onUpdate(player);
    if (cd.isCheckEnabled(clickSpeedLimiterCheck.getName()))
      clickSpeedLimiterCheck.onUpdate(player);
    if (cd.isCheckEnabled(clickPatternsCheck.getName())) clickPatternsCheck.onUpdate(player);
    if (cd.isCheckEnabled(heuristicsCheck.getName())) heuristicsCheck.onUpdate(player);

    if (cd.isCheckEnabled(noSlowCheck.getName())) noSlowCheck.onUpdate(player);
    if (cd.isCheckEnabled(physicsCheck.getName())) physicsCheck.onUpdate(player);
    if (cd.isCheckEnabled(timerCheck.getName())) timerCheck.onUpdate(player);

    if (cd.isCheckEnabled(breakSpeedLimiterCheck.getName()))
      breakSpeedLimiterCheck.onUpdate(player);
    if (cd.isCheckEnabled(interactionRaytraceCheck.getName()))
      interactionRaytraceCheck.onUpdate(player);

    if (cd.isCheckEnabled(scaffoldSneakCheck.getName())) scaffoldSneakCheck.onUpdate(player);
    if (cd.isCheckEnabled(scaffoldAngleSnapCheck.getName()))
      scaffoldAngleSnapCheck.onUpdate(player);
    if (cd.isCheckEnabled(scaffoldRotationSpeedCheck.getName()))
      scaffoldRotationSpeedCheck.onUpdate(player);
    if (cd.isCheckEnabled(scaffoldJumpAndPlaceCheck.getName()))
      scaffoldJumpAndPlaceCheck.onUpdate(player);
  }
}
