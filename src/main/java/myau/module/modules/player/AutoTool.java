package myau.module.modules.player;

import java.awt.Color;
import myau.Myau;
import myau.event.EventTarget;
import myau.event.impl.Render2DEvent;
import myau.event.impl.TickEvent;
import myau.event.types.EventType;
import myau.event.types.Priority;
import myau.module.Module;
import myau.module.modules.combat.KillAura;
import myau.module.modules.render.HUD;
import myau.property.properties.BooleanProperty;
import myau.property.properties.ModeProperty;
import myau.util.player.ItemUtil;
import myau.util.player.TeamUtil;
import myau.util.shader.RoundedUtils;
import net.minecraft.block.Block;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.item.ItemStack;
import net.minecraft.network.play.client.C09PacketHeldItemChange;
import net.minecraft.util.BlockPos;
import net.minecraft.util.MovingObjectPosition.MovingObjectType;

public class AutoTool extends Module {
  private static final Minecraft mc = Minecraft.getMinecraft();

  public final ModeProperty mode = new ModeProperty("Mode", 0, new String[] {"Silent", "Switch"});
  public final BooleanProperty switchBack =
      new BooleanProperty("SwitchBack", true, () -> mode.getValue() == 1);
  public final BooleanProperty onlySneaking = new BooleanProperty("OnlySneaking", false);

  private int savedSlot = -1;
  private int activeToolSlot = -1;

  private float animationProgress = 0f;
  private long lastFrame = System.currentTimeMillis();
  private ItemStack lastSpoofedStack = null;

  public AutoTool() {
    super("AutoTool", false);
  }

  public boolean isKillAura() {
    KillAura killAura = (KillAura) Myau.moduleManager.modules.get(KillAura.class);
    if (killAura == null || !killAura.isEnabled()) return false;
    return TeamUtil.isEntityLoaded(killAura.getTarget()) && killAura.isAttackAllowed();
  }

  @EventTarget(Priority.HIGH)
  public void onTick(TickEvent event) {
    if (!this.isEnabled() || event.getType() != EventType.PRE) return;
    if (mc.thePlayer == null || mc.theWorld == null) {
      this.resetState();
      return;
    }

    if (onlySneaking.getValue() && !mc.thePlayer.isSneaking()) {
      this.resetState();
      return;
    }

    if (!this.canAutoTool()) {
      this.resetState();
      return;
    }

    BlockPos pos = mc.objectMouseOver.getBlockPos();
    Block block = mc.theWorld.getBlockState(pos).getBlock();
    int bestSlot = this.findBestHotbarTool(block);

    if (bestSlot == -1) {
      return;
    }

    this.switchToSlot(bestSlot);
  }

  private boolean canAutoTool() {
    if (mc.currentScreen != null || mc.thePlayer.isDead || !mc.thePlayer.capabilities.allowEdit)
      return false;
    if (mc.objectMouseOver == null || mc.objectMouseOver.typeOfHit != MovingObjectType.BLOCK)
      return false;
    if (this.isKillAura() || mc.thePlayer.isUsingItem()) return false;
    if (!mc.gameSettings.keyBindAttack.isKeyDown()) return false;

    Block block = mc.theWorld.getBlockState(mc.objectMouseOver.getBlockPos()).getBlock();
    return block.getBlockHardness(mc.theWorld, mc.objectMouseOver.getBlockPos()) != 0.0F;
  }

  private int findBestHotbarTool(Block block) {
    int currentSlot = mc.thePlayer.inventory.currentItem;
    int bestSlot = ItemUtil.findInventorySlot(currentSlot, block);
    return bestSlot == currentSlot ? -1 : bestSlot;
  }

  private void switchToSlot(int slot) {
    if (mc.thePlayer == null) return;

    if (this.savedSlot == -1) {
      this.savedSlot = mc.thePlayer.inventory.currentItem;
    }

    if (this.mode.getValue() == 0) {
      Myau.slotComponent.setSlot(slot);
      if (this.activeToolSlot != slot) {
        mc.thePlayer.sendQueue.addToSendQueue(new C09PacketHeldItemChange(slot));
      }
    } else {
      if (this.activeToolSlot != slot) {
        mc.thePlayer.inventory.currentItem = slot;
        mc.thePlayer.sendQueue.addToSendQueue(new C09PacketHeldItemChange(slot));
      }
    }

    this.activeToolSlot = slot;
  }

  private void resetState() {
    if (this.savedSlot != -1 && mc.thePlayer != null) {
      if (this.mode.getValue() == 1) {
        if (this.switchBack.getValue()) {
          mc.thePlayer.inventory.currentItem = this.savedSlot;
          mc.thePlayer.sendQueue.addToSendQueue(new C09PacketHeldItemChange(this.savedSlot));
        }
      } else {
        mc.thePlayer.sendQueue.addToSendQueue(
            new C09PacketHeldItemChange(mc.thePlayer.inventory.currentItem));
      }
    }
    this.savedSlot = -1;
    this.activeToolSlot = -1;
  }

  @EventTarget
  public void onRender(Render2DEvent event) {
    if (mc.thePlayer == null) return;

    long currentFrame = System.currentTimeMillis();
    float delta = (currentFrame - lastFrame) / 1000f;
    lastFrame = currentFrame;

    boolean shouldShow = this.isEnabled() && this.activeToolSlot != -1;

    float target = shouldShow ? 1f : 0f;
    animationProgress += (target - animationProgress) * 12f * delta;
    animationProgress = Math.max(0f, Math.min(1f, animationProgress));

    if (animationProgress <= 0.01f) return;

    ItemStack itemStack = null;
    if (this.activeToolSlot != -1) {
      itemStack = mc.thePlayer.inventory.getStackInSlot(this.activeToolSlot);
      if (itemStack != null) {
        lastSpoofedStack = itemStack;
      }
    } else {
      itemStack = lastSpoofedStack;
    }

    if (itemStack == null) return;

    ScaledResolution sr = new ScaledResolution(mc);
    String toolName = itemStack.getDisplayName();

    float textWidth = myau.util.font.Fonts.MAIN.get(18).width(toolName);
    float width = 16f + 8f + textWidth + 8f;
    float height = 22f;
    float x = (sr.getScaledWidth() - width) / 2f;
    float y = sr.getScaledHeight() - 90f;

    GlStateManager.pushMatrix();

    float centerX = x + width / 2f;
    float centerY = y + height / 2f;
    GlStateManager.translate(centerX, centerY, 0);
    GlStateManager.scale(animationProgress, animationProgress, 1f);
    GlStateManager.translate(-centerX, -centerY, 0);

    HUD hud = (HUD) Myau.moduleManager.getModule(HUD.class);
    boolean shaders = hud != null && hud.shaders.getValue();

    if (shaders) {
      float blurP = hud.blurCompression.getValue();
      float blurR = hud.blurRadius.getValue();
      float bloomP = hud.bloomCompression.getValue();
      float bloomR = hud.bloomRadius.getValue();

      myau.util.shader.RenderSystem.renderBlur(
          blurR,
          blurP,
          () -> {
            RoundedUtils.drawRound(x, y, width, height, 4f, new Color(0, 0, 0, 150));
          });

      myau.util.shader.RenderSystem.renderBloom(
          bloomR,
          bloomP,
          () -> {
            RoundedUtils.drawRound(
                x - 1, y - 1, width + 2, height + 2, 4f, new Color(81, 99, 149, 80));
          });
    }

    int bgAlpha = (int) (150 * animationProgress);
    RoundedUtils.drawRound(x, y, width, height, 4f, new Color(0, 0, 0, bgAlpha));

    GlStateManager.pushMatrix();
    RenderHelper.enableGUIStandardItemLighting();
    mc.getRenderItem().renderItemAndEffectIntoGUI(itemStack, (int) x + 4, (int) y + 3);
    RenderHelper.disableStandardItemLighting();
    GlStateManager.popMatrix();

    GlStateManager.enableBlend();
    int textAlpha = (int) (255 * animationProgress);
    float fontY = y + (height / 2f) - (myau.util.font.Fonts.MAIN.get(18).height() / 2f);
    float textX = x + 24f;

    myau.util.font.Fonts.MAIN
        .get(18)
        .drawWithShadow(toolName, textX, fontY, new Color(255, 255, 255, textAlpha).getRGB());

    GlStateManager.popMatrix();
  }

  @Override
  public void onDisabled() {
    this.resetState();
    this.lastSpoofedStack = null;
    this.animationProgress = 0f;
  }
}
