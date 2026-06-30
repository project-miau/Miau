package miau.module.modules.ghost;

import java.util.Objects;
import miau.event.EventTarget;
import miau.event.impl.LeftClickMouseEvent;
import miau.event.impl.TickEvent;
import miau.event.types.EventType;
import miau.event.types.Priority;
import miau.module.Module;
import miau.module.modules.render.Keystrokes;
import miau.property.properties.BooleanProperty;
import miau.property.properties.FloatProperty;
import miau.util.animation.*;
import miau.util.client.*;
import miau.util.math.*;
import miau.util.misc.*;
import miau.util.network.*;
import miau.util.player.*;
import miau.util.render.*;
import miau.util.time.*;
import miau.util.world.*;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.MovingObjectPosition.MovingObjectType;
import net.minecraft.world.WorldSettings.GameType;

public class AutoClicker extends Module {
  private static final Minecraft mc = Minecraft.getMinecraft();
  private boolean clickPending = false;
  private long clickDelay = 0L;
  private boolean blockHitPending = false;
  private long blockHitDelay = 0L;
  public final FloatProperty cps = new FloatProperty("cps", 8.0F, 12.0F, 1.0F, 20.0F);
  public final BooleanProperty blockHit = new BooleanProperty("block-hit", false);
  public final FloatProperty blockHitTicks =
      new FloatProperty("block-hit-ticks", 1.5F, 1.0F, 20.0F, this.blockHit::getValue);
  public final BooleanProperty weaponsOnly = new BooleanProperty("weapons-only", true);
  public final BooleanProperty allowTools =
      new BooleanProperty("allow-tools", false, this.weaponsOnly::getValue);
  public final BooleanProperty breakBlocks = new BooleanProperty("break-blocks", true);
  public final FloatProperty range =
      new FloatProperty("range", 3.0F, 3.0F, 8.0F, this.breakBlocks::getValue);
  public final FloatProperty hitBoxVertical =
      new FloatProperty("hit-box-vertical", 0.1F, 0.0F, 1.0F, this.breakBlocks::getValue);
  public final FloatProperty hitBoxHorizontal =
      new FloatProperty("hit-box-horizontal", 0.2F, 0.0F, 1.0F, this.breakBlocks::getValue);

  private long getNextClickDelay() {
    return 1000L
        / RandomUtil.nextLong(this.cps.getValue().intValue(), this.cps.getSecondValue().intValue());
  }

  private long getBlockHitDelay() {
    return (long) (50.0F * this.blockHitTicks.getValue());
  }

  private boolean isBreakingBlock() {
    return mc.objectMouseOver != null && mc.objectMouseOver.typeOfHit == MovingObjectType.BLOCK;
  }

  private boolean canClick() {
    if (!this.weaponsOnly.getValue()
        || ItemUtil.hasRawUnbreakingEnchant()
        || this.allowTools.getValue() && ItemUtil.isHoldingTool()) {
      if (this.breakBlocks.getValue() && this.isBreakingBlock() && !this.hasValidTarget()) {
        GameType gameType12 = mc.playerController.getCurrentGameType();
        return gameType12 != GameType.SURVIVAL && gameType12 != GameType.CREATIVE;
      } else {
        return true;
      }
    } else {
      return false;
    }
  }

  private boolean isValidTarget(EntityPlayer entityPlayer) {
    if (entityPlayer != mc.thePlayer && entityPlayer != mc.thePlayer.ridingEntity) {
      if (entityPlayer == mc.getRenderViewEntity()
          || entityPlayer == mc.getRenderViewEntity().ridingEntity) {
        return false;
      } else if (entityPlayer.deathTime > 0) {
        return false;
      } else {
        net.minecraft.util.MovingObjectPosition mop =
            miau.util.player.RayCastUtil.rayCast(
                mc.thePlayer.rotationYaw,
                mc.thePlayer.rotationPitch,
                this.range.getValue(),
                Math.max(this.hitBoxHorizontal.getValue(), this.hitBoxVertical.getValue()),
                entityPlayer);
        return mop != null && mop.entityHit == entityPlayer;
      }
    } else {
      return false;
    }
  }

  private boolean hasValidTarget() {
    return mc.theWorld.loadedEntityList.stream()
        .filter(e -> e instanceof EntityPlayer)
        .map(e -> (EntityPlayer) e)
        .anyMatch(this::isValidTarget);
  }

  public AutoClicker() {
    super("AutoClicker", false);
  }

  @EventTarget
  public void onTick(TickEvent event) {
    if (event.getType() == EventType.PRE) {
      if (this.clickDelay > 0L) {
        this.clickDelay -= 50L;
      }
      if (this.blockHitDelay > 0L) {
        this.blockHitDelay -= 50L;
      }
      if (mc.currentScreen != null) {
        this.clickPending = false;
        this.blockHitPending = false;
      } else {
        if (this.clickPending) {
          this.clickPending = false;
          KeyBindUtil.updateKeyState(mc.gameSettings.keyBindAttack.getKeyCode());
        }
        if (this.blockHitPending) {
          this.blockHitPending = false;
          KeyBindUtil.updateKeyState(mc.gameSettings.keyBindUseItem.getKeyCode());
        }
        if (this.isEnabled() && this.canClick() && mc.gameSettings.keyBindAttack.isKeyDown()) {
          if (!mc.thePlayer.isUsingItem()) {
            while (this.clickDelay <= 0L) {
              this.clickPending = true;
              this.clickDelay = this.clickDelay + this.getNextClickDelay();
              KeyBindUtil.setKeyBindState(mc.gameSettings.keyBindAttack.getKeyCode(), false);
              KeyBindUtil.pressKeyOnce(mc.gameSettings.keyBindAttack.getKeyCode());
              Keystrokes.recordLeftClick();
            }
          }
          if (this.blockHit.getValue()
              && this.blockHitDelay <= 0L
              && mc.gameSettings.keyBindUseItem.isKeyDown()
              && ItemUtil.isHoldingSword()) {
            this.blockHitPending = true;
            KeyBindUtil.setKeyBindState(mc.gameSettings.keyBindUseItem.getKeyCode(), false);
            if (!mc.thePlayer.isUsingItem()) {
              this.blockHitDelay = this.blockHitDelay + this.getBlockHitDelay();
              KeyBindUtil.pressKeyOnce(mc.gameSettings.keyBindUseItem.getKeyCode());
            }
          }
        }
      }
    }
  }

  @EventTarget(Priority.LOWEST)
  public void onCLick(LeftClickMouseEvent event) {
    if (this.isEnabled() && !event.isCancelled()) {
      if (!this.clickPending) {
        this.clickDelay = this.clickDelay + this.getNextClickDelay();
      }
    }
  }

  @Override
  public void onEnabled() {
    this.clickDelay = 0L;
    this.blockHitDelay = 0L;
  }

  @Override
  public String[] getSuffix() {
    return Objects.equals(this.cps.getValue(), this.cps.getSecondValue())
        ? new String[] {String.valueOf(this.cps.getValue().intValue())}
        : new String[] {
          String.format(
              "%d-%d", this.cps.getValue().intValue(), this.cps.getSecondValue().intValue())
        };
  }
}
