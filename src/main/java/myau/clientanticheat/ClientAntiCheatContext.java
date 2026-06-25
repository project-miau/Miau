package myau.clientanticheat;

import net.minecraft.entity.player.EntityPlayer;

public interface ClientAntiCheatContext {
  void receiveSignal(String playerName, String cheatName);

  PlayerCheckData getPlayerData(EntityPlayer player);
}
