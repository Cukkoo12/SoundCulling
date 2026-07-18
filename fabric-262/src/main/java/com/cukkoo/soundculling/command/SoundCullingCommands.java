package com.cukkoo.soundculling.command;

import com.cukkoo.soundculling.SoundCulling;
import com.cukkoo.soundculling.SoundCullingTracker;
import com.cukkoo.soundculling.config.SoundCullingConfig;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.minecraft.network.chat.Component;

/**
 * /soundculling — in-game commands for config and stats.
 */
public class SoundCullingCommands {

    public static void register() {
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, context) -> {
            dispatcher.register(ClientCommands.literal("soundculling")

                    // /soundculling → show status
                    .executes(ctx -> {
                        SoundCullingConfig cfg = SoundCulling.getConfig();
                        ctx.getSource().sendFeedback(Component.literal("")
                                .append(Component.literal("§6§lSound Culling\n"))
                                .append(Component.literal("§e  Default Limit: §f" + cfg.limitDefault + "\n"))
                                .append(Component.literal("§e  Region Total: §f" + cfg.maxTotalPerRegion + "\n"))
                                .append(Component.literal("§e  Time Window: §f" + cfg.windowTicks + " ticks §7(" + String.format("%.1f", cfg.windowTicks / 20.0) + "s)\n"))
                                .append(Component.literal("§e  Region Size: §f" + (int) cfg.regionSize + " blocks\n"))
                                .append(Component.literal("§a  Culled: §f" + SoundCullingTracker.getTotalCulled()))
                        );
                        return 1;
                    })

                    // /soundculling limit <n>
                    .then(ClientCommands.literal("limit")
                            .then(ClientCommands.argument("value", IntegerArgumentType.integer(1, 50))
                                    .executes(ctx -> {
                                        int val = IntegerArgumentType.getInteger(ctx, "value");
                                        SoundCulling.getConfig().limitDefault = val;
                                        SoundCulling.getConfig().save();
                                        ctx.getSource().sendFeedback(Component.literal("§a[SoundCulling] §fDefault limit set to §e" + val));
                                        return 1;
                                    })
                            )
                    )

                    // /soundculling total <n>
                    .then(ClientCommands.literal("total")
                            .then(ClientCommands.argument("value", IntegerArgumentType.integer(1, 100))
                                    .executes(ctx -> {
                                        int val = IntegerArgumentType.getInteger(ctx, "value");
                                        SoundCulling.getConfig().maxTotalPerRegion = val;
                                        SoundCulling.getConfig().save();
                                        ctx.getSource().sendFeedback(Component.literal("§a[SoundCulling] §fRegion total set to §e" + val));
                                        return 1;
                                    })
                            )
                    )

                    // /soundculling window <n>
                    .then(ClientCommands.literal("window")
                            .then(ClientCommands.argument("ticks", IntegerArgumentType.integer(5, 200))
                                    .executes(ctx -> {
                                        int val = IntegerArgumentType.getInteger(ctx, "ticks");
                                        SoundCulling.getConfig().windowTicks = val;
                                        SoundCulling.getConfig().save();
                                        ctx.getSource().sendFeedback(Component.literal("§a[SoundCulling] §fTime window set to §e" + val + " ticks §7(" + String.format("%.1f", val / 20.0) + "s)"));
                                        return 1;
                                    })
                            )
                    )

                    // /soundculling region <n>
                    .then(ClientCommands.literal("region")
                            .then(ClientCommands.argument("size", IntegerArgumentType.integer(2, 64))
                                    .executes(ctx -> {
                                        int val = IntegerArgumentType.getInteger(ctx, "size");
                                        SoundCulling.getConfig().regionSize = val;
                                        SoundCulling.getConfig().save();
                                        ctx.getSource().sendFeedback(Component.literal("§a[SoundCulling] §fRegion size set to §e" + val + " blocks"));
                                        return 1;
                                    })
                            )
                    )

                    // /soundculling reset
                    .then(ClientCommands.literal("reset")
                            .executes(ctx -> {
                                int old = SoundCullingTracker.getTotalCulled();
                                SoundCullingTracker.resetStats();
                                ctx.getSource().sendFeedback(Component.literal("§a[SoundCulling] §fStats reset. §7(Was: " + old + ")"));
                                return 1;
                            })
                    )
            );
        });
    }
}
