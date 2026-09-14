package com.cukkoo.soundculling.command;

import com.cukkoo.soundculling.SoundCulling;
import com.cukkoo.soundculling.SoundCullingTracker;
import com.cukkoo.soundculling.config.SoundCullingConfig;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;

import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.network.chat.Component;

public class SoundCullingCommands {
    public static void register() {
        ClientCommandRegistrationCallback.EVENT.register(
                (dispatcher, context) -> {
                    var command =
                            ClientCommands.literal("soundculling")
                                    .executes(SoundCullingCommands::status);
                    command.then(
                            ClientCommands.literal("toggle")
                                    .executes(SoundCullingCommands::toggle));
                    command.then(
                            ClientCommands.literal("preset")
                                    .then(
                                            ClientCommands.argument(
                                                            "name", StringArgumentType.word())
                                                    .suggests(
                                                            (ctx, builder) -> {
                                                                builder.suggest("balanced");
                                                                builder.suggest("performance");
                                                                builder.suggest("aggressive");
                                                                return builder.buildFuture();
                                                            })
                                                    .executes(SoundCullingCommands::preset)));
                    command.then(
                            ClientCommands.literal("limit")
                                    .then(
                                            ClientCommands.argument(
                                                            "value",
                                                            IntegerArgumentType.integer(1, 50))
                                                    .executes(ctx -> limit(ctx, false))));
                    command.then(
                            ClientCommands.literal("total")
                                    .then(
                                            ClientCommands.argument(
                                                            "value",
                                                            IntegerArgumentType.integer(1, 100))
                                                    .executes(ctx -> limit(ctx, true))));
                    command.then(
                            ClientCommands.literal("reset").executes(SoundCullingCommands::reset));
                    dispatcher.register(command);
                });
    }

    private static int status(CommandContext<FabricClientCommandSource> ctx) {
        SoundCullingConfig cfg = SoundCulling.getConfig();
        ctx.getSource()
                .sendFeedback(
                        Component.literal(
                                "§6§lSound Culling 2.0\n"
                                        + "§e  Status: §f"
                                        + (cfg.enabled ? "Enabled" : "Disabled")
                                        + "\n"
                                        + "§e  Preset: §f"
                                        + cfg.preset
                                        + "\n"
                                        + "§e  Adaptive load: §f"
                                        + Math.round(
                                                SoundCullingTracker.getAdaptivePressure() * 100.0f)
                                        + "%\n"
                                        + "§e  Sound rate: §f"
                                        + SoundCullingTracker.getEventsPerSecond()
                                        + "/s\n"
                                        + "§a  Triggered: §f"
                                        + SoundCullingTracker.getTotalTriggered()
                                        + "\n"
                                        + "§a  Dampened: §f"
                                        + SoundCullingTracker.getTotalDampened()
                                        + "\n"
                                        + "§a  Culled: §f"
                                        + SoundCullingTracker.getTotalCulled()));
        return 1;
    }

    private static int toggle(CommandContext<FabricClientCommandSource> ctx) {
        SoundCullingConfig cfg = SoundCulling.getConfig().copy();
        cfg.enabled = !cfg.enabled;
        SoundCulling.setConfig(cfg);
        return feedback(ctx, cfg.enabled ? "Enabled" : "Disabled");
    }

    private static int preset(CommandContext<FabricClientCommandSource> ctx) {
        SoundCullingConfig cfg = SoundCulling.getConfig().copy();
        cfg.applyPreset(StringArgumentType.getString(ctx, "name"));
        SoundCulling.setConfig(cfg);
        return feedback(ctx, "Preset set to §e" + cfg.preset);
    }

    private static int limit(CommandContext<FabricClientCommandSource> ctx, boolean regional) {
        int value = IntegerArgumentType.getInteger(ctx, "value");
        SoundCullingConfig cfg = SoundCulling.getConfig().copy();
        if (regional) {
            cfg.maxTotalPerRegion = value;
        } else {
            cfg.limitDefault = value;
        }
        cfg.preset = "CUSTOM";
        SoundCulling.setConfig(cfg);
        return feedback(
                ctx, (regional ? "Regional limit" : "Default limit") + " set to §e" + value);
    }

    private static int reset(CommandContext<FabricClientCommandSource> ctx) {
        long old = SoundCullingTracker.getTotalCulled();
        SoundCullingTracker.resetStats();
        return feedback(ctx, "Session stats reset. §7(Was culled: " + old + ")");
    }

    private static int feedback(CommandContext<FabricClientCommandSource> ctx, String message) {
        ctx.getSource().sendFeedback(Component.literal("§a[SoundCulling] §f" + message));
        return 1;
    }
}
