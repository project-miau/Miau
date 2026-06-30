package miau.module.modules.player;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import miau.event.EventTarget;
import miau.event.impl.KeyEvent;
import miau.event.impl.UpdateEvent;
import miau.event.types.EventType;
import miau.module.Module;
import miau.property.properties.BooleanProperty;
import miau.property.properties.IntProperty;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.inventory.GuiChest;
import net.minecraft.init.Items;
import net.minecraft.inventory.ContainerChest;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.scoreboard.Score;
import net.minecraft.scoreboard.ScoreObjective;
import net.minecraft.scoreboard.Scoreboard;
import net.minecraft.util.StringUtils;

public class AutoChest extends Module {
  private static final Minecraft mc = Minecraft.getMinecraft();

  public final BooleanProperty automatic = new BooleanProperty("Automatic", false);
  public final BooleanProperty stopWhenFinished = new BooleanProperty("Stop When Finished", true);
  public final IntProperty delay = new IntProperty("Delay", 50, 0, 1000);
  public final BooleanProperty iron = new BooleanProperty("Iron", true);
  public final BooleanProperty gold = new BooleanProperty("Gold", true);
  public final BooleanProperty diamonds = new BooleanProperty("Diamonds", true);
  public final BooleanProperty emeralds = new BooleanProperty("Emeralds", true);

  // Extra keybinds stored as key codes (0 = unset).
  // Set via command or config: LWJGL Keyboard key codes.
  public final IntProperty invToChestKeyCode = new IntProperty("Inv-To-Chest Key", 0, 0, 255);
  public final IntProperty chestToInvKeyCode = new IntProperty("Chest-To-Inv Key", 0, 0, 255);

  private int delayTicks;
  private int inChestDelay;
  private boolean foundAllItemsInInventory;
  private boolean foundAllItemsInChest;
  private boolean foundItemsInChest;
  private boolean foundItemsInInventory;

  // Rising-edge detection for manual key presses.
  private boolean invToChestPending;
  private boolean chestToInvPending;

  public AutoChest() {
    super("AutoChest", false);
  }

  @Override
  public void onEnabled() {
    delayTicks = 0;
    inChestDelay = 0;
    foundAllItemsInInventory = false;
    foundAllItemsInChest = false;
    foundItemsInChest = false;
    foundItemsInInventory = false;
    invToChestPending = false;
    chestToInvPending = false;
  }

  @EventTarget
  public void onKey(KeyEvent event) {
    if (!isEnabled()) return;
    int key = event.getKey();
    if (invToChestKeyCode.getValue() != 0 && key == invToChestKeyCode.getValue()) {
      invToChestPending = true;
    }
    if (chestToInvKeyCode.getValue() != 0 && key == chestToInvKeyCode.getValue()) {
      chestToInvPending = true;
    }
  }

  @EventTarget
  public void onUpdate(UpdateEvent event) {
    if (event.getType() != EventType.PRE) return;

    if (delayTicks > 0) delayTicks--;

    // ── Reset state when not in a valid chest ──────────────────────
    if (!(mc.currentScreen instanceof GuiChest)) {
      inChestDelay = 2;
      resetFlags();
      return;
    }

    ContainerChest container = (ContainerChest) ((GuiChest) mc.currentScreen).inventorySlots;
    IInventory chestInv = container.getLowerChestInventory();
    String chestName = StringUtils.stripControlCodes(chestInv.getName());

    if (!isValidChestName(chestName) || getBedwarsStatus() != 3) {
      inChestDelay = 2;
      resetFlags();
      return;
    }

    // Initial delay after opening the chest.
    if (inChestDelay > 0) {
      inChestDelay--;
      if (inChestDelay > 0) return;
    }

    boolean auto = automatic.getValue();
    boolean stopWhenDone = stopWhenFinished.getValue();
    int delayMs = delay.getValue();
    int neededDelay = delayMs / 50; // Convert ms → game ticks

    boolean invToChestPressed = invToChestPending;
    boolean chestToInvPressed = chestToInvPending;
    invToChestPending = false;
    chestToInvPending = false;

    int chestSize = chestInv.getSizeInventory();

    // ── Direction 1: Inventory → Chest ─────────────────────────────
    if ((auto && !foundItemsInChest && !foundAllItemsInInventory)
        || (invToChestPressed && delayTicks == 0)) {
      boolean clicked = false;

      for (int i = chestSize; i < chestSize + 36; i++) {
        ItemStack stack = container.getSlot(i).getStack();
        if (stack == null || !isTargetItem(stack)) continue;

        mc.playerController.windowClick(container.windowId, i, 0, 1, mc.thePlayer);
        clicked = true;
        delayTicks = neededDelay;
        foundItemsInInventory = true;
        if (neededDelay > 0) break;
      }

      if (stopWhenDone && !clicked && foundItemsInInventory) {
        foundAllItemsInInventory = true;
      }
    }

    // ── Direction 2: Chest → Inventory ─────────────────────────────
    if ((auto && !foundItemsInInventory && !foundAllItemsInChest)
        || (chestToInvPressed && delayTicks == 0)) {
      boolean clicked = false;

      for (int i = 0; i < chestSize; i++) {
        ItemStack stack = container.getSlot(i).getStack();
        if (stack == null || !isTargetItem(stack)) continue;

        mc.playerController.windowClick(container.windowId, i, 0, 1, mc.thePlayer);
        clicked = true;
        delayTicks = neededDelay;
        foundItemsInChest = true;
        if (neededDelay > 0) break;
      }

      if (stopWhenDone && !clicked && foundItemsInChest) {
        foundAllItemsInChest = true;
      }
    }
  }

  // ── Helpers ──────────────────────────────────────────────────────────

  private void resetFlags() {
    foundItemsInInventory = false;
    foundItemsInChest = false;
    foundAllItemsInChest = false;
    foundAllItemsInInventory = false;
    invToChestPending = false;
    chestToInvPending = false;
  }

  /** Matches single / double chest and ender chest by their English suffix. */
  private boolean isValidChestName(String name) {
    return name.endsWith("Chest");
  }

  private boolean isTargetItem(ItemStack stack) {
    if (stack == null) return false;
    Item item = stack.getItem();
    return (iron.getValue() && item == Items.iron_ingot)
        || (gold.getValue() && item == Items.gold_ingot)
        || (diamonds.getValue() && item == Items.diamond)
        || (emeralds.getValue() && item == Items.emerald);
  }

  /**
   * BedWars game-state detector ported from the original Raven script.
   *
   * @return -1 = not BedWars / error, 0 = in The End (death / after game), 1 = lobby, 2 = waiting /
   *     starting, 3 = game active.
   */
  private int getBedwarsStatus() {
    List<String> sidebar = getSidebarLines();
    if (sidebar == null) {
      if (mc.thePlayer != null && mc.thePlayer.dimension == 1) {
        return 0;
      }
      return -1;
    }

    int size = sidebar.size();
    if (size < 7) return -1;

    // Title must start with "BED WARS".
    String title = sidebar.get(0);
    if (title == null || !title.startsWith("BED WARS")) {
      return -1;
    }

    // Line 1 contains the lobby ID.
    String line1 = sidebar.get(1);
    if (line1 == null) return -1;
    String[] parts = line1.split("  ");
    if (parts.length < 2) return -1;
    String lobbyId = parts[1].trim();

    if (lobbyId.endsWith("]")) {
      String[] lobbyParts = lobbyId.split(" ");
      if (lobbyParts.length > 0) {
        lobbyId = lobbyParts[0];
      }
    }

    if (lobbyId.startsWith("L")) {
      return 1; // Lobby
    }

    // Lines 5 & 6 – team colours indicate an active game.
    String line5 = sidebar.get(5);
    String line6 = sidebar.get(6);

    if (line5 != null
        && line5.startsWith("R Red:")
        && line6 != null
        && line6.startsWith("B Blue:")) {
      return 3; // In-game
    }

    if (line6 != null) {
      if ("Waiting...".equals(line6) || line6.startsWith("Starting in")) {
        return 2; // Pre-game
      }
    }

    return -1;
  }

  /** Reads the sidebar (display slot 1) as a list of plain-text lines. */
  private List<String> getSidebarLines() {
    if (mc.theWorld == null) return null;

    Scoreboard scoreboard = mc.theWorld.getScoreboard();
    if (scoreboard == null) return null;

    ScoreObjective objective = scoreboard.getObjectiveInDisplaySlot(1);
    if (objective == null) return null;

    List<String> lines = new ArrayList<>();
    lines.add(StringUtils.stripControlCodes(objective.getDisplayName()));

    Collection<Score> scores = scoreboard.getSortedScores(objective);
    List<Score> scoreList = new ArrayList<>();
    for (Score score : scores) {
      if (score.getObjective() != null && score.getObjective() == objective) {
        scoreList.add(score);
      }
    }

    scoreList.sort((a, b) -> Integer.compare(b.getScorePoints(), a.getScorePoints()));

    for (Score score : scoreList) {
      if (score.getPlayerName() != null) {
        String stripped = StringUtils.stripControlCodes(score.getPlayerName()).trim();
        if (!stripped.isEmpty()) {
          lines.add(stripped);
        }
      }
    }

    return lines;
  }
}
