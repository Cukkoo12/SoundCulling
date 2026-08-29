package com.cukkoo.soundculling.command;

import com.cukkoo.soundculling.SoundCulling;
import com.cukkoo.soundculling.SoundCullingTracker;
import com.cukkoo.soundculling.config.SoundCullingConfig;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.minecraft.network.chat.Component;

/** /soundculling — lightweight client-side status and tuning commands. */
public class SoundCullingCommands {

    public static void register() {
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, context) -> {
            dispatcher.register(ClientCommandManager.literal("soundculling")
                    .executes(ctx -> {
                        SoundCullingConfig cfg = SoundCulling.getConfig();
                        ctx.getSource().sendFeedback(Component.literal("")
                                .append(Component.literal("§6§lSound Culling 2.0\n"))
                                .append(Component.literal("§e  Status: §f" + (cfg.enabled ? "Enabled" : "Disabled") + "\n"))
                                .append(Component.literal("§e  Preset: §f" + cfg.preset + "\n"))
                                .append(Component.literal("§e  Adaptive load: §f" + Math.round(SoundCullingTracker.getAdaptivePressure() * 100.0f) + "%\n"))
                                .append(Component.literal("§e  Sound rate: §f" + SoundCullingTracker.getEventsPerSecond() + "/s\n"))
                                .append(Component.literal("§a  Triggered: §f" + SoundCullingTracker.getTotalTriggered() + "\n"))
                                .append(Component.literal("§a  Dampened: §f" + SoundCullingTracker.getTotalDampened() + "\n"))
                                .append(Component.literal("§a  Culled: §f" + SoundCullingTracker.getTotalCulled()))
                        );
                        return 1;
                    })

                    .then(ClientCommandManager.literal("toggle")
                            .executes(ctx -> {
                                SoundCullingConfig cfg = SoundCulling.getConfig();
                                cfg.enabled = !cfg.enabled;
                                cfg.save();
                                ctx.getSource().sendFeedback(Component.literal(
                                        "§a[SoundCulling] §f" + (cfg.enabled ? "Enabled" : "Disabled")));
                                return 1;
                            }))

                    .then(ClientCommandManager.literal("preset")
                            .then(ClientCommandManager.argument("name", StringArgumentType.word())
                                    .suggests((ctx, builder) -> {
                                        builder.suggest("balanced");
                                        builder.suggest("performance");
                                        builder.suggest("aggressive");
                                        return builder.buildFuture();
                                    })
                                    .executes(ctx -> {
                                        String name = StringArgumentType.getString(ctx, "name");
                                        SoundCullingConfig cfg = SoundCulling.getConfig();
                                        cfg.applyPreset(name);
                                        cfg.save();
                                        ctx.getSource().sendFeedback(Component.literal(
                                                "§a[SoundCulling] §fPreset set to §e" + cfg.preset));
                                        return 1;
                                    })))

                    .then(ClientCommandManager.literal("limit")
                            .then(ClientCommandManager.argument("value", IntegerArgumentType.integer(1, 50))
                                    .executes(ctx -> {
                                        int value = IntegerArgumentType.getInteger(ctx, "value");
                                        SoundCullingConfig cfg = SoundCulling.getConfig();
                                        cfg.limitDefault = value;
                                        cfg.preset = "CUSTOM";
                                        cfg.save();
                                        ctx.getSource().sendFeedback(Component.literal(
                                                "§a[SoundCulling] §fDefault limit set to §e" + value));
                                        return 1;
                                    })))

                    .then(ClientCommandManager.literal("total")
                            .then(ClientCommandManager.argument("value", IntegerArgumentType.integer(1, 100))
                                    .executes(ctx -> {
                                        int value = IntegerArgumentType.getInteger(ctx, "value");
                                        SoundCullingConfig cfg = SoundCulling.getConfig();
                                        cfg.maxTotalPerRegion = value;
                                        cfg.preset = "CUSTOM";
                                        cfg.save();
                                        ctx.getSource().sendFeedback(Component.literal(
                                                "§a[SoundCulling] §fRegional limit set to §e" + value));
                                        return 1;
                                    })))

                    .then(ClientCommandManager.literal("reset")
                            .executes(ctx -> {
                                long old = SoundCullingTracker.getTotalCulled();
                                SoundCullingTracker.resetStats();
                                ctx.getSource().sendFeedback(Component.literal(
                                        "§a[SoundCulling] §fSession stats reset. §7(Was culled: " + old + ")"));
                                return 1;
                            }))
            );
        });
    }
}
