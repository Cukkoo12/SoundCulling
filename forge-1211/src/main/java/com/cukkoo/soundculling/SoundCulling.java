package com.cukkoo.soundculling;

import com.cukkoo.soundculling.command.SoundCullingCommands;
import com.cukkoo.soundculling.config.SoundCullingConfig;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterClientCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Mod(SoundCulling.MOD_ID)
public class SoundCulling {

    public static final String MOD_ID = "soundculling";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    private static SoundCullingConfig config;

    public SoundCulling() {
        config = SoundCullingConfig.load();
        LOGGER.info("[SoundCulling] Forge Initialized — max {}/sound, max {}/region total, window {} ticks, region {}blocks",
                config.maxSoundsPerRegion, config.maxTotalPerRegion,
                config.windowTicks, config.regionSize);

        if (net.minecraftforge.fml.loading.FMLEnvironment.dist.isClient()) {
            com.cukkoo.soundculling.integration.ForgeClientSetup.registerConfigScreen();
        }
    }

    public static SoundCullingConfig getConfig() {
        return config;
    }

    @Mod.EventBusSubscriber(modid = MOD_ID, value = Dist.CLIENT)
    public static class ClientEvents {

        @SubscribeEvent
        public static void onClientTick(TickEvent.ClientTickEvent event) {
            if (event.phase == TickEvent.Phase.END) {
                SoundCullingTracker.onTick();
            }
        }

        @SubscribeEvent
        public static void onRegisterCommands(RegisterClientCommandsEvent event) {
            SoundCullingCommands.register(event.getDispatcher());
        }
    }
}
