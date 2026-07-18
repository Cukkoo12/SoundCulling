package com.cukkoo.soundculling;

import com.cukkoo.soundculling.command.SoundCullingCommands;
import com.cukkoo.soundculling.config.SoundCullingConfig;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SoundCulling implements ClientModInitializer {

    public static final String MOD_ID = "soundculling";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    private static SoundCullingConfig config;

    @Override
    public void onInitializeClient() {
        config = SoundCullingConfig.load();
        LOGGER.info("[SoundCulling] Initialized — default limit {}, max {}/region total, window {} ticks, region {} blocks",
                config.limitDefault, config.maxTotalPerRegion,
                config.windowTicks, config.regionSize);

        // Her tick'te tracker'ı güncelle
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            SoundCullingTracker.onTick();
        });

        // Oyun içi komutları kaydet
        SoundCullingCommands.register();
    }

    public static SoundCullingConfig getConfig() {
        return config;
    }
}
