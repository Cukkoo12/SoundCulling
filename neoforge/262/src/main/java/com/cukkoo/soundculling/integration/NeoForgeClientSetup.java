package com.cukkoo.soundculling.integration;

import net.neoforged.fml.ModLoadingContext;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;

public class NeoForgeClientSetup {
    public static void registerConfigScreen() {
        ModLoadingContext.get().registerExtensionPoint(
            IConfigScreenFactory.class,
            () -> (container, parent) -> new SoundCullingConfigScreen(parent)
        );
    }
}
