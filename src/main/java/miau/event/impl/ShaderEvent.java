package miau.event.impl;

import miau.event.Event;

public class ShaderEvent implements Event {
  private final boolean bloom;

  public ShaderEvent(boolean bloom) {
    this.bloom = bloom;
  }

  public boolean isBloom() {
    return bloom;
  }
}
