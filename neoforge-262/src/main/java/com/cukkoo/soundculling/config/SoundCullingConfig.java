package com.cukkoo.soundculling.config;

import com.cukkoo.soundculling.SoundCulling;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.neoforged.fml.loading.FMLPaths;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * SoundCulling configuration.
 * Saved as config/soundculling.json.
 */
public class SoundCullingConfig {

    private static final Path CONFIG_PATH = FMLPaths.CONFIGDIR.get().resolve("soundculling.json");

    /** Max identical sounds from one region within the time window. */
    public int maxSoundsPerRegion = 3;

    /** Max total sounds from one region (all types combined). */
    public int maxTotalPerRegion = 6;

    /** Time window in ticks. 20 ticks = 1 second. */
    public int windowTicks = 20;

    /** Region size in blocks. 16 = chunk size. */
    public double regionSize = 16.0;

    /** Category-specific sound limits. */
    public int limitHostile = 3;
    public int limitNeutral = 2; // Strict for cow farms!
    public int limitBlock = 5;
    public int limitAmbient = 4;
    public int limitDefault = 3;

    /** Whether to enable debug logs in the console. */
    public boolean debugLogging = false;

    /** Sounds that are never culled. */
    public List<String> whitelistedSounds = new ArrayList<>(List.of(
            "minecraft:entity.player.hurt",
            "minecraft:entity.player.death",
            "minecraft:entity.experience_orb.pickup",
            "minecraft:entity.item.pickup",
            "minecraft:block.note_block.harp",
            "minecraft:entity.generic.explode",
            "minecraft:entity.lightning_bolt.thunder",
            "minecraft:entity.creeper.primed",
            "minecraft:entity.tnt.primed"
    ));

    /** Which sound categories to apply culling to. Empty = all. */
    public List<String> enabledCategories = new ArrayList<>();

    public static SoundCullingConfig load() {
        try {
            if (Files.exists(CONFIG_PATH)) {
                SoundCullingConfig cfg = new Gson().fromJson(
                        Files.readString(CONFIG_PATH), SoundCullingConfig.class);
                if (cfg != null) {
                    if (cfg.enabledCategories == null) cfg.enabledCategories = new ArrayList<>();
                    if (cfg.whitelistedSounds == null) cfg.whitelistedSounds = new ArrayList<>();
                    return cfg;
                }
            }
        } catch (Exception e) {
            SoundCulling.LOGGER.warn("[SoundCulling] Config load failed, using defaults", e);
        }
        SoundCullingConfig defaultConfig = new SoundCullingConfig();
        defaultConfig.save();
        return defaultConfig;
    }

    public void save() {
        try {
            Files.writeString(CONFIG_PATH,
                    new GsonBuilder().setPrettyPrinting().create().toJson(this));
        } catch (Exception e) {
            SoundCulling.LOGGER.warn("[SoundCulling] Config save failed", e);
        }
    }
}
