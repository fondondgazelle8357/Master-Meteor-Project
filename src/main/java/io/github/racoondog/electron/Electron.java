package io.github.racoondog.electron;

import io.github.racoondog.meteorsharedaddonutils.features.TitleScreenCredits;
import meteordevelopment.meteorclient.addons.GithubRepo;
import meteordevelopment.meteorclient.addons.MeteorAddon;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.loader.api.FabricLoader;

@Environment(EnvType.CLIENT)
public final class Electron extends MeteorAddon {
    @Override
    public void onInitialize() {
        final String versionString = FabricLoader.getInstance().getModContainer("viafabric-mc").orElseThrow().getMetadata().getVersion().getFriendlyString();
    }

    @Override
    public String getPackage() {
        return "io.github.racoondog.electron";
    }

    @Override
    public GithubRepo getRepo() {
        return new GithubRepo("RacoonDog", "Electron");
    }
}
