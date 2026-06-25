package myau.management;

import java.awt.*;
import java.io.File;
import myau.enums.ChatColors;

public class TargetManager extends PlayerFileManager {
  public TargetManager() {
    super(new File("./config/Myau/", "enemies.txt"), new Color(ChatColors.DARK_RED.toAwtColor()));
  }
}
