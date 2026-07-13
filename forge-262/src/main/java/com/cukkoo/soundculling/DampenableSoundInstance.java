package com.cukkoo.soundculling;

/**
 * Interface injected into AbstractSoundInstance to allow setting dynamic volume multipliers.
 */
public interface DampenableSoundInstance {
    void soundculling$setVolumeMultiplier(float multiplier);
    float soundculling$getVolumeMultiplier();
}
