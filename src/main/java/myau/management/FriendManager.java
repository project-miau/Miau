package myau.management;

import java.awt.*;
import java.io.File;
import myau.Myau;
import myau.enums.ChatColors;

public class FriendManager extends PlayerFileManager {
  public FriendManager() {
    super(new File("./config/Myau/", "friends.txt"), new Color(ChatColors.DARK_GREEN.toAwtColor()));
  }

  @Override
  public String add(String name) {
    if (Myau.targetManager.isFriend(name)) {
      return null;
    }
    return super.add(name);
  }
}
