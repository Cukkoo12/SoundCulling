package com.cukkoo.soundculling.integration;

import com.cukkoo.soundculling.SoundCulling;
import com.cukkoo.soundculling.SoundCullingTracker;
import com.cukkoo.soundculling.config.SoundCullingConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public class SoundCullingConfigScreen extends Screen {

    private final Screen parent;
    private final SoundCullingConfig config;

    private int maxTotal;
    private int windowTicks;
    private int regionSize;
    private boolean debugLogging;

    private int limitHostile;
    private int limitNeutral;
    private int limitBlock;
    private int limitAmbient;
    private int limitDefault;

    private static final int COL_W = 170;
    private static final int COL_GAP = 20;

    public SoundCullingConfigScreen(Screen parent) {
        super(Component.literal("Dynamic Sound Culling"));
        this.parent = parent;
        this.config = SoundCulling.getConfig();
        loadFromConfig();
    }

    private void loadFromConfig() {
        this.maxTotal = config.maxTotalPerRegion;
        this.windowTicks = config.windowTicks;
        this.regionSize = (int) config.regionSize;
        this.debugLogging = config.debugLogging;
        this.limitHostile = config.limitHostile;
        this.limitNeutral = config.limitNeutral;
        this.limitBlock = config.limitBlock;
        this.limitAmbient = config.limitAmbient;
        this.limitDefault = config.limitDefault;
    }

    @Override
    protected void init() {
        refreshUI();
    }

    private void refreshUI() {
        this.clearWidgets();

        int cx = this.width / 2;
        int leftX = cx - COL_W - COL_GAP / 2;
        int rightX = cx + COL_GAP / 2;
        int topY = 50;
        int rowH = 22;

        // === LEFT COLUMN: SYSTEM ===
        addRow(leftX, topY,
                "Max Sounds/Region", "§6", maxTotal, 1, 100, 1,
                v -> maxTotal = v,
                "Maximum identical sounds allowed per region");
        addRow(leftX, topY + rowH,
                "Time Window", "§b", windowTicks, 5, 200, 5,
                v -> windowTicks = v,
                "Time window in ticks (20 ticks = 1 sec)");
        addRow(leftX, topY + rowH * 2,
                "Region Size", "§5", regionSize, 2, 64, 2,
                v -> regionSize = v,
                "Region size in blocks (16 = 1 chunk)");

        // Debug toggle
        this.addRenderableWidget(
                Button.builder(Component.literal(debugLogging ? "§aON" : "§7OFF"), btn -> {
                    debugLogging = !debugLogging;
                    refreshUI();
                }).bounds(leftX + 118, topY + rowH * 3, 48, 16)
                        .tooltip(Tooltip.create(Component.literal("Enable debug logging")))
                        .build()
        );

        // === RIGHT COLUMN: CATEGORY LIMITS ===
        addRow(rightX, topY,
                "Hostile Mobs", "§c", limitHostile, 1, 50, 1,
                v -> limitHostile = v,
                "Zombies, Creepers, Skeletons etc.");
        addRow(rightX, topY + rowH,
                "Neutral Mobs", "§a", limitNeutral, 1, 50, 1,
                v -> limitNeutral = v,
                "Cows, Sheep, Pigs etc.");
        addRow(rightX, topY + rowH * 2,
                "Blocks", "§6", limitBlock, 1, 50, 1,
                v -> limitBlock = v,
                "Pistons, Spawners, Dispensers etc.");
        addRow(rightX, topY + rowH * 3,
                "Ambient", "§9", limitAmbient, 1, 50, 1,
                v -> limitAmbient = v,
                "Cave sounds, weather etc.");
        addRow(rightX, topY + rowH * 4,
                "Default", "§d", limitDefault, 1, 50, 1,
                v -> limitDefault = v,
                "All other sound categories");

        // === BOTTOM BUTTONS ===
        int btnY = this.height - 26;
        this.addRenderableWidget(
                Button.builder(Component.literal("§a✔ Save & Close"), btn -> {
                    saveToConfig();
                    this.onClose();
                }).bounds(cx - 118, btnY, 110, 18).build()
        );
        this.addRenderableWidget(
                Button.builder(Component.literal("§c✖ Cancel"), btn -> this.onClose())
                        .bounds(cx + 8, btnY, 110, 18).build()
        );
        this.addRenderableWidget(
                Button.builder(Component.literal("§7↺"), btn -> {
                    maxTotal = 6; windowTicks = 20; regionSize = 16;
                    debugLogging = false;
                    limitHostile = 3; limitNeutral = 2; limitBlock = 5;
                    limitAmbient = 4; limitDefault = 3;
                    refreshUI();
                }).bounds(cx + 124, btnY, 22, 18)
                        .tooltip(Tooltip.create(Component.literal("Reset to defaults")))
                        .build()
        );
    }

    private void addRow(int x, int y, String label, String colorCode, int value,
                        int min, int max, int step,
                        java.util.function.IntConsumer setter, String tooltip) {
        Component minus = Component.literal("§f-");
        Component plus = Component.literal("§f+");
        Component val = Component.literal(colorCode + String.valueOf(value));

        this.addRenderableWidget(
                Button.builder(minus, btn -> {
                    int nv = Math.max(min, value - step);
                    setter.accept(nv);
                    refreshUI();
                }).bounds(x + 108, y, 16, 16).build()
        );
        this.addRenderableWidget(
                Button.builder(plus, btn -> {
                    int nv = Math.min(max, value + step);
                    setter.accept(nv);
                    refreshUI();
                }).bounds(x + 148, y, 16, 16)
                        .tooltip(Tooltip.create(Component.literal(tooltip)))
                        .build()
        );
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float delta) {
        this.renderBackground(g, mouseX, mouseY, delta);
        super.render(g, mouseX, mouseY, delta);

        g.pose().pushPose();
        g.pose().translate(0, 0, 200);

        int cx = this.width / 2;
        int leftX = cx - COL_W - COL_GAP / 2;
        int rightX = cx + COL_GAP / 2;
        int topY = 50;
        int rowH = 22;

        // Title
        g.drawCenteredString(this.font, Component.literal("§6§lDynamic Sound Culling"), cx, 10, 0xFFFFCC00);
        g.drawCenteredString(this.font, Component.literal("§7Configure sound culling behavior"), cx, 24, 0xFFAAAAAA);

        // Left section
        g.drawString(this.font, Component.literal("§b§lSystem"), leftX, topY - 12, 0xFF00E5FF);
        g.fill(leftX, topY - 1, leftX + COL_W, topY, 0xFF555555);

        // Left labels
        g.drawString(this.font, Component.literal("§6Max Sounds/Region"), leftX, topY + 4, 0xFFFFD740);
        g.drawString(this.font, Component.literal("§bTime Window"), leftX, topY + rowH + 4, 0xFF40C4FF);
        g.drawString(this.font, Component.literal("§5Region Size"), leftX, topY + rowH * 2 + 4, 0xFFB388FF);
        g.drawString(this.font, Component.literal("§dDebug Mode"), leftX, topY + rowH * 3 + 4, 0xFFFF80AB);

        // Left values
        drawVal(g, leftX, topY, maxTotal, "§6");
        drawVal(g, leftX, topY + rowH, windowTicks + "t", "§b");
        drawVal(g, leftX, topY + rowH * 2, regionSize + "b", "§5");

        // Right section
        g.drawString(this.font, Component.literal("§a§lLimits"), rightX, topY - 12, 0xFF76FF03);
        g.fill(rightX, topY - 1, rightX + COL_W, topY, 0xFF555555);

        // Right labels
        g.drawString(this.font, Component.literal("§cHostile Mobs"), rightX, topY + 4, 0xFFFF5252);
        g.drawString(this.font, Component.literal("§aNeutral Mobs"), rightX, topY + rowH + 4, 0xFF69F0AE);
        g.drawString(this.font, Component.literal("§6Blocks"), rightX, topY + rowH * 2 + 4, 0xFFFFAB40);
        g.drawString(this.font, Component.literal("§9Ambient"), rightX, topY + rowH * 3 + 4, 0xFF448AFF);
        g.drawString(this.font, Component.literal("§dDefault"), rightX, topY + rowH * 4 + 4, 0xFFE040FB);

        // Right values
        drawVal(g, rightX, topY, limitHostile, "§c");
        drawVal(g, rightX, topY + rowH, limitNeutral, "§a");
        drawVal(g, rightX, topY + rowH * 2, limitBlock, "§6");
        drawVal(g, rightX, topY + rowH * 3, limitAmbient, "§9");
        drawVal(g, rightX, topY + rowH * 4, limitDefault, "§d");

        // Footer
        int sepY = this.height - 42;
        g.fill(cx - 180, sepY, cx + 180, sepY + 1, 0xFF555555);
        String stats = "§a" + SoundCullingTracker.getTotalCulled() + " sounds culled this session";
        g.drawCenteredString(this.font, Component.literal(stats), cx, sepY + 6, 0xFF69F0AE);

        g.pose().popPose();
    }

    private void drawVal(GuiGraphics g, int colX, int y, Object val, String colorCode) {
        String s = String.valueOf(val);
        g.drawString(this.font, Component.literal(colorCode + s),
                colX + 130 - this.font.width(s) / 2, y + 4, 0xFFFFFFFF);
    }

    private void saveToConfig() {
        config.maxTotalPerRegion = maxTotal;
        config.windowTicks = windowTicks;
        config.regionSize = regionSize;
        config.debugLogging = debugLogging;
        config.limitHostile = limitHostile;
        config.limitNeutral = limitNeutral;
        config.limitBlock = limitBlock;
        config.limitAmbient = limitAmbient;
        config.limitDefault = limitDefault;
        config.save();
        SoundCulling.LOGGER.info("[SoundCulling] Settings saved.");
    }

    @Override
    public void onClose() {
        Minecraft.getInstance().setScreen(this.parent);
    }
}
