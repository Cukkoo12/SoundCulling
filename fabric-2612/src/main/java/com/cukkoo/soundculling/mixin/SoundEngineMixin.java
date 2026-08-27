package com.cukkoo.soundculling.mixin;

import com.cukkoo.soundculling.DampenableSoundInstance;
import com.cukkoo.soundculling.SoundCulling;
import com.cukkoo.soundculling.SoundCullingTracker;
import com.cukkoo.soundculling.config.SoundCullingConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.resources.sounds.TickableSoundInstance;
import net.minecraft.client.sounds.ChannelAccess;
import net.minecraft.client.sounds.SoundEngine;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.client.sounds.WeighedSoundEvents;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

/**
 * Sound Culling 2.0 SoundEngine integration.
 *
 * Handles:
 * - initial one-shot and looping sound decisions
 * - vanilla audible-distance filtering
 * - active looping sound re-evaluation
 * - smooth runtime loop fades
 */
@Mixin(SoundEngine.class)
public class SoundEngineMixin {

    /*
     * Vanilla's map between active SoundInstances and their OpenAL channels.
     *
     * We only use it to update channels belonging to looping sounds which
     * Sound Culling has explicitly registered.
     */
    @Shadow
    private Map<SoundInstance, ChannelAccess.ChannelHandle> instanceToChannel;

    /*
     * Vanilla volume calculation includes the SoundInstance volume and
     * category/master gain. Because AbstractSoundInstanceMixin modifies
     * getVolume(), this automatically includes our current multiplier.
     */
    @Shadow
    private float calculateVolume(SoundInstance sound) {
        throw new AssertionError();
    }

    /*
     * Identity semantics are intentional. Two SoundInstances with equal
     * properties are still two different active audio channels.
     *
     * Only loops are stored here, so a huge burst of one-shot piston sounds
     * does not make the runtime loop manager scan thousands of entries.
     */
    @Unique
    private final Set<SoundInstance> soundculling$activeLoops =
            Collections.newSetFromMap(
                    new IdentityHashMap<>()
            );


    @Inject(
            method = "play",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/resources/sounds/SoundInstance;resolve(Lnet/minecraft/client/sounds/SoundManager;)Lnet/minecraft/client/sounds/WeighedSoundEvents;",
                    shift = At.Shift.AFTER
            ),
            cancellable = true
    )
    private void soundculling$onPlay(
            SoundInstance sound,
            CallbackInfoReturnable<SoundEngine.PlayResult> cir
    ) {
        if (sound instanceof DampenableSoundInstance dampenableSound) {
            /*
             * Some instances can be reused.
             * A new playback attempt must never inherit an old multiplier.
             */
            dampenableSound.soundculling$setVolumeMultiplier(1.0f);
            soundculling$activeLoops.remove(sound);
        }

        SoundCullingConfig config =
                SoundCulling.getConfig();

        if (config == null
                || !config.enabled
                || sound == null) {
            return;
        }

        /*
         * Relative/UI sounds do not have meaningful world-space distance.
         */
        if (sound.isRelative()) {
            return;
        }

        Identifier soundId =
                sound.getIdentifier();

        SoundSource category =
                sound.getSource();

        if (soundId == null
                || category == SoundSource.MUSIC) {
            return;
        }

        /*
         * Vanilla has resolved the concrete sound resource at this point.
         */
        var resolvedSound =
                sound.getSound();

        if (resolvedSound == null) {
            return;
        }

        /*
         * Ignore sounds which vanilla would already make inaudible.
         *
         * This prevents distant sounds from polluting:
         * - Live Sounds
         * - adaptive pressure
         * - regional load
         * - repetition pressure
         */
        if (sound.getAttenuation()
                == SoundInstance.Attenuation.LINEAR) {

            try {
                Minecraft minecraft =
                        Minecraft.getInstance();

                if (minecraft.level != null) {
                    var listener =
                            minecraft.getSoundManager()
                                    .getListenerTransform()
                                    .position();

                    double dx =
                            sound.getX() - listener.x;

                    double dy =
                            sound.getY() - listener.y;

                    double dz =
                            sound.getZ() - listener.z;

                    double audibleDistance =
                            Math.max(
                                    sound.getVolume(),
                                    1.0f
                            )
                                    * resolvedSound
                                    .getAttenuationDistance();

                    double distanceSquared =
                            dx * dx
                                    + dy * dy
                                    + dz * dz;

                    double audibleDistanceSquared =
                            audibleDistance
                                    * audibleDistance;

                    if (distanceSquared
                            > audibleDistanceSquared) {
                        return;
                    }
                }
            } catch (RuntimeException exception) {
                /*
                 * Fail open for unusual modded SoundInstances.
                 */
            }
        }

        if (category != null
                && config.enabledCategories != null
                && !config.enabledCategories.isEmpty()
                && !config.enabledCategories.contains(
                category.getName()
        )) {
            return;
        }

        try {
            /*
             * Persistent audio is broader than SoundInstance.isLooping().
             *
             * Vanilla minecart, entity-bound and various ambient sounds can
             * live for many ticks through TickableSoundInstance even when
             * their looping flag is not the mechanism keeping them alive.
             */
            boolean persistentSound =
                    sound.isLooping()
                            || sound instanceof TickableSoundInstance;

            float multiplier =
                    SoundCullingTracker.getVolumeMultiplier(
                            soundId,
                            category,
                            sound.getX(),
                            sound.getY(),
                            sound.getZ(),
                            persistentSound
                    );

            if (multiplier <= 0.0f) {
                /*
                 * Initial hard culling:
                 * the channel is never started.
                 */
                soundculling$activeLoops.remove(sound);

                cir.setReturnValue(
                        SoundEngine.PlayResult.NOT_STARTED
                );

                return;
            }

            if (sound
                    instanceof DampenableSoundInstance dampenableSound) {

                if (multiplier < 1.0f) {
                    dampenableSound
                            .soundculling$setVolumeMultiplier(
                                    multiplier
                            );
                }

                /*
                 * Register admitted persistent loops for runtime management.
                 */
                if (persistentSound) {
                    soundculling$activeLoops.add(sound);
                }
            }
        } catch (RuntimeException exception) {
            /*
             * Compatibility safety:
             * Sound Culling must never crash the client.
             */
            SoundCullingTracker.reportEngineFailure(
                    exception
            );
        }
    }


    /**
     * Re-evaluates already-running loops after vanilla has completed its
     * regular SoundEngine tick.
     *
     * Normal one-shot sounds never enter this set.
     */
    @Inject(
            method = "tick",
            at = @At("TAIL")
    )
    private void soundculling$updateActiveLoops(
            boolean isGamePaused,
            CallbackInfo ci
    ) {
        if (isGamePaused
                || soundculling$activeLoops.isEmpty()) {
            return;
        }

        Minecraft minecraft =
                Minecraft.getInstance();

        /*
         * World/session cleanup.
         */
        if (minecraft.level == null) {
            soundculling$activeLoops.clear();
            return;
        }

        SoundCullingConfig config =
                SoundCulling.getConfig();

        Iterator<SoundInstance> iterator =
                soundculling$activeLoops.iterator();

        while (iterator.hasNext()) {
            SoundInstance sound =
                    iterator.next();

            ChannelAccess.ChannelHandle handle =
                    instanceToChannel.get(sound);

            /*
             * Vanilla already removed/stopped this sound.
             */
            if (handle == null
                    || handle.isStopped()) {
                iterator.remove();
                continue;
            }

            if (!(sound
                    instanceof DampenableSoundInstance dampenableSound)) {
                iterator.remove();
                continue;
            }

            float targetMultiplier = 1.0f;

            /*
             * Disabled Sound Culling means an already-active loop smoothly
             * returns to vanilla volume rather than remaining dampened.
             */
            if (config != null
                    && config.enabled
                    && !sound.isRelative()
                    && sound.getSource() != SoundSource.MUSIC) {

                Identifier soundId =
                        sound.getIdentifier();

                SoundSource category =
                        sound.getSource();

                boolean categoryEnabled =
                        category == null
                                || config.enabledCategories == null
                                || config.enabledCategories.isEmpty()
                                || config.enabledCategories.contains(
                                category.getName()
                        );

                if (soundId != null
                        && categoryEnabled) {

                    try {
                        targetMultiplier =
                                SoundCullingTracker
                                        .getRuntimeLoopMultiplier(
                                                soundId,
                                                category,
                                                sound.getX(),
                                                sound.getY(),
                                                sound.getZ()
                                        );

                    } catch (RuntimeException exception) {
                        /*
                         * Fail open. Restore toward vanilla volume.
                         */
                        targetMultiplier = 1.0f;

                        SoundCullingTracker.reportEngineFailure(
                                exception
                        );
                    }
                }
            }

            float currentMultiplier =
                    dampenableSound
                            .soundculling$getVolumeMultiplier();

            /*
             * Fade down faster than fade up.
             *
             * DOWN:
             * noticeable load gets controlled quickly without a pop.
             *
             * UP:
             * recovery is gentler so rapidly changing pressure does not
             * create audible pumping.
             */
            float response =
                    targetMultiplier < currentMultiplier
                            ? 0.18f
                            : 0.10f;

            float nextMultiplier =
                    currentMultiplier
                            + (
                            targetMultiplier
                                    - currentMultiplier
                    ) * response;

            /*
             * Snap tiny differences so values do not asymptotically wander.
             */
            if (Math.abs(
                    nextMultiplier - targetMultiplier
            ) < 0.005f) {
                nextMultiplier = targetMultiplier;
            }

            nextMultiplier =
                    Math.max(
                            0.0f,
                            Math.min(
                                    1.0f,
                                    nextMultiplier
                            )
                    );

            dampenableSound
                    .soundculling$setVolumeMultiplier(
                            nextMultiplier
                    );

            /*
             * Push the newly calculated gain into the already-running
             * OpenAL source. No restart, no pop, no new sound event.
             */
            float channelVolume =
                    calculateVolume(sound);

            handle.execute(
                    channel ->
                            channel.setVolume(
                                    channelVolume
                            )
            );
        }
    }
}