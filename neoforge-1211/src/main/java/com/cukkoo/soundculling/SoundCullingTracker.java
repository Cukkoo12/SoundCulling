package com.cukkoo.soundculling;

import com.cukkoo.soundculling.config.SoundCullingConfig;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundSource;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;

/**
 * Sliding time-window sound counter.
 */
public final class SoundCullingTracker {

    private static final Map<String, Deque<Long>> SOUND_TIMESTAMPS = new HashMap<>();
    private static final Map<String, Deque<Long>> REGION_TIMESTAMPS = new HashMap<>();

    private static long currentTick = 0;
    private static int totalCulled = 0;

    private SoundCullingTracker() {}

    public static void onTick() {
        currentTick++;
        if (currentTick % 100 == 0) {
            cleanOldEntries();
            if (SoundCulling.getConfig() != null && SoundCulling.getConfig().debugLogging) {
                SoundCulling.LOGGER.info("[SoundCullingDebug] Tick: {}, Active Sounds: {}, Regions: {}, Total Culled: {}", currentTick, SOUND_TIMESTAMPS.size(), REGION_TIMESTAMPS.size(), totalCulled);
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

    public static float getVolumeMultiplier(ResourceLocation soundId, SoundSource category, double x, double y, double z) {
        SoundCullingConfig config = SoundCulling.getConfig();

        if (x == 0.0 && y == 0.0 && z == 0.0) {
            return 1.0f;
        }

        int regionX = (int) Math.floor(x / config.regionSize);
        int regionY = (int) Math.floor(y / config.regionSize);
        int regionZ = (int) Math.floor(z / config.regionSize);

        String regionKey = regionX + "|" + regionY + "|" + regionZ;
        String soundKey = soundId.toString() + "|" + regionKey;
        long windowStart = currentTick - config.windowTicks;

        Deque<Long> soundTimes = SOUND_TIMESTAMPS.computeIfAbsent(soundKey, k -> new ArrayDeque<>());
        while (!soundTimes.isEmpty() && soundTimes.peekFirst() < windowStart) {
            soundTimes.pollFirst();
        }
        int count = soundTimes.size();

        Deque<Long> regionTimes = REGION_TIMESTAMPS.computeIfAbsent(regionKey, k -> new ArrayDeque<>());
        while (!regionTimes.isEmpty() && regionTimes.peekFirst() < windowStart) {
            regionTimes.pollFirst();
        }
        int regionCount = regionTimes.size();

        boolean isBehind = false;
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player != null) {
            float pitch = player.getXRot();
            float yaw = player.getYRot();
            float pitchRad = pitch * ((float) Math.PI / 180.0F);
            float yawRad = yaw * ((float) Math.PI / 180.0F);

            double lookX = -Math.sin(yawRad) * Math.cos(pitchRad);
            double lookY = -Math.sin(pitchRad);
            double lookZ = Math.cos(yawRad) * Math.cos(pitchRad);

            double dx = x - player.getX();
            double dy = y - player.getEyeY();
            double dz = z - player.getZ();
            double distSq = dx * dx + dy * dy + dz * dz;
            if (distSq > 0.001) {
                double dist = Math.sqrt(distSq);
                double dot = (dx / dist) * lookX + (dy / dist) * lookY + (dz / dist) * lookZ;
                if (dot < 0.0) {
                    isBehind = true;
                }
            }
        }

        int limit = getCategoryLimit(category, config);
        int activeLimit = isBehind ? Math.max(1, limit / 2) : limit;

        float multiplier = 1.0f;

        if (count >= limit * 2) {
            multiplier = 0.0f;
        } else if (count >= activeLimit) {
            multiplier = isBehind ? 0.07f : 0.15f;
        } else if (isBehind) {
            multiplier = 0.5f;
        }

        if (multiplier > 0.0f) {
            if (regionCount >= config.maxTotalPerRegion * 2) {
                multiplier = 0.0f;
            } else if (regionCount >= config.maxTotalPerRegion) {
                multiplier = Math.min(multiplier, isBehind ? 0.07f : 0.15f);
            }
        }

        if (multiplier > 0.0f) {
            soundTimes.addLast(currentTick);
            regionTimes.addLast(currentTick);
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

    public static void resetStats() {
        totalCulled = 0;
    }
}
