package myau.notification;

public enum NotificationType {
  SUCCESS("\ue86c", 0xFF2ECC71),
  ERROR("\ue5c9", 0xFFE74C3C),
  WARN("\ue002", 0xFFFDD235),
  INFO("\ue88e", 0xFF7097CF);

  private final String icon;
  private final int color;

  NotificationType(String icon, int color) {
    this.icon = icon;
    this.color = color;
  }

  public String getIcon() {
    return icon;
  }

  public int getColor() {
    return color;
  }
}
