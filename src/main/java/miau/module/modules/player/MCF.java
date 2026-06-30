package miau.module.modules.player;

import miau.Miau;
import miau.event.EventTarget;
import miau.event.impl.KeyEvent;
import miau.module.Module;
import miau.util.client.ChatUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.MovingObjectPosition.MovingObjectType;

public class MCF extends Module {
  private static final Minecraft mc = Minecraft.getMinecraft();

  public MCF() {
    super("MCF", false, true);
  }

  @EventTarget
  public void onKey(KeyEvent event) {
    if (this.isEnabled() && event.getKey() == -98) {
      if (mc.objectMouseOver != null
          && mc.objectMouseOver.typeOfHit == MovingObjectType.ENTITY
          && mc.objectMouseOver.entityHit instanceof EntityPlayer) {
        String hitName = mc.objectMouseOver.entityHit.getName();
        if (!Miau.friendManager.isFriend(hitName)) {
          Miau.friendManager.add(hitName);
          ChatUtil.display("%sAdded &o%s&r to your friend list&r", hitName);
        } else {
          Miau.friendManager.remove(hitName);
          ChatUtil.display("%sRemoved &o%s&r from your friend list&r", hitName);
        }
      }
    }
  }
}
