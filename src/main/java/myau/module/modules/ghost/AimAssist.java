package myau.module.modules.ghost;

import java.util.List;
import java.util.stream.Collectors;
import myau.event.EventTarget;
import myau.event.impl.UpdateEvent;
import myau.event.types.EventType;
import myau.module.Module;
import myau.property.properties.BooleanProperty;
import myau.property.properties.FloatProperty;
import myau.property.properties.IntProperty;
import myau.util.player.RotationUtil;
import myau.util.player.TeamUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.item.ItemSword;
import net.minecraft.util.MathHelper;
import net.minecraft.util.MovingObjectPosition;

public class AimAssist extends Module {
  private static final Minecraft mc = Minecraft.getMinecraft();

  public final FloatProperty speed = new FloatProperty("speed", 2.0F, 1.0F, 10.0F);
  public final BooleanProperty requireSwinging = new BooleanProperty("require-swinging", true);
  public final BooleanProperty sticky = new BooleanProperty("sticky", false);
  public final BooleanProperty mouseMovement = new BooleanProperty("require-mouse-movement", false);
  public final BooleanProperty limitItems = new BooleanProperty("limit-items", false);
  public final BooleanProperty aimWhilstOnTarget =
      new BooleanProperty("aim-whilst-on-target", false);
  public final IntProperty fov = new IntProperty("fov", 90, 0, 180);
  public final BooleanProperty player = new BooleanProperty("player", true);
  public final BooleanProperty invisibles = new BooleanProperty("invisibles", false);
  public final BooleanProperty teams = new BooleanProperty("player-teammates", true);

  private EntityLivingBase target;
  private float moveYaw;

  public AimAssist() {
    super("AimAssist", false);
  }

  @EventTarget
  public void onUpdate(UpdateEvent event) {
    if (event.getType() == EventType.PRE) {
      double range = 4.0;
      moveYaw = 0.0f;

      List<EntityLivingBase> targets =
          mc.theWorld.loadedEntityList.stream()
              .filter(entity -> entity instanceof EntityLivingBase)
              .map(entity -> (EntityLivingBase) entity)
              .filter(entity -> entity != mc.thePlayer && entity.isEntityAlive())
              .filter(entity -> mc.thePlayer.getDistanceToEntity(entity) <= range)
              .filter(
                  entity -> {
                    if (entity instanceof net.minecraft.entity.player.EntityPlayer) {
                      if (!player.getValue()) return false;
                      if (!teams.getValue()
                          && TeamUtil.isSameTeam((net.minecraft.entity.player.EntityPlayer) entity))
                        return false;
                      return true;
                    }
                    return false;
                  })
              .filter(entity -> !entity.isInvisible() || invisibles.getValue())
              .sorted(
                  (e1, e2) ->
                      Float.compare(
                          mc.thePlayer.getDistanceToEntity(e1),
                          mc.thePlayer.getDistanceToEntity(e2)))
              .collect(Collectors.toList());

      if (targets.isEmpty()) {
        return;
      }

      target = targets.get(0);

      if (target == null
          || myau.util.player.RayCastUtil.rayCast(
                      mc.thePlayer.rotationYaw, mc.thePlayer.rotationPitch, range)
                  .typeOfHit
              != MovingObjectPosition.MovingObjectType.ENTITY) {
        return;
      }

      float[] rotationsToTarget =
          RotationUtil.getRotationsToBox(
              target.getEntityBoundingBox(),
              mc.thePlayer.rotationYaw,
              mc.thePlayer.rotationPitch,
              180,
              1);
      if (Math.abs(MathHelper.wrapAngleTo180_float(rotationsToTarget[0] - mc.thePlayer.rotationYaw))
          > fov.getValue()) {
        return;
      }

      if (limitItems.getValue()
          && (mc.thePlayer.getHeldItem() == null
              || !(mc.thePlayer.getHeldItem().getItem() instanceof ItemSword))) {
        return;
      }

      float diffYaw =
          MathHelper.wrapAngleTo180_float(rotationsToTarget[0] - mc.thePlayer.rotationYaw);
      moveYaw =
          Math.max(-speed.getValue(), Math.min(speed.getValue(), diffYaw))
              * (sticky.getValue() ? 10 : 1)
              / Math.max(1, Minecraft.getDebugFPS())
              * 100;
    } else if (event.getType() == EventType.POST) {
      if (moveYaw == 0.0f) return;

      if (((mc.mouseHelper.deltaX != 0 || mc.mouseHelper.deltaY != 0) || !mouseMovement.getValue())
          && myau.util.player.RayCastUtil.rayCast(
                      mc.thePlayer.rotationYaw, mc.thePlayer.rotationPitch, 3.0f)
                  .typeOfHit
              != MovingObjectPosition.MovingObjectType.ENTITY
          && mc.objectMouseOver.typeOfHit != MovingObjectPosition.MovingObjectType.BLOCK
          && (!requireSwinging.getValue() || mc.thePlayer.isSwingInProgress)) {

        final float f = mc.gameSettings.mouseSensitivity * 0.6F + 0.2F;
        final float gcd = f * f * f * 8.0F;

        float f2 = (mc.mouseHelper.deltaX + (moveYaw - mc.mouseHelper.deltaX)) * gcd;
        mc.thePlayer.setAngles(f2, 0);
      }
    }
  }
}
