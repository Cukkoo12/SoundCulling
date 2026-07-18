package com.cukkoo.soundculling.integration;

import com.cukkoo.soundculling.SoundCulling;
import com.cukkoo.soundculling.SoundCullingTracker;
import com.cukkoo.soundculling.config.SoundCullingConfig;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.tooltip.Tooltip;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

/**
 * Modern, colorful configuration screen with side-by-side categories
 * and advanced category-specific culling limit sliders.
 */
public class SoundCullingConfigScreen extends Screen {

    private final Screen parent;
    private final SoundCullingConfig config;

    // Live editing values
    private int maxTotal;
    private int windowTicks;
    private int regionSize;
    private boolean debugLogging;

    private int limitHostile;
    private int limitNeutral;
    private int limitBlock;
    private int limitAmbient;
    private int limitDefault;

    public SoundCullingConfigScreen(Screen parent) {
        super(Text.literal("Dynamic Sound Culling"));
        this.parent = parent;
        this.config = SoundCulling.getConfig();

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
        this.clearChildren();

        int cx = this.width / 2;
        int leftColX = cx - 155;
        int rightColX = cx + 15;
        int startY = 45;
        int rowH = 22;

        // --- LEFT COLUMN: SYSTEM ---
        int leftStartY = startY + 15;

        // Row 1: Region Total
        addNumRow(leftColX, leftStartY,
                () -> { maxTotal = Math.max(1, maxTotal - 1); refreshUI(); },
                () -> { maxTotal = Math.min(100, maxTotal + 1); refreshUI(); },
                "Max total combined sounds within a single region"
        );

        // Row 2: Time Window
        addNumRow(leftColX, leftStartY + rowH,
                () -> { windowTicks = Math.max(5, windowTicks - 5); refreshUI(); },
                () -> { windowTicks = Math.min(200, windowTicks + 5); refreshUI(); },
                "Time window in game ticks (20 ticks = 1 second)"
        );

        // Row 3: Region Size
        addNumRow(leftColX, leftStartY + rowH * 2,
                () -> { regionSize = Math.max(2, regionSize - 2); refreshUI(); },
                () -> { regionSize = Math.min(64, regionSize + 2); refreshUI(); },
                "Region boundaries in blocks (16 blocks = 1 chunk)"
        );

        // Row 4: Debug Logging (Toggle)
        this.addDrawableChild(
                ButtonWidget.builder(Text.literal(debugLogging ? "ENABLED" : "DISABLED"), btn -> {
                    debugLogging = !debugLogging;
                    refreshUI();
                }).dimensions(leftColX + 90, leftStartY + rowH * 3, 56, 16).build()
        );

        // --- RIGHT COLUMN: CATEGORY LIMITS ---
        int rightStartY = startY + 15;

        // Row 1: Hostile Limit
        addNumRow(rightColX, rightStartY,
                () -> { limitHostile = Math.max(1, limitHostile - 1); refreshUI(); },
                () -> { limitHostile = Math.min(50, limitHostile + 1); refreshUI(); },
                "Sound limit for Hostile mobs (Zombies, Creepers, etc.)"
        );

        // Row 2: Neutral Limit
        addNumRow(rightColX, rightStartY + rowH,
                () -> { limitNeutral = Math.max(1, limitNeutral - 1); refreshUI(); },
                () -> { limitNeutral = Math.min(50, limitNeutral + 1); refreshUI(); },
                "Sound limit for Neutral mobs (Cows, Sheep, etc.)"
        );

        // Row 3: Block Limit
        addNumRow(rightColX, rightStartY + rowH * 2,
                () -> { limitBlock = Math.max(1, limitBlock - 1); refreshUI(); },
                () -> { limitBlock = Math.min(50, limitBlock + 1); refreshUI(); },
                "Sound limit for blocks (Pistons, Spawners, etc.)"
        );

        // Row 4: Ambient Limit
        addNumRow(rightColX, rightStartY + rowH * 3,
                () -> { limitAmbient = Math.max(1, limitAmbient - 1); refreshUI(); },
                () -> { limitAmbient = Math.min(50, limitAmbient + 1); refreshUI(); },
                "Sound limit for environmental ambient sounds"
        );

        // Row 5: Default Limit
        addNumRow(rightColX, rightStartY + rowH * 4,
                () -> { limitDefault = Math.max(1, limitDefault - 1); refreshUI(); },
                () -> { limitDefault = Math.min(50, limitDefault + 1); refreshUI(); },
                "Default sound limit for categories not listed above"
        );

        // --- BOTTOM NAVIGATION BAR ---
        int btnWidth = 110;
        int gap = 8;
        int bottomY = this.height - 25;

        this.addDrawableChild(
                ButtonWidget.builder(Text.literal("Save & Close"), btn -> {
                    applyAndSave();
                    this.close();
                }).dimensions(cx - btnWidth - gap, bottomY, btnWidth, 18).build()
        );

        this.addDrawableChild(
                ButtonWidget.builder(Text.literal("Cancel"), btn -> {
                    this.close();
                }).dimensions(cx + gap, bottomY, btnWidth, 18).build()
        );

        // Reset to default button
        this.addDrawableChild(
                ButtonWidget.builder(Text.literal("Reset"), btn -> {
                    maxTotal = 6;
                    windowTicks = 20;
                    regionSize = 16;
                    debugLogging = false;
                    limitHostile = 3;
                    limitNeutral = 2;
                    limitBlock = 5;
                    limitAmbient = 4;
                    limitDefault = 3;
                    refreshUI();
                }).dimensions(cx + 122, bottomY, 38, 18)
                        .tooltip(Tooltip.of(Text.literal("Reset all values to factory defaults")))
                        .build()
        );
    }

    private void addNumRow(int x, int y, Runnable onMinus, Runnable onPlus, String tooltip) {
        this.addDrawableChild(
                ButtonWidget.builder(Text.literal("-"), btn -> onMinus.run())
                        .dimensions(x + 90, y, 16, 16).build()
        );
        this.addDrawableChild(
                ButtonWidget.builder(Text.literal("+"), btn -> onPlus.run())
                        .dimensions(x + 130, y, 16, 16)
                        .tooltip(Tooltip.of(Text.literal(tooltip)))
                        .build()
        );
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        this.renderBackground(context);

        int cx = this.width / 2;
        int leftColX = cx - 155;
        int rightColX = cx + 15;
        int startY = 45;
        int rowH = 22;

        // Headers
        context.drawCenteredTextWithShadow(this.textRenderer, Text.literal("Dynamic Sound Culling"),
                cx, 8, 0xFFFFAA00);
        context.drawCenteredTextWithShadow(this.textRenderer, Text.literal("Configuration Panel"),
                cx, 20, 0xFF888888);

        // --- Left Column Rendering ---
        context.drawTextWithShadow(this.textRenderer, Text.literal("System Configuration"), leftColX, startY, 0xFF55FFFF);
        context.drawHorizontalLine(leftColX, leftColX + 146, startY + 10, 0xFF333333);

        int leftStartY = startY + 15;

        // Row 1: Region Total
        context.drawTextWithShadow(this.textRenderer, Text.literal("Region Total"), leftColX, leftStartY + 4, 0xFFCCCCCC);
        String totalVal = String.valueOf(maxTotal);
        context.drawTextWithShadow(this.textRenderer, Text.literal(totalVal), leftColX + 114 - this.textRenderer.getWidth(totalVal) / 2, leftStartY + 4, 0xFFFFFFFF);

        // Row 2: Time Window
        context.drawTextWithShadow(this.textRenderer, Text.literal("Time Window"), leftColX, leftStartY + rowH + 4, 0xFFCCCCCC);
        String windowVal = windowTicks + "t";
        context.drawTextWithShadow(this.textRenderer, Text.literal(windowVal), leftColX + 114 - this.textRenderer.getWidth(windowVal) / 2, leftStartY + rowH + 4, 0xFFFFFFFF);

        // Row 3: Region Size
        context.drawTextWithShadow(this.textRenderer, Text.literal("Region Size"), leftColX, leftStartY + rowH * 2 + 4, 0xFFCCCCCC);
        String sizeVal = regionSize + "b";
        context.drawTextWithShadow(this.textRenderer, Text.literal(sizeVal), leftColX + 114 - this.textRenderer.getWidth(sizeVal) / 2, leftStartY + rowH * 2 + 4, 0xFFFFFFFF);

        // Row 4: Debug Logs
        context.drawTextWithShadow(this.textRenderer, Text.literal("Debug Logs"), leftColX, leftStartY + rowH * 3 + 4, 0xFFCCCCCC);

        // --- Right Column Rendering ---
        context.drawTextWithShadow(this.textRenderer, Text.literal("Sound Category Limits"), rightColX, startY, 0xFF55FF55);
        context.drawHorizontalLine(rightColX, rightColX + 146, startY + 10, 0xFF333333);

        int rightStartY = startY + 15;

        // Row 1: Hostile Limit
        context.drawTextWithShadow(this.textRenderer, Text.literal("Hostile Mobs"), rightColX, rightStartY + 4, 0xFFE57373);
        String hostileVal = String.valueOf(limitHostile);
        context.drawTextWithShadow(this.textRenderer, Text.literal(hostileVal), rightColX + 114 - this.textRenderer.getWidth(hostileVal) / 2, rightStartY + 4, 0xFFFFFFFF);

        // Row 2: Neutral Limit
        context.drawTextWithShadow(this.textRenderer, Text.literal("Neutral Mobs"), rightColX, rightStartY + rowH + 4, 0xFF81C784);
        String neutralVal = String.valueOf(limitNeutral);
        context.drawTextWithShadow(this.textRenderer, Text.literal(neutralVal), rightColX + 114 - this.textRenderer.getWidth(neutralVal) / 2, rightStartY + rowH + 4, 0xFFFFFFFF);

        // Row 3: Block Limit
        context.drawTextWithShadow(this.textRenderer, Text.literal("Blocks/Spawners"), rightColX, rightStartY + rowH * 2 + 4, 0xFFFFB74D);
        String blockVal = String.valueOf(limitBlock);
        context.drawTextWithShadow(this.textRenderer, Text.literal(blockVal), rightColX + 114 - this.textRenderer.getWidth(blockVal) / 2, rightStartY + rowH * 2 + 4, 0xFFFFFFFF);

        // Row 4: Ambient Limit
        context.drawTextWithShadow(this.textRenderer, Text.literal("Ambient Sounds"), rightColX, rightStartY + rowH * 3 + 4, 0xFF64B5F6);
        String ambientVal = String.valueOf(limitAmbient);
        context.drawTextWithShadow(this.textRenderer, Text.literal(ambientVal), rightColX + 114 - this.textRenderer.getWidth(ambientVal) / 2, rightStartY + rowH * 3 + 4, 0xFFFFFFFF);

        // Row 5: Default Limit
        context.drawTextWithShadow(this.textRenderer, Text.literal("Default Limit"), rightColX, rightStartY + rowH * 4 + 4, 0xFFB0BEC5);
        String defaultVal = String.valueOf(limitDefault);
        context.drawTextWithShadow(this.textRenderer, Text.literal(defaultVal), rightColX + 114 - this.textRenderer.getWidth(defaultVal) / 2, rightStartY + rowH * 4 + 4, 0xFFFFFFFF);

        // --- Bottom Stats ---
        int sepY = this.height - 42;
        context.drawHorizontalLine(cx - 155, cx + 161, sepY, 0xFF444444);

        int statsY = sepY + 5;
        String statsText = "Session Culled: " + SoundCullingTracker.getTotalCulled() + " sounds";
        context.drawCenteredTextWithShadow(this.textRenderer, Text.literal(statsText),
                cx, statsY, 0xFF81C784);

        super.render(context, mouseX, mouseY, delta);
    }

    private void applyAndSave() {
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
        SoundCulling.LOGGER.info("[SoundCulling] GUI settings saved successfully.");
    }

    @Override
    public void close() {
        MinecraftClient.getInstance().setScreen(this.parent);
    }
}
