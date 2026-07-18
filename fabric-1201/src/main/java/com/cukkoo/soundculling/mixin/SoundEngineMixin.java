package com.cukkoo.soundculling.mixin;

import com.cukkoo.soundculling.SoundCulling;
import com.cukkoo.soundculling.SoundCullingTracker;
import com.cukkoo.soundculling.DampenableSoundInstance;
import com.cukkoo.soundculling.config.SoundCullingConfig;
import net.minecraft.client.sound.SoundInstance;
import net.minecraft.client.sound.SoundSystem;
import net.minecraft.util.Identifier;
import net.minecraft.sound.SoundCategory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(SoundSystem.class)
public class SoundEngineMixin {

    @Inject(method = "play", at = @At("HEAD"))
    private void soundculling$onPlay(SoundInstance sound, CallbackInfo ci) {
        try {
            SoundCullingConfig config = SoundCulling.getConfig();
            if (config == null) return;

            Identifier soundId = sound.getId();
            if (soundId == null) return;

            SoundCategory category = sound.getCategory();
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
                SoundCulling.LOGGER.info("[SoundCullingDebug] {} multiplier={} sound={}",
                        multiplier == 0.0f ? "CULLED" : multiplier < 1.0f ? "DAMPENED" : "PASS",
                        multiplier, soundIdStr);
            }
        } catch (Exception e) {
            SoundCulling.LOGGER.error("[SoundCulling] Mixin error", e);
        }
    }
}
