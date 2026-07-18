package com.cukkoo.soundculling.mixin;

import com.cukkoo.soundculling.DampenableSoundInstance;
import net.minecraft.client.sound.AbstractSoundInstance;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(AbstractSoundInstance.class)
public class AbstractSoundInstanceMixin implements DampenableSoundInstance {

    @Unique
    private float soundculling$volumeMultiplier = 1.0f;

    @Override
    public void soundculling$setVolumeMultiplier(float multiplier) {
        this.soundculling$volumeMultiplier = multiplier;
    }

    @Override
    public float soundculling$getVolumeMultiplier() {
        return this.soundculling$volumeMultiplier;
    }

    @Inject(method = "getVolume", at = @At("RETURN"), cancellable = true)
    private void soundculling$modifyVolume(CallbackInfoReturnable<Float> cir) {
        cir.setReturnValue(cir.getReturnValue() * this.soundculling$volumeMultiplier);
    }
}
