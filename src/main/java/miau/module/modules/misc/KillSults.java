package miau.module.modules.misc;

import miau.event.EventTarget;
import miau.event.impl.AttackEvent;
import miau.event.impl.UpdateEvent;
import miau.event.types.EventType;
import miau.module.Module;
import miau.property.properties.BooleanProperty;
import miau.property.properties.ModeProperty;
import miau.module.modules.misc.killsults.*;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayer;

public class KillSults extends Module {
    private static final Minecraft mc = Minecraft.getMinecraft();

    public final ModeProperty mode = new ModeProperty("Mode", 0, new String[]{"English", "Vietnamese", "30-04-1975"});
    public final BooleanProperty shout = new BooleanProperty("Shout", false);

    private EntityPlayer target;
    private long lastAttackTime;

    private final KillSultMode englishMode = new EnglishMode();
    private final KillSultMode vietnameseMode = new VietnameseMode();
    private final KillSultMode liberationDayMode = new LiberationDayMode();

    public KillSults() {
        super("KillSults", false, false);
    }

    @EventTarget
    public void onAttack(AttackEvent event) {
        if (event.getTarget() instanceof EntityPlayer) {
            target = (EntityPlayer) event.getTarget();
            lastAttackTime = System.currentTimeMillis();
        }
    }

    @EventTarget
    public void onUpdate(UpdateEvent event) {
        if (event.getType() == EventType.PRE) {
            if (target != null) {
                if (target.isDead || target.getHealth() <= 0) {
                    sendKillSult(target.getName());
                    target = null;
                } else if (System.currentTimeMillis() - lastAttackTime > 10000) {
                    // Timeout after 10 seconds of no attacks to prevent sending message later
                    target = null;
                }
            }
        }
    }

    private void sendKillSult(String targetName) {
        if (mc.thePlayer == null) return;
        
        KillSultMode activeMode = englishMode;
        switch (mode.getModeString()) {
            case "English":
                activeMode = englishMode;
                break;
            case "Vietnamese":
                activeMode = vietnameseMode;
                break;
            case "30-04-1975":
                activeMode = liberationDayMode;
                break;
        }

        String message = activeMode.getMessage(targetName);
        if (shout.getValue()) {
            message = "/shout " + message;
        }

        mc.thePlayer.sendChatMessage(message);
    }
}
