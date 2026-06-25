package myau.util.client;

import myau.util.animation.*;
import myau.util.math.*;
import myau.util.misc.*;
import myau.util.network.*;
import myau.util.player.*;
import myau.util.render.*;
import myau.util.time.*;
import myau.util.world.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.audio.PositionedSoundRecord;
import net.minecraft.client.audio.SoundHandler;
import net.minecraft.util.ResourceLocation;

public class SoundUtil {
  private static final Minecraft mc = Minecraft.getMinecraft();

  public static void playSound(String soundName) {
    SoundHandler soundHandler = mc.getSoundHandler();
    if (soundHandler != null) {
      PositionedSoundRecord positionedSoundRecord =
          PositionedSoundRecord.create(new ResourceLocation(soundName));
      soundHandler.playSound(positionedSoundRecord);
    }
  }
}
