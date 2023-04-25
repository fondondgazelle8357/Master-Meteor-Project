//By Zgoly
package zgoly.meteorist;

import meteordevelopment.meteorclient.addons.MeteorAddon;
import meteordevelopment.meteorclient.systems.Systems;
import meteordevelopment.meteorclient.systems.commands.Commands;
import meteordevelopment.meteorclient.systems.hud.Hud;
import meteordevelopment.meteorclient.systems.hud.HudGroup;
import meteordevelopment.meteorclient.systems.modules.Category;
import meteordevelopment.meteorclient.systems.modules.Modules;
import net.minecraft.item.Items;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import zgoly.meteorist.commands.Coordinates;
import zgoly.meteorist.commands.TargetNbt;
import zgoly.meteorist.hud.Presets;
import zgoly.meteorist.modules.*;

public class Meteorist extends MeteorAddon {
    public static final Logger LOG = LoggerFactory.getLogger("Meteorist");
    public static final Category CATEGORY = new Category("Meteorist", Items.FIRE_CHARGE.getDefaultStack());
    public static final HudGroup HUD_GROUP = new HudGroup("Meteorist");

    @Override
    public void onInitialize() {
        LOG.info("Meteorist joined the game");
        // Modules
        Modules.get().add(new AutoFeed());
        Modules.get().add(new AutoFix());
        Modules.get().add(new AutoFloor());
        Modules.get().add(new AutoHeal());
        Modules.get().add(new AutoLeave());
        Modules.get().add(new AutoLogin());
        Modules.get().add(new ContainerCleaner());
        Modules.get().add(new DmSpam());
        Modules.get().add(new EntityUse());
        Modules.get().add(new FastBridge());
        Modules.get().add(new AutoLight());
        Modules.get().add(new ItemSucker());
        Modules.get().add(new JumpFlight());
        Modules.get().add(new JumpJump());
        Modules.get().add(new NewVelocity());
        Modules.get().add(new Placer());
        Modules.get().add(new SlotClick());
        Modules.get().add(new ZKillaura());

        // Commands
        Commands.get().add(new Coordinates());
        Commands.get().add(new TargetNbt());

        // Hud Presets
        Presets.starscriptAdd();
        Hud hud = Systems.get(Hud.class);
        hud.register(Presets.INFO);
    }

    @Override
    public void onRegisterCategories() {
        Modules.registerCategory(CATEGORY);
    }

    @Override
    public String getPackage() {
        return "zgoly.meteorist";
    }
}