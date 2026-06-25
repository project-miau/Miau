package myau.management;

import java.awt.*;
import java.io.File;
import myau.enums.ChatColors;

public class FriendManager extends PlayerFileManager {
  public FriendManager() {
    super(new File("./config/Myau/", "friends.txt"), new Color(ChatColors.DARK_GREEN.toAwtColor()));
  }
}
