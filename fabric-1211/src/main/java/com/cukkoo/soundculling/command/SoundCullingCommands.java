package com.cukkoo.soundculling.command;

import com.cukkoo.soundculling.SoundCulling;
import com.cukkoo.soundculling.SoundCullingTracker;
import com.cukkoo.soundculling.config.SoundCullingConfig;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.minecraft.text.Text;

/**
 * /soundculling — in-game commands for config and stats.
 */
public class SoundCullingCommands {

    public static void register() {
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
            dispatcher.register(ClientCommandManager.literal("soundculling")

                    // /soundculling → show status
                    .executes(ctx -> {
                        SoundCullingConfig cfg = SoundCulling.getConfig();
                        ctx.getSource().sendFeedback(Text.literal("")
                                .append(Text.literal("§6§lDynamic Sound Culling\n"))
                                .append(Text.literal("§e  Sound Limit: §f" + cfg.maxSoundsPerRegion + "\n"))
                                .append(Text.literal("§e  Region Total: §f" + cfg.maxTotalPerRegion + "\n"))
                                .append(Text.literal("§e  Time Window: §f" + cfg.windowTicks + " ticks §7(" + String.format("%.1f", cfg.windowTicks / 20.0) + "s)\n"))
                                .append(Text.literal("§e  Region Size: §f" + (int) cfg.regionSize + " blocks\n"))
                                .append(Text.literal("§a  Culled: §f" + SoundCullingTracker.getTotalCulled()))
                        );
                        return 1;
                    })

                    // /soundculling limit <n>
                    .then(ClientCommandManager.literal("limit")
                            .then(ClientCommandManager.argument("value", IntegerArgumentType.integer(1, 50))
                                    .executes(ctx -> {
                                        int val = IntegerArgumentType.getInteger(ctx, "value");
                                        SoundCulling.getConfig().maxSoundsPerRegion = val;
                                        SoundCulling.getConfig().save();
                                        ctx.getSource().sendFeedback(Text.literal("§a[SoundCulling] §fSound limit set to §e" + val));
                                        return 1;
                                    })
                            )
                    )

                    // /soundculling total <n>
                    .then(ClientCommandManager.literal("total")
                            .then(ClientCommandManager.argument("value", IntegerArgumentType.integer(1, 100))
                                    .executes(ctx -> {
                                        int val = IntegerArgumentType.getInteger(ctx, "value");
                                        SoundCulling.getConfig().maxTotalPerRegion = val;
                                        SoundCulling.getConfig().save();
                                        ctx.getSource().sendFeedback(Text.literal("§a[SoundCulling] §fRegion total set to §e" + val));
                                        return 1;
                                    })
                            )
                    )

                    // /soundculling window <n>
                    .then(ClientCommandManager.literal("window")
                            .then(ClientCommandManager.argument("ticks", IntegerArgumentType.integer(5, 200))
                                    .executes(ctx -> {
                                        int val = IntegerArgumentType.getInteger(ctx, "ticks");
                                        SoundCulling.getConfig().windowTicks = val;
                                        SoundCulling.getConfig().save();
                                        ctx.getSource().sendFeedback(Text.literal("§a[SoundCulling] §fTime window set to §e" + val + " ticks §7(" + String.format("%.1f", val / 20.0) + "s)"));
                                        return 1;
                                    })
                            )
                    )

                    // /soundculling region <n>
                    .then(ClientCommandManager.literal("region")
                            .then(ClientCommandManager.argument("size", IntegerArgumentType.integer(2, 64))
                                    .executes(ctx -> {
                                        int val = IntegerArgumentType.getInteger(ctx, "size");
                                        SoundCulling.getConfig().regionSize = val;
                                        SoundCulling.getConfig().save();
                                        ctx.getSource().sendFeedback(Text.literal("§a[SoundCulling] §fRegion size set to §e" + val + " blocks"));
                                        return 1;
                                    })
                            )
                    )

                    // /soundculling reset
                    .then(ClientCommandManager.literal("reset")
                            .executes(ctx -> {
                                int old = SoundCullingTracker.getTotalCulled();
                                SoundCullingTracker.resetStats();
                                ctx.getSource().sendFeedback(Text.literal("§a[SoundCulling] §fStats reset. §7(Was: " + old + ")"));
                                return 1;
                            })
                    )
            );
        });
    }
}
