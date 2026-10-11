package com.cukkoo.soundculling;

import com.cukkoo.soundculling.command.SoundCullingCommands;
import com.cukkoo.soundculling.config.SoundCullingConfig;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Mod(SoundCulling.MOD_ID)
public class SoundCulling {

    public static final String MOD_ID = "soundculling";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    private static SoundCullingConfig config;

    public SoundCulling(IEventBus modEventBus, ModContainer container) {
        config = SoundCullingConfig.load();
        LOGGER.info("[SoundCulling] 2.1.0 engine initialized — preset={}, adaptive={}, region={} blocks",
                config.preset, config.adaptiveCulling, config.regionSize);

        container.registerExtensionPoint(
                IConfigScreenFactory.class,
                (mc, screen) -> new com.cukkoo.soundculling.integration.SoundCullingConfigScreen(screen)
        );
    }

    public static SoundCullingConfig getConfig() {
        return config;
    }

    public static void setConfig(SoundCullingConfig newConfig) {
        config = newConfig;
        config.save();
    }

    @EventBusSubscriber(modid = MOD_ID, value = Dist.CLIENT)
    public static class ClientEvents {

        @SubscribeEvent
        public static void onClientTick(ClientTickEvent.Post event) {
            Minecraft mc = Minecraft.getInstance();
            SoundCullingTracker.onTick(mc != null && mc.level != null);
        }

        @SubscribeEvent
        public static void onRegisterCommands(RegisterClientCommandsEvent event) {
            SoundCullingCommands.register(event.getDispatcher());
        }
    }
}
