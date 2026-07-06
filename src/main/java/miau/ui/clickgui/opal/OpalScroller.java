package miau.ui.clickgui.opal;

public final class OpalScroller {

  private final OpalAnimation animation =
      new OpalAnimation(miau.util.animation.Easing.EASE_OUT_EXPO, 250);
  private float value;

  public OpalAnimation getAnimation() {
    return this.animation;
  }

  public void onScroll(float maxOffset) {
    this.value = Math.min(0, Math.max(-maxOffset, this.value));
    this.animation.run(this.value);
  }

  public void addScroll(double verticalScroll, float maxOffset) {
    this.value += (float) (verticalScroll * 50);
    this.value = Math.max(-maxOffset, this.value);
    this.value = Math.min(0, this.value);
    this.animation.run(this.value);
  }
}
