package com.cukkoo.soundculling.mixin;

import com.cukkoo.soundculling.SoundCulling;
import com.cukkoo.soundculling.SoundCullingTracker;
import com.cukkoo.soundculling.DampenableSoundInstance;
import com.cukkoo.soundculling.config.SoundCullingConfig;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.SoundEngine;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(SoundEngine.class)
public class SoundEngineMixin {

    @Inject(method = "play", at = @At("HEAD"))
    private void soundculling$onPlay(SoundInstance sound, CallbackInfo ci) {
        try {
            SoundCullingConfig config = SoundCulling.getConfig();
            if (config == null) return;

            ResourceLocation soundId = sound.getLocation();
            if (soundId == null) return;

            SoundSource category = sound.getSource();
            double x = sound.getX();
            double y = sound.getY();
            double z = sound.getZ();

            String soundIdStr = soundId.toString();
            if (config.whitelistedSounds.contains(soundIdStr)) {
                return;
            }

            if (category != null && !config.enabledCategories.isEmpty()) {
                if (!config.enabledCategories.contains(category.getName())) {
                    return;
                }
            }

            float multiplier = SoundCullingTracker.getVolumeMultiplier(soundId, category, x, y, z);

            if (multiplier < 1.0f && sound instanceof DampenableSoundInstance) {
                ((DampenableSoundInstance) sound).soundculling$setVolumeMultiplier(multiplier);
            }

            if (config.debugLogging) {
                SoundCulling.LOGGER.info("[SoundCullingDebug] {} sound={} multiplier={}",
                        multiplier == 0.0f ? "CULLED" : multiplier < 1.0f ? "DAMPENED" : "PASS",
                        soundIdStr, multiplier);
            }
        } catch (Exception e) {
            SoundCulling.LOGGER.warn("[SoundCulling] Error in mixin: {}", e.getMessage());
        }
    }
}
