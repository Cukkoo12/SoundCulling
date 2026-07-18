package com.cukkoo.soundculling.mixin;

import com.cukkoo.soundculling.SoundCulling;
import com.cukkoo.soundculling.SoundCullingTracker;
import com.cukkoo.soundculling.DampenableSoundInstance;
import com.cukkoo.soundculling.config.SoundCullingConfig;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.SoundEngine;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Mixin to SoundEngine.play() to intercept all sounds right before they are played.
 */
@Mixin(SoundEngine.class)
public class SoundEngineMixin {

    @Inject(method = "play", at = @At("HEAD"), cancellable = true)
    private void soundculling$onPlay(SoundInstance sound, CallbackInfoReturnable<SoundEngine.PlayResult> cir) {
        if (sound instanceof DampenableSoundInstance dampenableSound) {
            dampenableSound.soundculling$setVolumeMultiplier(1.0f);
        }

        SoundCullingConfig config = SoundCulling.getConfig();
        if (config == null) return;

        if (sound.isRelative()) return;

        Identifier soundId = sound.getIdentifier();
        SoundSource category = sound.getSource();
        if (category == SoundSource.MUSIC) return;

        double x = sound.getX();
        double y = sound.getY();
        double z = sound.getZ();

        // --- Whitelist kontrolü ---
        String soundIdStr = soundId.toString();
        if (config.whitelistedSounds.contains(soundIdStr)) {
            return;
        }

        // --- Kategori filtresi ---
        if (!config.enabledCategories.isEmpty()) {
            if (!config.enabledCategories.contains(category.getName())) {
                return;
            }
        }

        float multiplier = SoundCullingTracker.getVolumeMultiplier(soundId, category, x, y, z);
        if (multiplier == 0.0f) {
            cir.setReturnValue(SoundEngine.PlayResult.NOT_STARTED);
        } else if (multiplier < 1.0f) {
            if (sound instanceof DampenableSoundInstance dampenableSound) {
                dampenableSound.soundculling$setVolumeMultiplier(multiplier);
            }
        }
    }
}
