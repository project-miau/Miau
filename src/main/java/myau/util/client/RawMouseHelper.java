package myau.util.client;

import myau.module.modules.misc.MouseRawInput;
import myau.util.animation.*;
import myau.util.math.*;
import myau.util.misc.*;
import myau.util.network.*;
import myau.util.player.*;
import myau.util.render.*;
import myau.util.time.*;
import myau.util.world.*;
import net.minecraft.util.MouseHelper;

public class RawMouseHelper extends MouseHelper {
  @Override
  public void mouseXYChange() {
    int rawDeltaX = MouseRawInput.consumeDeltaX();
    int rawDeltaY = MouseRawInput.consumeDeltaY();

    if (rawDeltaX == 0 && rawDeltaY == 0) {
      super.mouseXYChange();
      return;
    }

    this.deltaX = rawDeltaX;
    this.deltaY = -rawDeltaY;
  }
}
