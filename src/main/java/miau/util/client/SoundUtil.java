package miau.util.client;

import miau.util.animation.*;
import miau.util.math.*;
import miau.util.misc.*;
import miau.util.network.*;
import miau.util.player.*;
import miau.util.render.*;
import miau.util.time.*;
import miau.util.world.*;
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
