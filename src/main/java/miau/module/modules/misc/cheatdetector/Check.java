package miau.module.modules.misc.cheatdetector;

import miau.Miau;
import miau.event.impl.PacketEvent;
import miau.module.modules.misc.CheatDetector;
import miau.notification.NotificationType;
import miau.util.time.TimerUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayer;

public abstract class Check {
  protected static final Minecraft mc = Minecraft.getMinecraft();
  public TimerUtil flagTimer = new TimerUtil();

  public abstract String getName();

  public void onUpdate(EntityPlayer player) {}

  public void onPacket(PacketEvent event, EntityPlayer player) {}

  public void flag(EntityPlayer player, String verbose) {
    if (flagTimer.hasTimeElapsed(
        (long)
            ((CheatDetector) Miau.moduleManager.getModule(CheatDetector.class))
                .alertCoolDown
                .getValue()
                .floatValue())) {
      Miau.notificationManager.pop(
          "CheatDetector",
          player.getName()
              + net.minecraft.util.EnumChatFormatting.WHITE
              + " has failed "
              + net.minecraft.util.EnumChatFormatting.GRAY
              + getName()
              + net.minecraft.util.EnumChatFormatting.WHITE
              + " "
              + verbose,
          NotificationType.INFO);
      ((CheatDetector) Miau.moduleManager.getModule(CheatDetector.class)).mark(player);
      flagTimer.reset();
    }
  }
}
