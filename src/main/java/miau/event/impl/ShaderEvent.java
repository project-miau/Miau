package miau.event.impl;

import miau.event.Event;

public class ShaderEvent implements Event {
  public static final int BLOOM_PASS = 0;
  public static final int BLUR_PASS = 1;

  private final int pass;

  public ShaderEvent(int pass) {
    this.pass = pass;
  }

  public boolean isBloom() {
    return pass == BLOOM_PASS;
  }

  public int getPass() {
    return pass;
  }
}
