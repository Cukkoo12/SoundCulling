package com.cukkoo.soundculling;

import com.cukkoo.soundculling.config.SoundCullingConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;

public final class SoundCullingTracker {

    private static final Map<Long, RegionState> REGIONS = new LinkedHashMap<>();
    private static final PriorityQueue<RegionExpiry> CLEANUP_QUEUE =
            new PriorityQueue<>(Comparator.comparingLong(RegionExpiry::dueTick));
    private static final WindowCounter GLOBAL_EVENTS = new WindowCounter();

    private static final LinkedHashMap<String, MutableRecentSound> RECENT_SOUNDS =
            new LinkedHashMap<>(32, 0.75f, true);

    private static long currentTick = 0L;
    private static boolean wasInWorld = false;

    private static long totalTriggered = 0L;
    private static long totalPlayed = 0L;
    private static long totalDampened = 0L;
    private static long totalCulled = 0L;

    private static int lastEventsPerSecond = 0;
    private static float lastAdaptivePressure = 0.0f;
    private static long lastFailureLogTick = -1000L;


    private SoundCullingTracker() {
    }

    public static void onTick(boolean inWorld) {
        currentTick++;

        if (wasInWorld && !inWorld) {
            resetRuntime();
        }

        wasInWorld = inWorld;

        processExpirations();

        SoundCullingConfig config = SoundCulling.getConfig();

        if (config != null && config.enabled) {
            lastEventsPerSecond =
                    GLOBAL_EVENTS.countSince(currentTick - 20L);

            lastAdaptivePressure =
                    calculateAdaptivePressure(
                            lastEventsPerSecond,
                            config
                    );
        } else {
            lastEventsPerSecond = 0;
            lastAdaptivePressure = 0.0f;
        }

        if (config != null
                && config.enabled
                && config.debugLogging
                && currentTick % 100L == 0L) {

            SoundCulling.LOGGER.info(
                    "[SoundCullingDebug] regions={}, eps={}, pressure={}%, triggered={}, dampened={}, culled={}",
                    REGIONS.size(),
                    lastEventsPerSecond,
                    Math.round(lastAdaptivePressure * 100.0f),
                    totalTriggered,
                    totalDampened,
                    totalCulled
            );
        }
    }
    public static int getCategoryLimit(SoundSource category, SoundCullingConfig config) {
        if (category == null) {
            return config.limitDefault;
        }
        return switch (category) {
            case HOSTILE -> config.limitHostile;
            case NEUTRAL -> config.limitNeutral;
            case BLOCKS -> config.limitBlock;
            case AMBIENT -> config.limitAmbient;
            default -> config.limitDefault;
        };
    }

    public static float getVolumeMultiplier(
            ResourceLocation soundId,
            SoundSource category,
            double x,
            double y,
            double z
    ) {
        return getVolumeMultiplier(
                soundId,
                category,
                x,
                y,
                z,
                false
        );
    }

    public static float getVolumeMultiplier(
            ResourceLocation soundId,
            SoundSource category,
            double x,
            double y,
            double z,
            boolean looping
    ) {
        return evaluateVolumeMultiplier(
                soundId,
                category,
                x,
                y,
                z,
                looping,
                true
        );
    }

    // Recheck loops without counting a new sound.
    public static float getRuntimeLoopMultiplier(
            ResourceLocation soundId,
            SoundSource category,
            double x,
            double y,
            double z
    ) {
        return evaluateVolumeMultiplier(
                soundId,
                category,
                x,
                y,
                z,
                true,
                false
        );
    }

    private static float evaluateVolumeMultiplier(
            ResourceLocation soundId,
            SoundSource category,
            double x,
            double y,
            double z,
            boolean looping,
            boolean recordEvent
    ) {
        SoundCullingConfig config = SoundCulling.getConfig();

        if (config == null
                || !config.enabled
                || soundId == null) {
            return 1.0f;
        }

        String soundIdString = soundId.toString();

        if (recordEvent) {
            totalTriggered++;
            GLOBAL_EVENTS.add(currentTick);

            lastEventsPerSecond =
                    GLOBAL_EVENTS.countSince(currentTick - 20L);

            lastAdaptivePressure =
                    calculateAdaptivePressure(
                            lastEventsPerSecond,
                            config
                    );
        }

        float adaptivePressure =
                lastAdaptivePressure;

        long windowStart =
                currentTick - config.windowTicks;

        int cellX =
                floorCell(x, config.regionSize);

        int cellY =
                floorCell(y, config.regionSize);

        int cellZ =
                floorCell(z, config.regionSize);

        long cellKey =
                packCell(cellX, cellY, cellZ);

        long sourceKey =
                packCell(
                        (int) Math.floor(x),
                        (int) Math.floor(y),
                        (int) Math.floor(z)
                );

        // Cap one source's share of the region limit.
        int sourceContributionCap =
                Math.max(
                        2,
                        Math.round(
                                config.windowTicks / 10.0f
                        )
                );
        if (recordEvent) {
            RegionState currentRegion =
                    REGIONS.computeIfAbsent(
                            cellKey,
                            ignored -> new RegionState()
                    );

            currentRegion.record(
                    soundIdString,
                    sourceKey,
                    currentTick,
                    windowStart,
                    sourceContributionCap
            );

            scheduleCleanupIfNeeded(
                    cellKey,
                    currentRegion,
                    config.windowTicks
            );
        }

        SoundRuleEngine.RuleResult rule =
                SoundRuleEngine.resolve(
                        soundIdString,
                        config
                );

        if (rule.alwaysCull()) {
            if (recordEvent) {
                recordDecision(
                        soundIdString,
                        category,
                        0.0f,
                        0.0f,
                        adaptivePressure
                );
            }

            return 0.0f;
        }

        if (rule.neverCull()) {
            if (recordEvent) {
                recordDecision(
                        soundIdString,
                        category,
                        1.0f,
                        rule.priorityMultiplier(),
                        adaptivePressure
                );
            }

            return 1.0f;
        }

        PlayerRelation relation =
                calculatePlayerRelation(x, y, z);

        float priority =
                calculatePriority(
                        category,
                        relation,
                        rule.priorityMultiplier(),
                        config
                );

        NearbyCounts nearby =
                countNearby(
                        soundIdString,
                        sourceKey,
                        cellX,
                        cellY,
                        cellZ,
                        Math.max(
                                0,
                                config.spatialNeighborRadius
                        ),
                        windowStart,
                        sourceContributionCap
                );

        float adaptiveLimitFactor = 1.0f;

        if (config.adaptiveCulling) {
            float protectedPressure =
                    adaptivePressure
                            / Math.max(1.0f, priority);

            adaptiveLimitFactor =
                    1.0f
                            - protectedPressure
                            * (float) config.adaptiveStrength
                            * 0.48f;

            adaptiveLimitFactor =
                    clamp(
                            adaptiveLimitFactor,
                            0.48f,
                            1.0f
                    );
        }

        int baseCategoryLimit =
                getCategoryLimit(
                        category,
                        config
                );

        int effectiveSoundLimit =
                Math.max(
                        1,
                        Math.round(
                                baseCategoryLimit
                                        * priority
                                        * adaptiveLimitFactor
                        )
                );

        float regionPriorityFactor =
                clamp(
                        0.82f
                                + priority * 0.18f,
                        0.72f,
                        1.35f
                );

        int effectiveRegionLimit =
                Math.max(
                        2,
                        Math.round(
                                config.maxTotalPerRegion
                                        * regionPriorityFactor
                                        * adaptiveLimitFactor
                        )
                );

        int effectiveSourceLimit =
                Math.max(
                        2,
                        Math.round(
                                effectiveRegionLimit * 0.55f
                        )
                );

        int sourceAdmissionLimit;

        if (adaptivePressure >= 0.85f) {

            sourceAdmissionLimit =
                    Math.max(
                            3,
                            Math.min(
                                    6,
                                    effectiveSourceLimit
                            )
                    );

        } else if (adaptivePressure >= 0.50f) {

            sourceAdmissionLimit =
                    Math.max(
                            6,
                            Math.min(
                                    10,
                                    effectiveSourceLimit * 2
                            )
                    );

        } else {

            sourceAdmissionLimit =
                    Math.max(
                            10,
                            Math.min(
                                    16,
                                    effectiveSourceLimit * 4
                            )
                    );
        }


        boolean repeatedPersistentBurst =
                looping
                        && nearby.sameSoundCount
                        > sourceAdmissionLimit;


        boolean sourceAdmissionGate =
                recordEvent

                        && nearby.sameSourceCount
                        > sourceAdmissionLimit

                        && (
                        !looping
                                || repeatedPersistentBurst
                )

                        && category != SoundSource.PLAYERS
                        && category != SoundSource.HOSTILE

                        && rule.priorityMultiplier() <= 1.05f

                        && !soundIdString.equals(
                        "minecraft:entity.generic.eat"
                )
                        && !soundIdString.equals(
                        "minecraft:entity.generic.drink"
                )
                        && !soundIdString.equals(
                        "minecraft:entity.player.burp"
                );


        if (sourceAdmissionGate) {

            recordDecision(
                    soundIdString,
                    category,
                    0.0f,
                    priority,
                    adaptivePressure
            );

            return 0.0f;
        }

        float soundRatio =
                nearby.sameSoundCount
                        / effectiveSoundLimit;

        float regionRatio =
                nearby.totalCount
                        / effectiveRegionLimit;

        float sourceRatio =
                nearby.sameSourceCount
                        / effectiveSourceLimit;

        float loadRatio =
                Math.max(
                        soundRatio,
                        Math.max(
                                regionRatio,
                                sourceRatio
                        )
                );

        boolean novelSound =
                recordEvent
                        && nearby.sameSoundCount <= 1.01f;
        float multiplier;

        if (loadRatio <= 1.0f) {
            multiplier = 1.0f;
        } else {
            float hardThreshold = 2.0f;

            if (priority > 1.0f) {
                hardThreshold +=
                        (priority - 1.0f) * 0.95f;
            } else {
                hardThreshold -=
                        (1.0f - priority) * 0.35f;
            }

            hardThreshold =
                    clamp(
                            hardThreshold,
                            1.45f,
                            3.6f
                    );

            if (loadRatio >= hardThreshold) {
                multiplier =
                        novelSound
                                ? 0.30f
                                : 0.0f;
            } else {
                float severity =
                        (loadRatio - 1.0f)
                                / (hardThreshold - 1.0f);

                multiplier =
                        lerp(
                                0.68f,
                                0.08f,
                                severity
                        );

                if (config.directionalPriority
                        && relation.behind) {
                    multiplier *= 0.62f;
                }

                if (config.distancePriority
                        && relation.distance > 24.0) {
                    multiplier *= 0.78f;
                }

                if (config.adaptiveCulling) {
                    multiplier *=
                            1.0f
                                    - adaptivePressure
                                    * (float) config.adaptiveStrength
                                    * 0.28f;
                }

                multiplier =
                        clamp(
                                multiplier,
                                0.04f,
                                1.0f
                        );

                if (multiplier <= 0.045f) {
                    multiplier = 0.0f;
                }
                if (novelSound
                        && multiplier < 0.30f) {
                    multiplier = 0.30f;
                }
            }
        }

        if (looping
                && multiplier < 0.12f) {
            multiplier = 0.12f;
        }

        boolean protectedPlayerFeedback =
                config.protectPlayerSounds
                        && (
                                category == SoundSource.PLAYERS
                                        || soundIdString.equals(
                                        "minecraft:entity.generic.eat"
                                )
                                        || soundIdString.equals(
                                        "minecraft:entity.generic.drink"
                                )
                                        || soundIdString.equals(
                                        "minecraft:entity.player.burp"
                                )
                        );

        if (protectedPlayerFeedback
                && multiplier < 0.35f) {
            multiplier = 0.35f;
        }

        if (recordEvent) {
            recordDecision(
                    soundIdString,
                    category,
                    multiplier,
                    priority,
                    adaptivePressure
            );
        }

        return multiplier;
    }
    private static float calculateAdaptivePressure(int eventsPerSecond, SoundCullingConfig config) {
        if (!config.adaptiveCulling || eventsPerSecond <= config.adaptiveStartPerSecond) {
            return 0.0f;
        }
        int range = Math.max(1, config.adaptiveFullPerSecond - config.adaptiveStartPerSecond);
        return clamp((eventsPerSecond - config.adaptiveStartPerSecond) / (float) range, 0.0f, 1.0f);
    }

    private static float calculatePriority(SoundSource category, PlayerRelation relation,
                                           float ruleMultiplier, SoundCullingConfig config) {
        float priority = switch (category) {
            case PLAYERS -> 1.35f;
            case HOSTILE -> 1.20f;
            case WEATHER -> 1.05f;
            case BLOCKS -> 0.92f;
            case NEUTRAL -> 0.78f;
            case AMBIENT -> 0.72f;
            default -> 1.0f;
        };

        if (config.protectPlayerSounds && category == SoundSource.PLAYERS) {
            priority *= 1.28f;
            if (relation.distance < 6.0) {
                priority *= 1.30f;
            }
        }

        if (config.distancePriority) {
            if (relation.distance < 4.0) {
                priority *= 1.25f;
            } else if (relation.distance < 8.0) {
                priority *= 1.12f;
            } else if (relation.distance > 32.0) {
                priority *= 0.70f;
            } else if (relation.distance > 18.0) {
                priority *= 0.84f;
            }
        }

        if (config.directionalPriority) {
            if (relation.dot > 0.55) {
                priority *= 1.12f;
            } else if (relation.behind) {
                priority *= 0.78f;
            }
        }

        priority *= ruleMultiplier;
        return clamp(priority, 0.25f, 3.5f);
    }

    private static PlayerRelation calculatePlayerRelation(double x, double y, double z) {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        if (player == null) {
            return PlayerRelation.UNKNOWN;
        }

        double dx = x - player.getX();
        double dy = y - player.getEyeY();
        double dz = z - player.getZ();
        double distSq = dx * dx + dy * dy + dz * dz;
        if (distSq <= 0.0001) {
            return new PlayerRelation(0.0, 1.0, false);
        }

        double distance = Math.sqrt(distSq);
        Vec3 look = player.getLookAngle();
        double dot = (dx * look.x + dy * look.y + dz * look.z) / distance;
        return new PlayerRelation(distance, dot, dot < -0.15);
    }

    private static NearbyCounts countNearby(
            String soundId,
            long sourceKey,
            int cellX,
            int cellY,
            int cellZ,
            int radius,
            long windowStart,
            int sourceContributionCap
    ) {
        float total = 0.0f;
        float same = 0.0f;
        float source = 0.0f;

        for (int dx = -radius; dx <= radius; dx++) {
            for (int dy = -radius; dy <= radius; dy++) {
                for (int dz = -radius; dz <= radius; dz++) {

                    long key =
                            packCell(
                                    cellX + dx,
                                    cellY + dy,
                                    cellZ + dz
                            );

                    RegionState state =
                            REGIONS.get(key);

                    if (state == null) {
                        continue;
                    }

                    int distanceSq =
                            dx * dx
                                    + dy * dy
                                    + dz * dz;

                    float weight =
                            distanceSq == 0
                                    ? 1.0f
                                    : 1.0f
                                    / (
                                    1.0f
                                            + distanceSq * 1.4f
                            );

                    total +=
                            state.countFairTotal(
                                    windowStart,
                                    sourceContributionCap
                            ) * weight;

                    same +=
                            state.countSound(
                                    soundId,
                                    windowStart
                            ) * weight;

                    source +=
                            state.countSource(
                                    sourceKey,
                                    windowStart
                            ) * weight;
                }
            }
        }

        return new NearbyCounts(
                total,
                same,
                source
        );
    }

    private static int floorCell(double coordinate, double regionSize) {
        return (int) Math.floor(coordinate / regionSize);
    }

    private static long packCell(int x, int y, int z) {
        long px = ((long) x & 0x3FFFFFFL) << 38;
        long py = ((long) y & 0xFFFL) << 26;
        long pz = (long) z & 0x3FFFFFFL;
        return px | py | pz;
    }

    private static void scheduleCleanupIfNeeded(long cellKey, RegionState state, int windowTicks) {
        state.lastTouchedTick = currentTick;
        if (!state.cleanupScheduled) {
            state.cleanupScheduled = true;
            CLEANUP_QUEUE.add(new RegionExpiry(cellKey, currentTick + windowTicks + 40L));
        }
    }

    private static void processExpirations() {
        SoundCullingConfig config = SoundCulling.getConfig();
        int window = config == null ? 20 : config.windowTicks;

        while (!CLEANUP_QUEUE.isEmpty() && CLEANUP_QUEUE.peek().dueTick <= currentTick) {
            RegionExpiry expiry = CLEANUP_QUEUE.poll();
            RegionState state = REGIONS.get(expiry.cellKey);
            if (state == null) {
                continue;
            }

            long nextDue = state.lastTouchedTick + window + 40L;
            if (nextDue <= currentTick) {
                REGIONS.remove(expiry.cellKey);
            } else {
                CLEANUP_QUEUE.add(new RegionExpiry(expiry.cellKey, nextDue));
            }
        }
    }

    private static void recordDecision(String soundId, SoundSource category, float multiplier,
                                       float priority, float adaptivePressure) {
        if (multiplier <= 0.0f) {
            totalCulled++;
        } else {
            totalPlayed++;
            if (multiplier < 1.0f) {
                totalDampened++;
            }
        }

        MutableRecentSound stats = RECENT_SOUNDS.get(soundId);
        if (stats == null) {
            if (RECENT_SOUNDS.size() >= 128) {
                Iterator<Map.Entry<String, MutableRecentSound>> iterator = RECENT_SOUNDS.entrySet().iterator();
                if (iterator.hasNext()) {
                    iterator.next();
                    iterator.remove();
                }
            }
            stats = new MutableRecentSound(soundId);
            RECENT_SOUNDS.put(soundId, stats);
        }

        stats.category = category == null ? "unknown" : category.getName();
        stats.triggered++;
        if (multiplier <= 0.0f) {
            stats.culled++;
        } else if (multiplier < 1.0f) {
            stats.dampened++;
        }
        stats.lastMultiplier = multiplier;
        stats.lastPriority = priority;
        stats.lastPressure = adaptivePressure;
        stats.lastTick = currentTick;
    }

    public static void reportEngineFailure(Exception exception) {
        if (currentTick - lastFailureLogTick >= 100L) {
            lastFailureLogTick = currentTick;
            SoundCulling.LOGGER.warn(
                    "[SoundCulling] Sound evaluation failed; allowing the sound to play to avoid a client crash.",
                    exception
            );
        }
    }

    public static List<RecentSoundSnapshot> getRecentSounds(int limit) {
        int wanted = Math.max(0, Math.min(32, limit));
        ArrayList<RecentSoundSnapshot> result = new ArrayList<>(wanted);
        ArrayList<MutableRecentSound> values = new ArrayList<>(RECENT_SOUNDS.values());
        for (int index = values.size() - 1; index >= 0 && result.size() < wanted; index--) {
            MutableRecentSound value = values.get(index);
            result.add(value.snapshot());
        }
        return result;
    }

    public static long getTotalTriggered() {
        return totalTriggered;
    }

    public static long getTotalPlayed() {
        return totalPlayed;
    }

    public static long getTotalCulled() {
        return totalCulled;
    }

    public static long getTotalDampened() {
        return totalDampened;
    }

    public static int getEventsPerSecond() {
        return lastEventsPerSecond;
    }

    public static float getAdaptivePressure() {
        return lastAdaptivePressure;
    }

    public static int getTrackedRegionCount() {
        return REGIONS.size();
    }

    public static void resetStats() {
        totalTriggered = 0L;
        totalPlayed = 0L;
        totalDampened = 0L;
        totalCulled = 0L;
        RECENT_SOUNDS.clear();
    }

    public static void resetRuntime() {
        REGIONS.clear();
        CLEANUP_QUEUE.clear();
        GLOBAL_EVENTS.clear();
        RECENT_SOUNDS.clear();
        totalTriggered = 0L;
        totalPlayed = 0L;
        totalDampened = 0L;
        totalCulled = 0L;
        lastEventsPerSecond = 0;
        lastAdaptivePressure = 0.0f;
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    private static float lerp(float from, float to, float progress) {
        return from + (to - from) * clamp(progress, 0.0f, 1.0f);
    }

    private static final class WindowCounter {
        private final Deque<TickBucket> buckets = new ArrayDeque<>();
        private int total = 0;

        void add(long tick) {
            TickBucket last = buckets.peekLast();
            if (last != null && last.tick == tick) {
                last.count++;
            } else {
                buckets.addLast(new TickBucket(tick, 1));
            }
            total++;
        }

        int countSince(long windowStart) {
            while (!buckets.isEmpty() && buckets.peekFirst().tick < windowStart) {
                total -= buckets.removeFirst().count;
            }
            return total;
        }

        void clear() {
            buckets.clear();
            total = 0;
        }
    }

    private static final class TickBucket {
        private final long tick;
        private int count;

        private TickBucket(long tick, int count) {
            this.tick = tick;
            this.count = count;
        }
    }

    private static final class RegionState {

        private final WindowCounter total =
                new WindowCounter();

        private final LinkedHashMap<String, WindowCounter> sounds =
                new LinkedHashMap<>(
                        16,
                        0.75f,
                        true
                );

        private final LinkedHashMap<Long, WindowCounter> sources =
                new LinkedHashMap<>(
                        32,
                        0.75f,
                        true
                );

        private long lastTouchedTick = 0L;
        private boolean cleanupScheduled = false;

        private long fairCacheTick =
                Long.MIN_VALUE;

        private long fairCacheWindowStart =
                Long.MIN_VALUE;

        private int fairCacheCap =
                -1;

        private float fairCacheValue =
                0.0f;


        void record(
                String soundId,
                long sourceKey,
                long tick,
                long windowStart,
                int sourceContributionCap
        ) {
            total.add(tick);

            WindowCounter soundCounter =
                    sounds.get(soundId);

            if (soundCounter == null) {

                if (sounds.size() >= 128) {
                    Iterator<Map.Entry<String, WindowCounter>> iterator =
                            sounds.entrySet().iterator();

                    if (iterator.hasNext()) {
                        iterator.next();
                        iterator.remove();
                    }
                }

                soundCounter =
                        new WindowCounter();

                sounds.put(
                        soundId,
                        soundCounter
                );
            }

            soundCounter.add(tick);

            WindowCounter sourceCounter =
                    sources.get(sourceKey);

            if (sourceCounter == null) {

                if (sources.size() >= 256) {

                    Iterator<Map.Entry<Long, WindowCounter>> iterator =
                            sources.entrySet().iterator();

                    if (iterator.hasNext()) {
                        iterator.next();
                        iterator.remove();

                        fairCacheTick =
                                Long.MIN_VALUE;
                    }
                }

                sourceCounter =
                        new WindowCounter();

                sources.put(
                        sourceKey,
                        sourceCounter
                );
            }

            int sourceCountBefore =
                    sourceCounter.countSince(
                            windowStart
                    );

            sourceCounter.add(tick);

            if (
                fairCacheTick == tick
                        && fairCacheWindowStart == windowStart
                        && fairCacheCap == sourceContributionCap
                        && sourceCountBefore < sourceContributionCap
            ) {
                fairCacheValue += 1.0f;
            }
        }


        int countSound(
                String soundId,
                long windowStart
        ) {
            WindowCounter counter =
                    sounds.get(soundId);

            if (counter == null) {
                return 0;
            }

            return counter.countSince(
                    windowStart
            );
        }


        int countSource(
                long sourceKey,
                long windowStart
        ) {
            WindowCounter counter =
                    sources.get(sourceKey);

            if (counter == null) {
                return 0;
            }

            return counter.countSince(
                    windowStart
            );
        }


        float countFairTotal(
                long windowStart,
                int sourceContributionCap
        ) {
            if (
                fairCacheTick == currentTick
                        && fairCacheWindowStart == windowStart
                        && fairCacheCap == sourceContributionCap
            ) {
                return fairCacheValue;
            }

            float fairTotal = 0.0f;

            for (WindowCounter counter : sources.values()) {

                int count =
                        counter.countSince(
                                windowStart
                        );

                fairTotal +=
                        Math.min(
                                count,
                                sourceContributionCap
                        );
            }

            fairCacheTick =
                    currentTick;

            fairCacheWindowStart =
                    windowStart;

            fairCacheCap =
                    sourceContributionCap;

            fairCacheValue =
                    fairTotal;

            return fairTotal;
        }
    }

    private static final class MutableRecentSound {
        private final String soundId;
        private String category = "unknown";
        private long triggered = 0L;
        private long dampened = 0L;
        private long culled = 0L;
        private float lastMultiplier = 1.0f;
        private float lastPriority = 1.0f;
        private float lastPressure = 0.0f;
        private long lastTick = 0L;

        private MutableRecentSound(String soundId) {
            this.soundId = soundId;
        }

        RecentSoundSnapshot snapshot() {
            return new RecentSoundSnapshot(
                    soundId, category, triggered, dampened, culled,
                    lastMultiplier, lastPriority, lastPressure, lastTick
            );
        }
    }

    private record RegionExpiry(long cellKey, long dueTick) {
    }

    private record NearbyCounts(
            float totalCount,
            float sameSoundCount,
            float sameSourceCount
    ) {
    }

    private record PlayerRelation(double distance, double dot, boolean behind) {
        private static final PlayerRelation UNKNOWN = new PlayerRelation(0.0, 0.0, false);
    }

    public record RecentSoundSnapshot(
            String soundId,
            String category,
            long triggered,
            long dampened,
            long culled,
            float lastMultiplier,
            float lastPriority,
            float lastPressure,
            long lastTick
    ) {
        public long affected() {
            return dampened + culled;
        }
    }
}
