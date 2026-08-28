package com.cukkoo.soundculling.config;

import com.cukkoo.soundculling.SoundCulling;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.loader.api.FabricLoader;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Sound Culling 2.0 configuration.
 *
 * Existing 1.x fields are intentionally kept so old soundculling.json files can
 * be loaded without forcing users to rebuild their configuration.
 */
public class SoundCullingConfig {

    public static final int CURRENT_CONFIG_VERSION = 2;

    private static final Path CONFIG_PATH = FabricLoader.getInstance()
            .getConfigDir().resolve("soundculling.json");

    public int configVersion = CURRENT_CONFIG_VERSION;

    /** Master switch. */
    public boolean enabled = true;

    /** UI preset name. Values are BALANCED, PERFORMANCE, AGGRESSIVE or CUSTOM. */
    public String preset = "BALANCED";

    /** Legacy/base limits retained for backwards compatible config migration. */
    public int maxTotalPerRegion = 6;
    public int windowTicks = 20;
    public double regionSize = 16.0;
    public int limitHostile = 3;
    public int limitNeutral = 2;
    public int limitBlock = 5;
    public int limitAmbient = 4;
    public int limitDefault = 3;

    /** 2.0 adaptive priority engine. */
    public boolean adaptiveCulling = true;
    public int adaptiveStartPerSecond = 40;
    public int adaptiveFullPerSecond = 160;
    public double adaptiveStrength = 0.85;
    public boolean directionalPriority = true;
    public boolean distancePriority = true;
    public boolean protectPlayerSounds = true;

    /** Number of neighbouring spatial cells inspected around the source cell. */
    public int spatialNeighborRadius = 1;

    /** Whether to enable debug summaries in the console. */
    public boolean debugLogging = false;

    /**
     * 1.x exact whitelist. Kept and honoured by the 2.0 rule engine so existing
     * configs continue to behave the same.
     */
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

    /** Which vanilla SoundSource categories are affected. Empty = all. */
    public List<String> enabledCategories = new ArrayList<>();

    /**
     * 2.0 wildcard rules. Later matching rules override earlier rules.
     * Patterns support '*', for example presencefootsteps:* or minecraft:entity.cow.*.
     */
    public List<SoundRule> rules = defaultRules();

    public static final class SoundRule {
        public String pattern = "";
        public String action = "NORMAL";

        public SoundRule() {
        }

        public SoundRule(String pattern, String action) {
            this.pattern = pattern;
            this.action = action;
        }

        public SoundRule copy() {
            return new SoundRule(pattern, action);
        }
    }

    private static List<SoundRule> defaultRules() {
        ArrayList<SoundRule> defaults = new ArrayList<>();
        // General protection for player combat feedback and Better Combat issue #3.
        defaults.add(new SoundRule("minecraft:entity.player.attack.*", "CRITICAL"));
        defaults.add(new SoundRule("bettercombat:*", "HIGH"));
        return defaults;
    }

    public static SoundCullingConfig load() {
        try {
            if (Files.exists(CONFIG_PATH)) {
                String raw = Files.readString(CONFIG_PATH);
                JsonObject root = JsonParser.parseString(raw).getAsJsonObject();
                boolean legacyConfig = !root.has("configVersion");
                SoundCullingConfig cfg = new Gson().fromJson(root, SoundCullingConfig.class);
                if (cfg != null) {
                    if (legacyConfig) {
                        cfg.configVersion = 1;
                    }
                    boolean migrated = cfg.migrateFromLegacy();
                    cfg.validateAndClamp();
                    if (migrated) {
                        cfg.save();
                    }
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

    /**
     * Migration is deliberately conservative: all 1.x values stay untouched and
     * only missing 2.0 structures are restored.
     */
    private boolean migrateFromLegacy() {
        boolean migrated = false;
        if (configVersion < CURRENT_CONFIG_VERSION) {
            SoundCulling.LOGGER.info("[SoundCulling] Migrating configuration from v{} to v{}",
                    configVersion, CURRENT_CONFIG_VERSION);
            configVersion = CURRENT_CONFIG_VERSION;
            preset = "CUSTOM";
            migrated = true;
        }

        if (rules == null) {
            rules = defaultRules();
        }
        if (whitelistedSounds == null) {
            whitelistedSounds = new ArrayList<>();
        }
        if (enabledCategories == null) {
            enabledCategories = new ArrayList<>();
        }
        return migrated;
    }

    public void save() {
        validateAndClamp();
        configVersion = CURRENT_CONFIG_VERSION;
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
        int clampedAdaptiveStart = Math.max(10, Math.min(500, adaptiveStartPerSecond));
        int clampedAdaptiveFull = Math.max(clampedAdaptiveStart + 10, Math.min(1000, adaptiveFullPerSecond));
        int clampedNeighborRadius = Math.max(0, Math.min(2, spatialNeighborRadius));
        double clampedStrength = Double.isFinite(adaptiveStrength)
                ? Math.max(0.0, Math.min(1.0, adaptiveStrength))
                : 0.85;
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
        sanitized |= adaptiveStartPerSecond != clampedAdaptiveStart;
        sanitized |= adaptiveFullPerSecond != clampedAdaptiveFull;
        sanitized |= spatialNeighborRadius != clampedNeighborRadius;
        sanitized |= Double.compare(adaptiveStrength, clampedStrength) != 0;
        sanitized |= Double.compare(regionSize, clampedRegion) != 0;

        maxTotalPerRegion = clampedMaxTotal;
        windowTicks = clampedWindow;
        regionSize = clampedRegion;
        limitHostile = clampedHostile;
        limitNeutral = clampedNeutral;
        limitBlock = clampedBlock;
        limitAmbient = clampedAmbient;
        limitDefault = clampedDefault;
        adaptiveStartPerSecond = clampedAdaptiveStart;
        adaptiveFullPerSecond = clampedAdaptiveFull;
        adaptiveStrength = clampedStrength;
        spatialNeighborRadius = clampedNeighborRadius;

        if (preset == null) {
            preset = "BALANCED";
            sanitized = true;
        } else {
            String normalized = preset.toUpperCase(Locale.ROOT);
            if (!normalized.equals("BALANCED")
                    && !normalized.equals("PERFORMANCE")
                    && !normalized.equals("AGGRESSIVE")
                    && !normalized.equals("CUSTOM")) {
                normalized = "CUSTOM";
                sanitized = true;
            }
            preset = normalized;
        }

        if (whitelistedSounds == null) {
            whitelistedSounds = new ArrayList<>();
            sanitized = true;
        }
        if (enabledCategories == null) {
            enabledCategories = new ArrayList<>();
            sanitized = true;
        }
        if (rules == null) {
            rules = defaultRules();
            sanitized = true;
        }

        rules.removeIf(rule -> rule == null || rule.pattern == null || rule.pattern.isBlank());
        for (SoundRule rule : rules) {
            if (rule.action == null || rule.action.isBlank()) {
                rule.action = "NORMAL";
                sanitized = true;
            } else {
                rule.action = normalizeAction(rule.action);
            }
        }

        if (sanitized) {
            SoundCulling.LOGGER.warn("[SoundCulling] Invalid config values were sanitized.");
        }
    }

    public void applyPreset(String requestedPreset) {
        String value = requestedPreset == null ? "BALANCED" : requestedPreset.toUpperCase(Locale.ROOT);
        switch (value) {
            case "PERFORMANCE" -> {
                preset = "PERFORMANCE";
                maxTotalPerRegion = 6;
                limitHostile = 3;
                limitNeutral = 2;
                limitBlock = 4;
                limitAmbient = 3;
                limitDefault = 3;
                adaptiveStartPerSecond = 30;
                adaptiveFullPerSecond = 120;
                adaptiveStrength = 0.95;
            }
            case "AGGRESSIVE" -> {
                preset = "AGGRESSIVE";
                maxTotalPerRegion = 4;
                limitHostile = 2;
                limitNeutral = 2;
                limitBlock = 3;
                limitAmbient = 2;
                limitDefault = 2;
                adaptiveStartPerSecond = 20;
                adaptiveFullPerSecond = 80;
                adaptiveStrength = 1.0;
            }
            default -> {
                preset = "BALANCED";
                maxTotalPerRegion = 8;
                limitHostile = 4;
                limitNeutral = 3;
                limitBlock = 6;
                limitAmbient = 4;
                limitDefault = 4;
                adaptiveStartPerSecond = 40;
                adaptiveFullPerSecond = 160;
                adaptiveStrength = 0.85;
            }
        }
    }

    public List<SoundRule> copyRules() {
        ArrayList<SoundRule> copy = new ArrayList<>();
        if (rules != null) {
            for (SoundRule rule : rules) {
                if (rule != null) {
                    copy.add(rule.copy());
                }
            }
        }
        return copy;
    }

    public void addOrReplaceRule(String pattern, String action) {
        if (pattern == null || pattern.isBlank()) {
            return;
        }
        if (rules == null) {
            rules = new ArrayList<>();
        }
        String trimmed = pattern.trim();
        rules.removeIf(rule -> rule != null && trimmed.equalsIgnoreCase(rule.pattern));
        rules.add(new SoundRule(trimmed, normalizeAction(action)));
    }

    public static String normalizeAction(String action) {
        if (action == null) {
            return "NORMAL";
        }
        return switch (action.toUpperCase(Locale.ROOT)) {
            case "NEVER_CULL", "CRITICAL", "HIGH", "NORMAL", "AGGRESSIVE", "ALWAYS_CULL" ->
                    action.toUpperCase(Locale.ROOT);
            default -> "NORMAL";
        };
    }
}
