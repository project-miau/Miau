package miau.module.modules.player;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.*;
import miau.event.EventTarget;
import miau.event.impl.PacketEvent;
import miau.event.impl.UpdateEvent;
import miau.event.types.EventType;
import miau.module.Module;
import miau.property.properties.BooleanProperty;
import miau.property.properties.IntProperty;
import miau.property.properties.ModeProperty;
import net.minecraft.block.Block;
import net.minecraft.block.BlockBed;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraft.network.play.client.C03PacketPlayer;
import net.minecraft.util.BlockPos;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.MathHelper;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.Vec3;

@SuppressWarnings("unused")
public class BedDefender extends Module {
  private static final Minecraft mc = Minecraft.getMinecraft();

  private static final EnumFacing[] CLICK_FACES = {
    EnumFacing.DOWN, EnumFacing.UP,
    EnumFacing.SOUTH, EnumFacing.NORTH,
    EnumFacing.WEST, EnumFacing.EAST
  };

  private static String[] defenseNames;
  private static Object[][][] allDefenses;

  static {
    loadDefenses();
  }

  private static void loadDefenses() {
    try (Reader reader =
        new InputStreamReader(
            BedDefender.class.getResourceAsStream("/bed_defenses.json"), StandardCharsets.UTF_8)) {
      JsonObject root = new JsonParser().parse(reader).getAsJsonObject();
      Set<String> keys =
          root.entrySet().size() > 0 ? new LinkedHashSet<>() : Collections.emptySet();
      List<String> names = new ArrayList<>();
      List<Object[][]> defs = new ArrayList<>();
      for (Map.Entry<String, JsonElement> entry : root.entrySet()) {
        names.add(entry.getKey());
        JsonArray arr = entry.getValue().getAsJsonArray();
        Object[][] steps = new Object[arr.size()][];
        for (int i = 0; i < arr.size(); i++) {
          JsonObject step = arr.get(i).getAsJsonObject();
          steps[i] =
              new Object[] {
                step.get("block").getAsString(),
                step.get("x").getAsInt(),
                step.get("y").getAsInt(),
                step.get("z").getAsInt()
              };
        }
        defs.add(steps);
      }
      defenseNames = names.toArray(new String[0]);
      allDefenses = defs.toArray(new Object[0][][]);
    } catch (Exception e) {
      defenseNames = new String[] {"Fallback"};
      allDefenses =
          new Object[][][] {
            {
              {"wool", 0, 0, -1}, {"wool", 0, 0, 1},
              {"wool", -1, 0, 0}, {"wool", 1, 0, 0},
              {"wool", 0, 1, 0}
            }
          };
    }
  }

  public final ModeProperty defense = new ModeProperty("defense", 0, defenseNames);
  public final BooleanProperty onlyTopBeds = new BooleanProperty("only-top-beds", true);
  public final IntProperty delayAfterSwap = new IntProperty("delay-after-swap", 0, 0, 10);
  public final IntProperty delayAfterAim = new IntProperty("delay-after-aim", 0, 0, 10);
  public final IntProperty sneakHoldTicks = new IntProperty("sneak-hold-ticks", 5, 0, 20);
  public final IntProperty fov = new IntProperty("fov", 180, 0, 180);

  private int defenseStepIndex;
  private boolean started;
  private boolean sneaked;
  private boolean pendingPlace;
  private String lockedDirection = "";
  private EnumFacing pendingFace;
  private BlockPos origin;
  private BlockPos hitPos;
  private BlockPos renderTarget;
  private Vec3 placePos;
  private float serverYaw;
  private float serverPitch;
  private int swapTicks;
  private int aimTicks;
  private int sneakHoldTicksRemaining;
  private Object[][] defenseSteps;
  private final Map<String, Integer> inventoryCache = new HashMap<>();

  public BedDefender() {
    super("BedDefender", false);
  }

  @Override
  public void onEnabled() {
    int presetIdx = defense.getValue();
    defenseSteps = allDefenses[Math.max(0, Math.min(presetIdx, allDefenses.length - 1))];
    defenseStepIndex = 0;
    started = false;
    sneaked = false;
    pendingPlace = false;
    lockedDirection = "";
    origin = null;
    renderTarget = null;
    inventoryCache.clear();
    aimTicks = delayAfterAim.getValue();
    swapTicks = 0;
    sneakHoldTicksRemaining = 0;
    serverYaw = mc.thePlayer != null ? mc.thePlayer.rotationYaw : 0f;
    serverPitch = mc.thePlayer != null ? mc.thePlayer.rotationPitch : 0f;
  }

  @Override
  public void onDisabled() {
    if (sneaked) {
      KeyBinding.setKeyBindState(mc.gameSettings.keyBindSneak.getKeyCode(), false);
    }
    sneaked = false;
    pendingPlace = false;
  }

  @EventTarget
  public void onPacket(PacketEvent e) {
    if (!isEnabled()) return;
    if (e.getType() == EventType.SEND && e.getPacket() instanceof C03PacketPlayer) {
      C03PacketPlayer packet = (C03PacketPlayer) e.getPacket();
      if (packet instanceof C03PacketPlayer.C05PacketPlayerLook
          || packet instanceof C03PacketPlayer.C06PacketPlayerPosLook) {
        serverYaw = packet.getYaw();
        serverPitch = packet.getPitch();
      }
    }
  }

  @EventTarget
  public void onUpdate(UpdateEvent e) {
    if (!isEnabled()) return;
    if (mc.thePlayer == null || mc.theWorld == null) {
      if (e.getType() == EventType.PRE) reset();
      return;
    }

    if (e.getType() == EventType.POST) {
      if (pendingPlace) {
        pendingPlace = false;
        doPlaceBlock();
      }
      return;
    }

    if (e.getType() != EventType.PRE) return;

    if (sneaked) {
      if (sneakHoldTicksRemaining > 0) {
        sneakHoldTicksRemaining--;
      } else {
        KeyBinding.setKeyBindState(mc.gameSettings.keyBindSneak.getKeyCode(), false);
        sneaked = false;
      }
    }

    float[] rots = computeRotations(fov.getValue());
    if (rots != null) {
      e.setRotation(rots[0], rots[1], 6);
    }
  }

  private void reset() {
    started = false;
    pendingPlace = false;
    if (sneaked) {
      KeyBinding.setKeyBindState(mc.gameSettings.keyBindSneak.getKeyCode(), false);
      sneaked = false;
    }
  }

  private void doPlaceBlock() {
    if (mc.thePlayer == null || mc.theWorld == null) return;
    ItemStack held = mc.thePlayer.inventory.getCurrentItem();
    if (held == null) return;
    if (mc.playerController.onPlayerRightClick(
        mc.thePlayer, mc.theWorld, held, hitPos, pendingFace, placePos)) {
      mc.thePlayer.swingItem();
      defenseStepIndex++;
    }
  }

  private float[] computeRotations(float fov) {
    float maxYaw = fov;
    float maxPitch = Math.min(fov, 90f);

    if (!started) {
      BlockPos bed = findBed(8);
      if (bed == null) return null;
      origin = bed;
      lockedDirection = getBedDirection(bed);
      started = true;
    }

    while (defenseStepIndex < defenseSteps.length) {
      int[] off = rotateOffset(defenseSteps[defenseStepIndex], lockedDirection);
      BlockPos tgt = origin.add(off[0], off[1], off[2]);
      if (mc.theWorld.isAirBlock(tgt)) break;
      defenseStepIndex++;
    }

    if (defenseStepIndex >= defenseSteps.length) {
      this.toggle();
      return null;
    }

    int[] off = rotateOffset(defenseSteps[defenseStepIndex], lockedDirection);
    BlockPos target = origin.add(off[0], off[1], off[2]);
    renderTarget = target;

    float[] res0 = attemptPlace(serverYaw, serverPitch, target);
    if (res0 != null) {
      return res0[0] == -999f ? new float[] {serverYaw, serverPitch} : res0;
    }

    Vec3 eye = mc.thePlayer.getPositionEyes(1.0f);
    float curYawW = normYaw(serverYaw);
    float curPit = serverPitch;
    float cliYawW = normYaw(mc.thePlayer.rotationYaw);
    float cliPit = mc.thePlayer.rotationPitch;

    double INSET = 0.05, STEP = 0.2, JIT = 0.2;
    double insetTop = 1 - INSET - 1e-3;
    double insetBot = INSET + 1e-3;
    int GRID = (int) Math.round(1.0 / STEP);

    List<double[]> cands = new ArrayList<>();

    for (int fi = 0; fi < CLICK_FACES.length; fi++) {
      EnumFacing clickFace = CLICK_FACES[fi];
      BlockPos support = target.offset(clickFace.getOpposite());
      Block supportBlock = mc.theWorld.getBlockState(support).getBlock();
      if (supportBlock == Blocks.air) continue;
      if (onlyTopBeds.getValue() && supportBlock instanceof BlockBed && clickFace != EnumFacing.UP)
        continue;

      for (int rr = 0; rr <= GRID; rr++) {
        boolean ltr = (rr & 1) == 0;
        double v = rr * STEP + (Math.random() * 2 - 1) * STEP * JIT;
        v = Math.max(0, Math.min(1, v));
        for (int cc = 0; cc <= GRID; cc++) {
          double cu = cc * STEP + (Math.random() * 2 - 1) * STEP * JIT;
          cu = Math.max(0, Math.min(1, cu));
          double u = ltr ? cu : 1 - cu;

          double px, py, pz;
          if (fi < 2) {
            px = support.getX() + u;
            pz = support.getZ() + v;
            py = support.getY() + (fi == 1 ? insetTop : insetBot);
          } else if (fi < 4) {
            px = support.getX() + u;
            py = support.getY() + v;
            pz = support.getZ() + (fi == 2 ? insetTop : insetBot);
          } else {
            pz = support.getZ() + u;
            py = support.getY() + v;
            px = support.getX() + (fi == 5 ? insetTop : insetBot);
          }

          float[] rotW = rotationsWrapped(eye, px, py, pz);
          float yawW = rotW[0], pit = rotW[1];

          if (Math.abs(wrapYawDelta(cliYawW, yawW)) > maxYaw) continue;
          if (Math.abs(pit - cliPit) > maxPitch) continue;
          if (Math.abs(pit) > 90f) continue;

          double cost =
              Math.abs(wrapYawDelta(curYawW, yawW))
                  + Math.abs(pit - curPit)
                  + (clickFace == EnumFacing.UP ? -0.25 : 0);
          cands.add(new double[] {cost, yawW, pit});
        }
      }
    }

    if (cands.isEmpty()) {
      if (defenseStepIndex >= defenseSteps.length) this.toggle();
      return null;
    }

    cands.sort(Comparator.comparingDouble(a -> a[0]));

    for (double[] cand : cands) {
      float yawW = (float) cand[1];
      float pit = (float) cand[2];
      float yawU = unwrapYaw(yawW, serverYaw);
      float[] res = attemptPlace(yawU, pit, target);
      if (res != null) {
        return res[0] == -999f ? new float[] {yawU, pit} : res;
      }
    }

    if (defenseStepIndex >= defenseSteps.length) this.toggle();
    return null;
  }

  private float[] attemptPlace(float yaw, float pitch, BlockPos target) {
    if (defenseStepIndex >= defenseSteps.length) return null;

    MovingObjectPosition mop = raycastBlock(yaw, pitch, 4.5);
    if (mop == null || mop.typeOfHit != MovingObjectPosition.MovingObjectType.BLOCK) return null;

    BlockPos hitBlock = mop.getBlockPos();
    EnumFacing face = mop.sideHit;
    BlockPos placeTarget = hitBlock.offset(face);

    if (!placeTarget.equals(target)) return null;

    Block hitBlockType = mc.theWorld.getBlockState(hitBlock).getBlock();
    if (onlyTopBeds.getValue() && face != EnumFacing.UP && hitBlockType instanceof BlockBed)
      return null;

    if (!mc.theWorld.isAirBlock(placeTarget)) return null;

    String blockName = (String) defenseSteps[defenseStepIndex][0];
    int wantSlot = getMatchingHotbarSlot(blockName);
    if (wantSlot == -1) {
      this.toggle();
      return null;
    }

    if (mc.thePlayer.inventory.currentItem != wantSlot) {
      mc.thePlayer.inventory.currentItem = wantSlot;
      swapTicks = delayAfterSwap.getValue();
    }

    if (swapTicks-- > 0) return new float[] {-999f, -999f};

    if (hitBlockType instanceof BlockBed && !mc.gameSettings.keyBindSneak.isKeyDown()) {
      KeyBinding.setKeyBindState(mc.gameSettings.keyBindSneak.getKeyCode(), true);
      sneaked = true;
      sneakHoldTicksRemaining = sneakHoldTicks.getValue();
      return new float[] {-999f, -999f};
    }

    if (aimTicks-- > 0 || Math.abs(yaw - serverYaw) > 25 || Math.abs(pitch - serverPitch) > 25) {
      return new float[] {yaw, pitch};
    }

    aimTicks = delayAfterAim.getValue();
    hitPos = hitBlock;
    pendingFace = face;
    placePos = mop.hitVec;
    pendingPlace = true;

    return new float[] {yaw, pitch};
  }

  private MovingObjectPosition raycastBlock(float yaw, float pitch, double range) {
    Vec3 eye = mc.thePlayer.getPositionEyes(1.0f);
    float cosPitch = MathHelper.cos(-pitch * (float) Math.PI / 180f);
    float sinPitch = MathHelper.sin(-pitch * (float) Math.PI / 180f);
    float cosYaw = MathHelper.cos(-yaw * (float) Math.PI / 180f - (float) Math.PI);
    float sinYaw = MathHelper.sin(-yaw * (float) Math.PI / 180f - (float) Math.PI);
    double dx = sinYaw * cosPitch;
    double dy = sinPitch;
    double dz = cosYaw * cosPitch;
    Vec3 end = eye.addVector(dx * range, dy * range, dz * range);
    return mc.theWorld.rayTraceBlocks(eye, end);
  }

  private BlockPos findBed(int range) {
    BlockPos playerPos = new BlockPos(mc.thePlayer);
    int cx = playerPos.getX(), cy = playerPos.getY(), cz = playerPos.getZ();
    for (int x = cx - range; x <= cx + range; x++) {
      for (int y = cy - range; y <= cy + range; y++) {
        for (int z = cz - range; z <= cz + range; z++) {
          BlockPos pos = new BlockPos(x, y, z);
          IBlockState state = mc.theWorld.getBlockState(pos);
          if (!(state.getBlock() instanceof BlockBed)) continue;

          EnumFacing facing = state.getValue(BlockBed.FACING);
          if (state.getValue(BlockBed.PART) == BlockBed.EnumPartType.FOOT) {
            return pos;
          } else {
            return pos.offset(facing.getOpposite());
          }
        }
      }
    }
    return null;
  }

  private String getBedDirection(BlockPos footPos) {
    IBlockState state = mc.theWorld.getBlockState(footPos);
    if (!(state.getBlock() instanceof BlockBed)) return "";
    EnumFacing facing = state.getValue(BlockBed.FACING);
    switch (facing) {
      case SOUTH:
        return "north";
      case NORTH:
        return "south";
      case WEST:
        return "east";
      case EAST:
        return "west";
      default:
        return "";
    }
  }

  private int getMatchingHotbarSlot(String blockName) {
    String key = blockName.toLowerCase();
    Integer cached = inventoryCache.get(key);
    if (cached != null) {
      ItemStack item = mc.thePlayer.inventory.getStackInSlot(cached);
      if (item != null && getBlockRegistryName(item).equalsIgnoreCase(blockName)) return cached;
    }
    for (int s = 0; s < 9; s++) {
      ItemStack item = mc.thePlayer.inventory.getStackInSlot(s);
      if (item != null && getBlockRegistryName(item).equalsIgnoreCase(blockName)) {
        inventoryCache.put(key, s);
        return s;
      }
    }
    return -1;
  }

  private static String getBlockRegistryName(ItemStack stack) {
    if (stack == null || stack.getItem() == null) return "";
    Block block = Block.getBlockFromItem(stack.getItem());
    if (block == null || block == Blocks.air) return "";
    Object regName = Block.blockRegistry.getNameForObject(block);
    if (regName == null) return "";
    String name = regName.toString();
    return name.startsWith("minecraft:") ? name.substring(10) : name;
  }

  private static int[] rotateOffset(Object[] step, String dir) {
    int x = (int) step[1], y = (int) step[2], z = (int) step[3];
    switch (dir) {
      case "south":
        return new int[] {-x, y, -z};
      case "east":
        return new int[] {-z, y, x};
      case "west":
        return new int[] {z, y, -x};
      default:
        return new int[] {x, y, z};
    }
  }

  private static float normYaw(float yaw) {
    yaw = ((yaw % 360f) + 360f) % 360f;
    return yaw > 180f ? yaw - 360f : yaw;
  }

  private static float wrapYawDelta(float base, float target) {
    float d = target - base;
    while (d <= -180f) d += 360f;
    while (d > 180f) d -= 360f;
    return d;
  }

  private static float unwrapYaw(float yaw, float prevYaw) {
    return prevYaw + ((((yaw - prevYaw + 180f) % 360f) + 360f) % 360f - 180f);
  }

  private static float[] rotationsWrapped(Vec3 eye, double tx, double ty, double tz) {
    double dx = tx - eye.xCoord, dy = ty - eye.yCoord, dz = tz - eye.zCoord;
    double hd = Math.sqrt(dx * dx + dz * dz);
    float yaw = (float) Math.toDegrees(Math.atan2(dz, dx)) - 90f;
    yaw = normYaw(yaw);
    float pitch = (float) Math.toDegrees(-Math.atan2(dy, hd));
    return new float[] {yaw, pitch};
  }
}
