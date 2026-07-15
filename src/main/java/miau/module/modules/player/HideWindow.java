package miau.module.modules.player;

import miau.event.EventTarget;
import miau.event.impl.GuiOpenEvent;
import miau.event.impl.LoadWorldEvent;
import miau.event.impl.PacketEvent;
import miau.event.impl.Render2DEvent;
import miau.event.types.EventType;
import miau.event.types.Priority;
import miau.module.Module;
import miau.property.properties.BooleanProperty;
import miau.property.properties.ColorProperty;
import miau.property.properties.DragProperty;
import miau.property.properties.FloatProperty;
import miau.property.properties.TextProperty;
import miau.util.render.RenderUtil;
import miau.util.vector.Vector2d;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.client.gui.inventory.GuiInventory;
import net.minecraft.inventory.ContainerChest;
import net.minecraft.network.play.server.S2EPacketCloseWindow;
import net.minecraft.util.ResourceLocation;

public class HideWindow extends Module {
    private static final String ICON_PATH = "/assets/keystrokesmod/textures/gui/tv_off.png";
    private static final int ICON_BASE_SIZE = 16;
    private static final float DEFAULT_RELATIVE_X = 0.5f;
    private static final float DEFAULT_RELATIVE_Y = 0.05f;
    private static final int EDIT_OUTLINE_COLOR = 0xFFFFFFFF;

    private static final Minecraft mc = Minecraft.getMinecraft();

    public final DragProperty drag = new DragProperty("Icon Position", new Vector2d(0, 0));
    public final ColorProperty iconColor = new ColorProperty("Icon color", 0xFFFFFF);
    public final FloatProperty iconScale = new FloatProperty("Icon scale", 1.0f, 0.5f, 3.0f);
    public final BooleanProperty onlyWhileCrouching = new BooleanProperty("Only while crouching", false);
    public final BooleanProperty whitelist = new BooleanProperty("Whitelist", false);
    public final TextProperty whitelistEntries = new TextProperty("Whitelist names", "e.g. Upgrades & Traps");

    private GuiContainer hiddenGui;

    public HideWindow() {
        super("Hide Window", false);
        whitelistEntries.setVisibleChecker(() -> whitelist.getValue());
    }

    @Override
    public String[] getSuffix() {
        return hiddenGui != null ? new String[]{"Hidden"} : new String[0];
    }

    @Override
    public void onDisabled() {
        if (hiddenGui != null && mc.thePlayer != null) {
            mc.thePlayer.closeScreen();
        }
        hiddenGui = null;
    }

    @EventTarget(Priority.HIGHEST)
    public void onGuiOpen(GuiOpenEvent event) {
        if (event.getGui() == null || mc.thePlayer == null) {
            return;
        }

        if (event.getGui() instanceof GuiContainer && !(event.getGui() instanceof GuiInventory)) {
            if (mc.currentScreen instanceof GuiContainer) {
                hiddenGui = null;
                return;
            }

            GuiContainer gui = (GuiContainer) event.getGui();
            if (onlyWhileCrouching.getValue() && !mc.thePlayer.isSneaking()) {
                return;
            }
            if (whitelist.getValue() && !matchesWhitelist(gui)) {
                return;
            }

            hiddenGui = gui;
            mc.thePlayer.openContainer = hiddenGui.inventorySlots;
            event.setCancelled(true);
            return;
        }

        if (event.getGui() instanceof GuiInventory && hiddenGui != null) {
            event.setGui(hiddenGui);
            hiddenGui = null;
        }
    }

    @EventTarget
    public void onReceivePacket(PacketEvent event) {
        if (event.getType() == EventType.RECEIVE && event.getPacket() instanceof S2EPacketCloseWindow) {
            hiddenGui = null;
        }
    }

    @EventTarget
    public void onWorldLoad(LoadWorldEvent event) {
        hiddenGui = null;
    }

    @EventTarget
    public void onRenderTick(Render2DEvent event) {
        if (hiddenGui == null || mc.currentScreen != null || mc.gameSettings.showDebugInfo) {
            return;
        }
        renderIcon(false);
    }

    private void renderIcon(boolean editing) {
        syncPosition();
        ResourceLocation icon = RenderUtil.getIcon(ICON_PATH);
        if (icon == null) {
            return;
        }

        float scale = iconScale.getValue();
        int size = Math.round(ICON_BASE_SIZE * scale);

        // Update drag hitbox for DragManager
        drag.scale.x = size;
        drag.scale.y = size;

        float drawX = (float) drag.position.x - size / 2.0f;
        float drawY = (float) drag.position.y - size / 2.0f;

        RenderUtil.drawIcon(icon, drawX, drawY, size, 0xFF000000 | (iconColor.getValue() & 0xFFFFFF));

        if (editing) {
            float outLeft = drawX - 2;
            float outTop = drawY - 2;
            float outRight = drawX + size + 2;
            float outBottom = drawY + size + 2;
            net.minecraft.client.gui.Gui.drawRect((int) outLeft, (int) outTop, (int) outRight, (int) (outTop + 1), EDIT_OUTLINE_COLOR);
            net.minecraft.client.gui.Gui.drawRect((int) outLeft, (int) (outBottom - 1), (int) outRight, (int) outBottom, EDIT_OUTLINE_COLOR);
            net.minecraft.client.gui.Gui.drawRect((int) outLeft, (int) outTop, (int) (outLeft + 1), (int) outBottom, EDIT_OUTLINE_COLOR);
            net.minecraft.client.gui.Gui.drawRect((int) (outRight - 1), (int) outTop, (int) outRight, (int) outBottom, EDIT_OUTLINE_COLOR);
        }
    }

    private void syncPosition() {
        ScaledResolution sr = new ScaledResolution(mc);
        int w = Math.max(1, sr.getScaledWidth());
        int h = Math.max(1, sr.getScaledHeight());
        if (drag.position.x == 0 && drag.position.y == 0 && w > 0 && h > 0) {
            drag.position.x = w * DEFAULT_RELATIVE_X;
            drag.position.y = h * DEFAULT_RELATIVE_Y;
            drag.targetPosition.x = drag.position.x;
            drag.targetPosition.y = drag.position.y;
        }
    }

    private boolean matchesWhitelist(GuiContainer gui) {
        String entries = whitelistEntries.getValue();
        if (entries == null || entries.trim().isEmpty()) {
            return false;
        }
        String title = getContainerTitle(gui);
        if (title.isEmpty()) {
            return false;
        }
        String lower = title.toLowerCase();
        String[] split = entries.split(",");
        for (String name : split) {
            String trimmed = name.trim();
            if (trimmed.isEmpty()) continue;
            if (lower.contains(trimmed.toLowerCase())) {
                return true;
            }
        }
        return false;
    }

    private static String getContainerTitle(GuiContainer gui) {
        if (gui.inventorySlots instanceof ContainerChest) {
            return ((ContainerChest) gui.inventorySlots)
                    .getLowerChestInventory()
                    .getDisplayName()
                    .getUnformattedText();
        }
        return "";
    }
}
