package com.cukkoo.soundculling.config;

import com.cukkoo.soundculling.SoundCulling;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * SoundCulling configuration.
 * Saved as config/soundculling.json.
 */
public class SoundCullingConfig {

    private static final Path CONFIG_PATH = FabricLoader.getInstance()
            .getConfigDir().resolve("soundculling.json");

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
                    cfg.validateAndClamp();
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
        validateAndClamp();
        try {
            Files.writeString(CONFIG_PATH,
                    new GsonBuilder().setPrettyPrinting().create().toJson(this));
        } catch (Exception e) {
            SoundCulling.LOGGER.warn("[SoundCulling] Config save failed", e);
        }
    }

    public void validateAndClamp() {
        boolean sanitized = false;

        int clampedMaxTotal = Math.max(1, Math.min(100, maxTotalPerRegion));
        int clampedWindow = Math.max(5, Math.min(200, windowTicks));
        int clampedHostile = Math.max(1, Math.min(50, limitHostile));
        int clampedNeutral = Math.max(1, Math.min(50, limitNeutral));
        int clampedBlock = Math.max(1, Math.min(50, limitBlock));
        int clampedAmbient = Math.max(1, Math.min(50, limitAmbient));
        int clampedDefault = Math.max(1, Math.min(50, limitDefault));
        double clampedRegion = Double.isFinite(regionSize)
                ? Math.max(2.0, Math.min(64.0, regionSize))
                : 16.0;

        sanitized |= maxTotalPerRegion != clampedMaxTotal;
        sanitized |= windowTicks != clampedWindow;
        sanitized |= limitHostile != clampedHostile;
        sanitized |= limitNeutral != clampedNeutral;
        sanitized |= limitBlock != clampedBlock;
        sanitized |= limitAmbient != clampedAmbient;
        sanitized |= limitDefault != clampedDefault;
        sanitized |= Double.compare(regionSize, clampedRegion) != 0;

        maxTotalPerRegion = clampedMaxTotal;
        windowTicks = clampedWindow;
        regionSize = clampedRegion;
        limitHostile = clampedHostile;
        limitNeutral = clampedNeutral;
        limitBlock = clampedBlock;
        limitAmbient = clampedAmbient;
        limitDefault = clampedDefault;

        if (whitelistedSounds == null) {
            whitelistedSounds = new ArrayList<>();
            sanitized = true;
        }
        if (enabledCategories == null) {
            enabledCategories = new ArrayList<>();
            sanitized = true;
        }

        if (sanitized) {
            SoundCulling.LOGGER.warn("[SoundCulling] Invalid config values were clamped to supported ranges.");
        }
    }
}
