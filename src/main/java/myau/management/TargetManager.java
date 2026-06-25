package myau.management;

import java.awt.*;
import java.io.File;
import myau.Myau;
import myau.enums.ChatColors;

public class TargetManager extends PlayerFileManager {
  public TargetManager() {
    super(new File("./config/Myau/", "enemies.txt"), new Color(ChatColors.DARK_RED.toAwtColor()));
  }

  @Override
  public String add(String name) {
    if (Myau.friendManager.isFriend(name)) {
      return null;
    }
    return super.add(name);
  }
}
