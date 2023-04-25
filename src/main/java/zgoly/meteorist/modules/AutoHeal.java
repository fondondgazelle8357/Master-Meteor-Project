//By Zgoly
package zgoly.meteorist.modules;

import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.settings.StringSetting;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import zgoly.meteorist.Meteorist;

public class AutoHeal extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<String> healCommand = sgGeneral.add(new StringSetting.Builder()
            .name("heal-command")
            .description("Command to refill health bar.")
            .defaultValue("/heal")
            .build()
    );

    private final Setting<Integer> healthLevel = sgGeneral.add(new IntSetting.Builder()
            .name("health-level")
            .description("Health level at which to send the command.")
            .defaultValue(10)
            .range(1, 1024)
            .sliderRange(1, 20)
            .build()
    );

    private final Setting<Integer> delay = sgGeneral.add(new IntSetting.Builder()
            .name("delay")
            .description("Delay after sending a command in ticks (20 ticks = 1 sec).")
            .defaultValue(20)
            .range(1, 1200)
            .sliderRange(1, 40)
            .build()
    );

    private int timer;

    public AutoHeal() {
        super(Meteorist.CATEGORY, "auto-heal", "Writes command in chat when health level is low.");
    }

    @Override
    public void onActivate() {
        timer = 0;
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (timer >= delay.get() && mc.player.getHealth() <= healthLevel.get()) {
            mc.getNetworkHandler().sendChatCommand(healCommand.get().replace("/", ""));
            timer = 0;
        } else timer ++;
    }
}