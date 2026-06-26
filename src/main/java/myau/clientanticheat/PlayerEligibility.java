package myau.clientanticheat;

import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.network.NetHandlerPlayClient;
import net.minecraft.client.network.NetworkPlayerInfo;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.world.WorldSettings.GameType;

public final class PlayerEligibility {

  private PlayerEligibility() {}

  public static boolean isRealPlayer(EntityPlayer player) {
    Minecraft mc = Minecraft.getMinecraft();
    if (player == null || mc == null || mc.theWorld == null || player.isDead) {
      return false;
    }
    if (player.getHealth() <= 0.0F || player.isInvisible()) {
      return false;
    }
    NetHandlerPlayClient netHandler = mc.getNetHandler();
    if (netHandler == null) {
      return false;
    }
    UUID uuid = player.getUniqueID();
    if (uuid == null) {
      return false;
    }
    NetworkPlayerInfo info = netHandler.getPlayerInfo(uuid);
    if (info == null || info.getGameType() == GameType.SPECTATOR) {
      return false;
    }
    String name = player.getName();
    if (name == null || name.length() == 0) {
      return false;
    }
    return netHandler.getPlayerInfo(name) == info;
  }

  public static boolean shouldCheckPlayer(EntityPlayer player) {
    Minecraft mc = Minecraft.getMinecraft();
    return isRealPlayer(player) && mc != null && mc.thePlayer != null && player != mc.thePlayer;
  }

  public static boolean shouldUseAsTarget(EntityPlayer candidate, EntityPlayer attacker) {
    return candidate != attacker && isRealPlayer(candidate);
  }
}
