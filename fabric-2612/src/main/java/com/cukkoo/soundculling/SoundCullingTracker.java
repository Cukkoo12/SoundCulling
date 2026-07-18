package com.cukkoo.soundculling;

import com.cukkoo.soundculling.config.SoundCullingConfig;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundSource;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;

/**
 * Sliding time-window sound counter.
 *
 * Tracks how many times each sound has played per region within the last N ticks.
 * If a sound exceeds the limit, it gets culled.
 */
public final class SoundCullingTracker {

    private static final Map<String, Deque<Long>> SOUND_TIMESTAMPS = new HashMap<>();
    private static final Map<String, Deque<Long>> REGION_TIMESTAMPS = new HashMap<>();

    private static long currentTick = 0;
    private static int totalDampened = 0;
    private static int totalCulled = 0;

    private SoundCullingTracker() {}

    /** Called every client tick to advance the tick counter. */
    public static void onTick() {
        currentTick++;
        if (currentTick % 100 == 0) {
            cleanOldEntries();
            if (SoundCulling.getConfig() != null && SoundCulling.getConfig().debugLogging) {
                SoundCulling.LOGGER.info("[SoundCullingDebug] Tracked sounds: {}, regions: {}, dampened: {}, culled: {}",
                        SOUND_TIMESTAMPS.size(), REGION_TIMESTAMPS.size(), totalDampened, totalCulled);
            }
        }
    }

    public static int getCategoryLimit(SoundSource category, SoundCullingConfig config) {
        if (category == null) return config.limitDefault;
        switch (category) {
            case HOSTILE: return config.limitHostile;
            case NEUTRAL: return config.limitNeutral;
            case BLOCKS: return config.limitBlock;
            case AMBIENT: return config.limitAmbient;
            default: return config.limitDefault;
        }
    }

    /** Returns the volume multiplier (0.0f = fully culled, 1.0f = no cull/normal). */
    public static float getVolumeMultiplier(Identifier soundId, SoundSource category, double x, double y, double z) {
        SoundCullingConfig config = SoundCulling.getConfig();

        int regionX = (int) Math.floor(x / config.regionSize);
        int regionY = (int) Math.floor(y / config.regionSize);
        int regionZ = (int) Math.floor(z / config.regionSize);

        String regionKey = regionX + "|" + regionY + "|" + regionZ;
        String soundKey = soundId.toString() + "|" + regionKey;
        long windowStart = currentTick - config.windowTicks;

        // Per-sound check
        Deque<Long> soundTimes = SOUND_TIMESTAMPS.computeIfAbsent(soundKey, k -> new ArrayDeque<>());
        while (!soundTimes.isEmpty() && soundTimes.peekFirst() < windowStart) {
            soundTimes.pollFirst();
        }
        int count = soundTimes.size();

        // Per-region total check
        Deque<Long> regionTimes = REGION_TIMESTAMPS.computeIfAbsent(regionKey, k -> new ArrayDeque<>());
        while (!regionTimes.isEmpty() && regionTimes.peekFirst() < windowStart) {
            regionTimes.pollFirst();
        }
        int regionCount = regionTimes.size();

        int limit = getCategoryLimit(category, config);
        boolean categoryHardLimit = (long) count >= (long) limit * 2L;
        boolean regionHardLimit = (long) regionCount >= (long) config.maxTotalPerRegion * 2L;
        boolean overlapLimitReached = count >= limit || regionCount >= config.maxTotalPerRegion;

        float multiplier;
        if (categoryHardLimit || regionHardLimit) {
            multiplier = 0.0f;
        } else if (overlapLimitReached) {
            boolean isBehind = false;
            Minecraft mc = Minecraft.getInstance();
            LocalPlayer player = mc.player;
            if (player != null) {
                double dx = x - player.getX();
                double dy = y - player.getEyeY();
                double dz = z - player.getZ();
                double distSq = dx * dx + dy * dy + dz * dz;
                if (distSq > 0.001) {
                    Vec3 look = player.getLookAngle();
                    isBehind = dx * look.x + dy * look.y + dz * look.z < 0.0;
                }
            }
            multiplier = isBehind ? 0.07f : 0.15f;
        } else {
            multiplier = 1.0f;
        }

        if (multiplier > 0.0f) {
            soundTimes.addLast(currentTick);
            regionTimes.addLast(currentTick);
            if (multiplier < 1.0f) {
                totalDampened++;
            }
        } else {
            totalCulled++;
        }

        return multiplier;
    }

    private static void cleanOldEntries() {
        long windowStart = currentTick - SoundCulling.getConfig().windowTicks;

        SOUND_TIMESTAMPS.entrySet().removeIf(entry -> {
            Deque<Long> times = entry.getValue();
            while (!times.isEmpty() && times.peekFirst() < windowStart) {
                times.pollFirst();
            }
            return times.isEmpty();
        });

        REGION_TIMESTAMPS.entrySet().removeIf(entry -> {
            Deque<Long> times = entry.getValue();
            while (!times.isEmpty() && times.peekFirst() < windowStart) {
                times.pollFirst();
            }
            return times.isEmpty();
        });
    }

    public static int getTotalCulled() {
        return totalCulled;
    }

    public static int getTotalDampened() {
        return totalDampened;
    }

    public static void resetStats() {
        totalDampened = 0;
        totalCulled = 0;
    }
}
