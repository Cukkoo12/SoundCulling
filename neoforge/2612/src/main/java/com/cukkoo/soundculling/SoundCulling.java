package com.cukkoo.soundculling;

import com.cukkoo.soundculling.command.SoundCullingCommands;
import com.cukkoo.soundculling.config.SoundCullingConfig;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Mod(SoundCulling.MOD_ID)
public class SoundCulling {

    public static final String MOD_ID = "soundculling";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    private static SoundCullingConfig config;

    public SoundCulling(IEventBus modEventBus) {
        config = SoundCullingConfig.load();
        LOGGER.info("[SoundCulling] NeoForge Initialized — max {}/sound, max {}/region total, window {} ticks, region {}blocks",
                config.maxSoundsPerRegion, config.maxTotalPerRegion,
                config.windowTicks, config.regionSize);

        if (net.neoforged.fml.loading.FMLEnvironment.getDist().isClient()) {
            com.cukkoo.soundculling.integration.NeoForgeClientSetup.registerConfigScreen();
        }

        NeoForge.EVENT_BUS.register(this);
    }

    public static SoundCullingConfig getConfig() {
        return config;
    }

    @SubscribeEvent
    public void onClientTick(ClientTickEvent.Post event) {
        SoundCullingTracker.onTick();
    }

    @SubscribeEvent
    public void onRegisterCommands(RegisterClientCommandsEvent event) {
        SoundCullingCommands.register(event.getDispatcher());
    }
}
