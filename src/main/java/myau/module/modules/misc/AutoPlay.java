package myau.module.modules.misc;

import myau.event.EventTarget;
import myau.event.impl.PacketEvent;
import myau.event.types.EventType;
import myau.module.Module;
import myau.property.properties.ModeProperty;
import myau.util.client.ChatUtil;
import net.minecraft.network.Packet;
import net.minecraft.network.play.server.S02PacketChat;
import net.minecraft.util.IChatComponent;

public class AutoPlay extends Module {
  public final ModeProperty mode = new ModeProperty("mode", 0, new String[] {"Hypixel"});

  public AutoPlay() {
    super("AutoPlay", false);
  }

  @EventTarget
  public void onPacket(PacketEvent event) {
    if (!this.isEnabled() || event.getType() != EventType.RECEIVE) return;

    Packet<?> packet = event.getPacket();

    if (packet instanceof S02PacketChat) {
      S02PacketChat chat = (S02PacketChat) packet;

      if (mode.getValue() == 0) {
        if (chat.isChat()) return;
        if (chat.getChatComponent().getFormattedText().contains("play again?")) {
          for (IChatComponent iChatComponent : chat.getChatComponent().getSiblings()) {
            for (String value : iChatComponent.toString().split("'")) {
              if (value.startsWith("/play") && !value.contains(".")) {

                net.minecraft.client.Minecraft.getMinecraft().thePlayer.sendChatMessage(value);
                ChatUtil.display("§8[§bAutoPlay§8] §fJoined a new game.");
                break;
              }
            }
          }
        }
      }
    }
  }
}
