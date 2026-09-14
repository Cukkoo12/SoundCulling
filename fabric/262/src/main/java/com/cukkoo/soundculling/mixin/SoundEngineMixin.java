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
import java.util.Map;
import java.util.Set;

@Mixin(SoundEngine.class)
public class SoundEngineMixin {
    @Shadow private Map<SoundInstance, ChannelAccess.ChannelHandle> instanceToChannel;

    @Shadow
    private float calculateVolume(SoundInstance sound) {
        throw new AssertionError();
    }

    @Unique
    private final Set<SoundInstance> soundculling$activeLoops =
            Collections.synchronizedSet(Collections.newSetFromMap(new IdentityHashMap<>()));

    @Inject(
            method = "play",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lnet/minecraft/client/resources/sounds/SoundInstance;resolve(Lnet/minecraft/client/sounds/SoundManager;)Lnet/minecraft/client/sounds/WeighedSoundEvents;",
                            shift = At.Shift.AFTER),
            cancellable = true)
    private void soundculling$onPlay(
            SoundInstance sound, CallbackInfoReturnable<SoundEngine.PlayResult> cir) {
        if (sound instanceof DampenableSoundInstance dampenableSound) {
            dampenableSound.soundculling$setVolumeMultiplier(1.0f);
            soundculling$activeLoops.remove(sound);
        }

        SoundCullingConfig config = SoundCulling.getConfig();

        if (config == null || !config.enabled || sound == null) {
            return;
        }

        if (sound.isRelative()) {
            return;
        }

        Identifier soundId = sound.getIdentifier();

        SoundSource category = sound.getSource();

        if (soundId == null || category == SoundSource.MUSIC) {
            return;
        }

        var resolvedSound = sound.getSound();

        if (resolvedSound == null) {
            return;
        }

        if (sound.getAttenuation() == SoundInstance.Attenuation.LINEAR) {
            try {
                Minecraft minecraft = Minecraft.getInstance();

                if (minecraft.level != null) {
                    var listener = minecraft.getSoundManager().getListenerTransform().position();

                    double dx = sound.getX() - listener.x;

                    double dy = sound.getY() - listener.y;

                    double dz = sound.getZ() - listener.z;

                    double audibleDistance =
                            Math.max(sound.getVolume(), 1.0f)
                                    * resolvedSound.getAttenuationDistance();

                    double distanceSquared = dx * dx + dy * dy + dz * dz;

                    double audibleDistanceSquared = audibleDistance * audibleDistance;

                    if (distanceSquared > audibleDistanceSquared) {
                        return;
                    }
                }
            } catch (RuntimeException exception) {
            }
        }

        if (category != null
                && config.enabledCategories != null
                && !config.enabledCategories.isEmpty()
                && !config.enabledCategories.contains(category.getName())) {
            return;
        }

        try {
            // Tickable sounds can keep playing without isLooping().
            boolean persistentSound = sound.isLooping() || sound instanceof TickableSoundInstance;

            float multiplier =
                    SoundCullingTracker.getVolumeMultiplier(
                            soundId,
                            category,
                            sound.getX(),
                            sound.getY(),
                            sound.getZ(),
                            persistentSound);

            if (multiplier <= 0.0f) {
                soundculling$activeLoops.remove(sound);

                cir.setReturnValue(SoundEngine.PlayResult.NOT_STARTED);

                return;
            }

            if (sound instanceof DampenableSoundInstance dampenableSound) {
                if (multiplier < 1.0f) {
                    dampenableSound.soundculling$setVolumeMultiplier(multiplier);
                }

                if (persistentSound) {
                    soundculling$activeLoops.add(sound);
                }
            }
        } catch (RuntimeException exception) {
            SoundCullingTracker.reportEngineFailure(exception);
        }
    }

    @Inject(method = "tick", at = @At("TAIL"))
    private void soundculling$updateActiveLoops(boolean isGamePaused, CallbackInfo ci) {
        if (isGamePaused || soundculling$activeLoops.isEmpty()) {
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();

        if (minecraft.level == null) {
            soundculling$activeLoops.clear();
            return;
        }

        SoundCullingConfig config = SoundCulling.getConfig();

        for (SoundInstance sound : soundculling$activeLoops.toArray(SoundInstance[]::new)) {
            ChannelAccess.ChannelHandle handle = instanceToChannel.get(sound);

            if (handle == null || handle.isStopped()) {
                soundculling$activeLoops.remove(sound);
                continue;
            }

            if (!(sound instanceof DampenableSoundInstance dampenableSound)) {
                soundculling$activeLoops.remove(sound);
                continue;
            }

            float targetMultiplier = 1.0f;

            if (config != null
                    && config.enabled
                    && !sound.isRelative()
                    && sound.getSource() != SoundSource.MUSIC) {
                Identifier soundId = sound.getIdentifier();

                SoundSource category = sound.getSource();

                boolean categoryEnabled =
                        category == null
                                || config.enabledCategories == null
                                || config.enabledCategories.isEmpty()
                                || config.enabledCategories.contains(category.getName());

                if (soundId != null && categoryEnabled) {
                    try {
                        targetMultiplier =
                                SoundCullingTracker.getRuntimeLoopMultiplier(
                                        soundId,
                                        category,
                                        sound.getX(),
                                        sound.getY(),
                                        sound.getZ());
                    } catch (RuntimeException exception) {
                        targetMultiplier = 1.0f;

                        SoundCullingTracker.reportEngineFailure(exception);
                    }
                }
            }

            float currentMultiplier = dampenableSound.soundculling$getVolumeMultiplier();

            float response = targetMultiplier < currentMultiplier ? 0.18f : 0.10f;

            float nextMultiplier =
                    currentMultiplier + (targetMultiplier - currentMultiplier) * response;

            if (Math.abs(nextMultiplier - targetMultiplier) < 0.005f) {
                nextMultiplier = targetMultiplier;
            }

            nextMultiplier = Math.max(0.0f, Math.min(1.0f, nextMultiplier));

            dampenableSound.soundculling$setVolumeMultiplier(nextMultiplier);

            float channelVolume = calculateVolume(sound);

            handle.execute(channel -> channel.setVolume(channelVolume));
        }
    }
}
