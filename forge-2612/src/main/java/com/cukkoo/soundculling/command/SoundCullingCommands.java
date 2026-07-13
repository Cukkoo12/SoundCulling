package com.cukkoo.soundculling.command;

import com.cukkoo.soundculling.SoundCulling;
import com.cukkoo.soundculling.SoundCullingTracker;
import com.cukkoo.soundculling.config.SoundCullingConfig;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;

/**
 * /soundculling — in-game commands for config and stats.
 */
public class SoundCullingCommands {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("soundculling")

                // /soundculling → show status
                .executes(ctx -> {
                    SoundCullingConfig cfg = SoundCulling.getConfig();
                    ctx.getSource().sendSuccess(() -> Component.literal("")
                            .append(Component.literal("§6§lDynamic Sound Culling\n"))
                            .append(Component.literal("§e  Sound Limit: §f" + cfg.maxSoundsPerRegion + "\n"))
                            .append(Component.literal("§e  Region Total: §f" + cfg.maxTotalPerRegion + "\n"))
                            .append(Component.literal("§e  Time Window: §f" + cfg.windowTicks + " ticks §7(" + String.format("%.1f", cfg.windowTicks / 20.0) + "s)\n"))
                            .append(Component.literal("§e  Region Size: §f" + (int) cfg.regionSize + " blocks\n"))
                            .append(Component.literal("§a  Culled: §f" + SoundCullingTracker.getTotalCulled())),
                            false
                    );
                    return 1;
                })

                // /soundculling limit <n>
                .then(Commands.literal("limit")
                        .then(Commands.argument("value", IntegerArgumentType.integer(1, 50))
                                .executes(ctx -> {
                                    int val = IntegerArgumentType.getInteger(ctx, "value");
                                    SoundCulling.getConfig().maxSoundsPerRegion = val;
                                    SoundCulling.getConfig().save();
                                    ctx.getSource().sendSuccess(() -> Component.literal("§a[SoundCulling] §fSound limit set to §e" + val), false);
                                    return 1;
                                })
                        )
                )

                // /soundculling total <n>
                .then(Commands.literal("total")
                        .then(Commands.argument("value", IntegerArgumentType.integer(1, 100))
                                .executes(ctx -> {
                                    int val = IntegerArgumentType.getInteger(ctx, "value");
                                    SoundCulling.getConfig().maxTotalPerRegion = val;
                                    SoundCulling.getConfig().save();
                                    ctx.getSource().sendSuccess(() -> Component.literal("§a[SoundCulling] §fRegion total set to §e" + val), false);
                                    return 1;
                                })
                        )
                )
        );
    }
}
